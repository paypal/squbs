/*
 * Copyright 2017 PayPal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.squbs.unicomplex

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.Uri.Path
import org.apache.pekko.http.scaladsl.model.headers.Connection
import org.apache.pekko.http.scaladsl.model.ws.PeerClosedConnectionException
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes, Uri}
import org.apache.pekko.pattern._
import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}
import org.apache.pekko.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues._
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.metrics.MetricsExtension
import org.squbs.unicomplex.Timeouts.{awaitMax, _}

import java.lang.management.ManagementFactory
import java.net.{HttpURLConnection, URL}
import javax.management.ObjectName
import scala.concurrent.{Await, Promise}

object ConnectionMetricsSpec {
  val classPaths = Array(getClass.getClassLoader.getResource("classpaths/ConnectionMetricsSpec").getPath)

  val config = ConfigFactory.parseString(
    s"""
       |default-listener.bind-port = 0
       |
       |second-listener {
       |  type = squbs.listener
       |  aliases = []
       |  bind-address = "0.0.0.0"
       |  full-address = false
       |  bind-port = 0
       |  secure = false
       |  need-client-auth = false
       |  ssl-context = default
       |}
       |
       |third-listener {
       |  type = squbs.listener
       |  aliases = []
       |  bind-address = "0.0.0.0"
       |  full-address = false
       |  bind-port = 0
       |  secure = false
       |  need-client-auth = false
       |  ssl-context = default
       |}
       |
       |squbs {
       |  actorsystem-name = ServerConnectionMetricsSpec
       |  ${JMX.prefixConfig} = true
       |}
       |
       |preFlow {
       |  type = squbs.pipelineflow
       |  factory = org.squbs.unicomplex.pipeline.DefaultFlow
       |}
       |squbs.pipeline.server.default {
       |  pre-flow =  preFlow
       |}
       |
       |pekko.http {
       |  server {
       |    max-connections = 512
       |  }
       |  host-connection-pool {
       |    min-connections = 0
       |    max-connections = 16
       |  }
       |}
    """.stripMargin
  )

  val boot = UnicomplexBoot(config)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()
}

class ConnectionMetricsSpec extends TestKit(ConnectionMetricsSpec.boot.actorSystem)
  with AsyncFlatSpecLike with Matchers {

  val hello = HttpRequest(uri = s"/sample/hello") -> 0
  val hello2 = HttpRequest(uri = s"/sample2/hello") -> 0
  val helloWithConnectionClose = HttpRequest(uri = s"/sample/hello").withHeaders(Connection("close")) -> 0
  val connectionException = HttpRequest(uri = s"/sample/connectionException") -> 0

  val portBindings = Await.result((Unicomplex(system).uniActor ? PortBindings).mapTo[Map[String, Int]], awaitMax)
  val secondListenerPort = portBindings("second-listener")

  it should "collect connection metrics" in {

    val port = portBindings("default-listener")
    val poolClientFlow = Http().cachedHostConnectionPool[Int]("localhost", port)

    val future =
      Source(
        hello ::
        hello2 :: // Make sure multiple endpoints do not cause duplicate counts
        hello2 ::
        helloWithConnectionClose :: // Make sure metrics are updated when connection is closed
        hello ::
        helloWithConnectionClose :: Nil).
        via(poolClientFlow).
        runWith(Sink.ignore)

    future map { _ =>
      jmxValue("default-listener-connections-creation-count", "Count").value shouldBe 6
      jmxValue("default-listener-connections-termination-count", "Count").value shouldBe 2
      jmxValue("default-listener-connections-active-count", "Count").value shouldBe 4
    }
  }

  it should "update metrics when an exception is thrown in the server stream" in {

    val port = portBindings("second-listener")
    val poolClientFlow = Http().cachedHostConnectionPool[Int]("localhost", port)

    val future =
      Source(
        hello ::
        connectionException :: // Make sure multiple endpoints do not cause duplicate counts
        connectionException ::
        hello :: Nil).
        via(poolClientFlow).
        runWith(Sink.ignore)

    future map { _ =>
      jmxValue("second-listener-connections-creation-count", "Count").value shouldBe 4
      jmxValue("second-listener-connections-termination-count", "Count").value shouldBe 2
      jmxValue("second-listener-connections-active-count", "Count").value shouldBe 2
    }
  }

  it should "collect connection metrics when the client drops the connection" in {

    val port = portBindings("third-listener")
    val urlConnection1 = (new URL(s"http://localhost:$port").openConnection()).asInstanceOf[HttpURLConnection]
    val urlConnection2 = (new URL(s"http://localhost:$port").openConnection()).asInstanceOf[HttpURLConnection]
    val urlConnection3 = (new URL(s"http://localhost:$port").openConnection()).asInstanceOf[HttpURLConnection]
    urlConnection1.connect()
    urlConnection2.connect()
    urlConnection3.connect()

    awaitAssert {
      jmxValue("third-listener-connections-creation-count", "Count").value shouldBe 3
    }

    urlConnection1.disconnect()
    urlConnection2.disconnect()

    awaitAssert {
      jmxValue("third-listener-connections-termination-count", "Count").value shouldBe 2
      jmxValue("third-listener-connections-active-count", "Count").value shouldBe 1
    }

    assert(true)
  }

  def jmxValue(beanName: String, key: String) = {
    val oName =
      ObjectName.getInstance(s"${MetricsExtension(system).Domain}:name=${MetricsExtension(system).Domain}.$beanName")
    Option(ManagementFactory.getPlatformMBeanServer.getAttribute(oName, key))
  }
}

class ConnectionMetricsFlow extends FlowDefinition with WebContext {

  val hello = Path(s"/${webContext}/hello")
  val connectionException = Path(s"/$webContext/connectionException")

  override def flow = Flow[HttpRequest].mapAsync(1) {
    case HttpRequest(_, Uri(_, _, `hello`, _, _), _, _, _) =>
      val promise = Promise[HttpResponse]()
      import context.dispatcher

      import scala.concurrent.duration._
      context.system.scheduler.scheduleOnce(1.second) {
        promise.success(HttpResponse(StatusCodes.OK, entity = "Hello World!"))
      }
      promise.future
    case HttpRequest(_, Uri(_, _, `connectionException`, _, _), _, _, _) =>
      throw new PeerClosedConnectionException(0, "Dummy Exception")
  }
}