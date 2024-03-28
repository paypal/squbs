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

package org.squbs.unicomplex.pipeline

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.Uri.Path
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.ws.PeerClosedConnectionException
import org.apache.pekko.http.scaladsl.server._
import org.apache.pekko.pattern._
import org.apache.pekko.stream.ActorMaterializer
import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}
import org.apache.pekko.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues._
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.metrics.{MetricsExtension, MetricsFlow}
import org.squbs.pipeline.{Context, PipelineFlow, PipelineFlowFactory}
import org.squbs.unicomplex.Timeouts._
import org.squbs.unicomplex._

import java.lang.management.ManagementFactory
import javax.management.ObjectName
import scala.concurrent.{Await, Future}

object MetricsFlowSpec {
  val classPaths = Array(getClass.getClassLoader.getResource("classpaths/pipeline/MetricsFlowSpec").getPath)

  val config = ConfigFactory.parseString(
    s"""
       |default-listener.bind-port = 0
       |squbs {
       |  actorsystem-name = ServerMetricsFlowSpec
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
    """.stripMargin
  )

  val boot = UnicomplexBoot(config)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()
}

class MetricsFlowSpec extends TestKit(MetricsFlowSpec.boot.actorSystem) with AsyncFlatSpecLike with Matchers {

  def hello(webContext: String) = HttpRequest(uri = s"$webContext/hello") -> 0
  def redirect(webContext: String) = HttpRequest(uri = s"$webContext/redirect") -> 0
  def notFound(webContext: String) = HttpRequest(uri = s"$webContext/notfound") -> 0
  def internalServerError(webContext: String) = HttpRequest(uri = s"$webContext/internalservererror") -> 0
  def connectionException(webContext: String) = HttpRequest(uri = s"$webContext/connectionException") -> 0
  def timeoutException(webContext: String) = HttpRequest(uri = s"$webContext/timeoutException") -> 0

  val portBindings = Await.result((Unicomplex(system).uniActor ? PortBindings).mapTo[Map[String, Int]], awaitMax)
  val port = portBindings("default-listener")
  val poolClientFlow = Http().cachedHostConnectionPool[Int]("localhost", port)

  it should "collect request count and time metrics when webContext is empty" in {
    val future = Source(hello("") :: hello("") :: Nil).
      via(poolClientFlow).
      runWith(Sink.ignore)

    future map { _ =>
      jmxValue("/-request-count", "Count").value shouldBe 2
      jmxValue("/-request-time", "Count").value shouldBe 2
      jmxValue("/-request-time", "FifteenMinuteRate") should not be empty
    }
  }

  it should "collect request count and time metrics" in {
    val future = Source(hello("/sample") :: hello("/sample") :: Nil).
      via(poolClientFlow).
      runWith(Sink.ignore)

    future map { _ =>
      jmxValue("sample-request-count", "Count").value shouldBe 2
      jmxValue("sample-request-time", "Count").value shouldBe 2
      jmxValue("sample-request-time", "FifteenMinuteRate") should not be empty
    }
  }

  it should "collect metrics per http status code category" in {
    val future = Source(hello("/sample2") :: hello("/sample2") :: redirect("/sample2") :: notFound("/sample2")
      :: internalServerError("/sample2") :: internalServerError("/sample2") :: Nil).
      via(poolClientFlow).
      runWith(Sink.ignore)

    future map { _ =>
      jmxValue("sample2-request-count", "Count").value shouldBe 6
      jmxValue("sample2-2XX-count", "Count").value shouldBe 2
      jmxValue("sample2-3XX-count", "Count").value shouldBe 1
      jmxValue("sample2-4XX-count", "Count").value shouldBe 1
      jmxValue("sample2-5XX-count", "Count").value shouldBe 2
    }
  }

  it should "be able to collect multiple metrics" in {

    val f1 = Source(hello("/sample3") :: hello("/sample3") :: internalServerError("/sample3") ::
      internalServerError("/sample3") :: Nil).
      via(poolClientFlow).
      runWith(Sink.ignore)

    val f2 = Source.single(redirect("/sample4")).
      via(poolClientFlow).
      runWith(Sink.ignore)

    val f3 = Source.single(notFound("/sample5")).
      via(poolClientFlow).
      runWith(Sink.ignore)

    val f = Future.sequence(List(f1, f2, f3))

    f map { _ =>
      jmxValue("sample3-request-count", "Count").value shouldBe 4
      jmxValue("sample3-2XX-count", "Count").value shouldBe 2
      jmxValue("sample3-5XX-count", "Count").value shouldBe 2

      jmxValue("sample4-request-count", "Count").value shouldBe 1
      jmxValue("sample4-3XX-count", "Count").value shouldBe 1

      jmxValue("sample5-request-count", "Count").value shouldBe 1
      jmxValue("sample5-4XX-count", "Count").value shouldBe 1
    }
  }

  it should "collect metrics for mapped exceptions" in {

    val future = Source(hello("/sample6") :: hello("/sample6") :: connectionException("/sample6")
      :: connectionException("/sample6") :: timeoutException("/sample6") :: timeoutException("/sample6") :: Nil).
      via(poolClientFlow).
      runWith(Sink.ignore)

    future map { _ =>
      jmxValue("sample6-request-count", "Count").value shouldBe 6
      jmxValue("sample6-2XX-count", "Count").value shouldBe 2
      jmxValue("sample6-5XX-count", "Count").value shouldBe 4
    }
  }

  it should "aggregate 5XX and 2XX counts in response-count metric" in {

    val f = Source(hello("/sample7") :: internalServerError("/sample7") :: internalServerError("/sample7") :: Nil).
      via(poolClientFlow).
      runWith(Sink.ignore)

    f map { _ =>
      jmxValue("sample7-response-count", "Count").value shouldBe 3
      jmxValue("sample7-5XX-count", "Count").value shouldBe 2
      jmxValue("sample7-2XX-count", "Count").value shouldBe 1
    }
  }

  it should "collect metric counts with Flow based server" in {

    val flowPort = portBindings("second-listener")
    val clientFlow = Http().cachedHostConnectionPool[Int]("localhost", flowPort)

    val result =
      Source(hello("/sample8") :: connectionException("/sample8") :: connectionException("/sample8")
        :: hello("/sample8") :: notFound("/sample8") :: internalServerError("/sample8") :: Nil).
        via(clientFlow).
        runWith(Sink.seq)

    result map { _ =>
      jmxValue("sample8-2XX-count", "Count").value shouldBe 2
      jmxValue("sample8-4XX-count", "Count").value shouldBe 1
      //jmxValue("sample8-5XX-count", "Count").value shouldBe 1
      jmxValue("sample8-response-count", "Count").value shouldBe 3
    }
  }

  def jmxValue(beanName: String, key: String) = {
    val oName =
      ObjectName.getInstance(s"${MetricsExtension(system).Domain}:name=${MetricsExtension(system).Domain}.$beanName")
    Option(ManagementFactory.getPlatformMBeanServer.getAttribute(oName, key))
  }
}

class MetricsRoute extends RouteDefinition {
  override def route: Route =
    path("hello") {
      complete("Hello World!")
    } ~ path("redirect") {
      complete(StatusCodes.Found)
    } ~ path("notfound") {
      complete(StatusCodes.NotFound)
    } ~ path("internalservererror") {
      complete(StatusCodes.InternalServerError)
    } ~ path("connectionException") {
      throw new PeerClosedConnectionException(0, "")
    } ~ path("timeoutException") {
      extractRequest { request =>
        throw new RuntimeException(RequestTimeoutException(request, ""))
      }
    }
}

class DefaultFlow extends PipelineFlowFactory {

  override def create(context: Context)(implicit system: ActorSystem): PipelineFlow = {
    MetricsFlow(context.name)
  }
}

class MetricsDummyFlow extends FlowDefinition with WebContext {

  val hello = Path(s"/${webContext}/hello")
  val connectionException = Path(s"/$webContext/connectionException")
  val internalServerError = Path(s"/$webContext/internalServerError")
  val notFound = Path(s"/$webContext/notfound")

  override def flow = Flow[HttpRequest].map {
    case HttpRequest(_, Uri(_, _, `hello`, _, _), _, _, _) =>
      HttpResponse(StatusCodes.OK, entity = "Hello World!")
    case HttpRequest(_, Uri(_, _, `connectionException`, _, _), _, _, _) =>
      throw new PeerClosedConnectionException(0, "Dummy Exception")
    case HttpRequest(_, Uri(_, _, `internalServerError`, _, _), _, _, _) =>
      HttpResponse(StatusCodes.InternalServerError)
    case HttpRequest(_, Uri(_, _, `notFound`, _, _), _, _, _) =>
      HttpResponse(StatusCodes.NotFound, entity = "Path not found!")
  }
}