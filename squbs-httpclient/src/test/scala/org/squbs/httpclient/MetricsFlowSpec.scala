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

package org.squbs.httpclient

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.ws.PeerClosedConnectionException
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse, RequestTimeoutException, StatusCodes}
import org.apache.pekko.stream.scaladsl.{BidiFlow, Flow, Sink, Source}
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.OptionValues._
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.squbs.metrics.{MetricsExtension, MetricsFlow}
import org.squbs.pipeline.{Context, PipelineFlow, PipelineFlowFactory, RequestContext}
import org.squbs.resolver._
import org.squbs.testkit.Timeouts._

import java.lang.management.ManagementFactory
import javax.management.ObjectName
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

object MetricsFlowSpec {

  val config = ConfigFactory.parseString(
    s"""
       |preFlow {
       |  type = squbs.pipelineflow
       |  factory = org.squbs.httpclient.DefaultFlow
       |}
       |
       |squbs.pipeline.client.default {
       |  pre-flow =  preFlow
       |}
       |
       |sampleClient {
       |  type = squbs.httpclient
       |}
       |
       |sampleClient6 {
       |  type = squbs.httpclient
       |  pipeline = failure
       |}
       |
       |sampleClient8 {
       |  type = squbs.httpclient
       |  pipeline = failure
       |}
       |
       |failure {
       |  type = squbs.pipelineflow
       |  factory = org.squbs.httpclient.FailureFlow
       |}
    """.stripMargin
  )

  implicit val system: ActorSystem = ActorSystem("ClientMetricFlowSpec", config)
  import org.apache.pekko.http.scaladsl.server.Directives._

  val route =
    path("hello") {
      get {
        complete("Hello World!")
      }
    } ~ path("redirect") {
      get {
        complete(HttpResponse(status = StatusCodes.Found))
      }
    } ~ path("internalservererror") {
      get {
        complete(HttpResponse(status = StatusCodes.InternalServerError))
      }
    }

  val serverBinding = Await.result(Http().newServerAt("localhost", 0).bind(route), awaitMax)
  val port = serverBinding.localAddress.getPort
}

class MetricsFlowSpec extends AsyncFlatSpec with Matchers with BeforeAndAfterAll {

  import MetricsFlowSpec._
  import system.dispatcher

  override def afterAll(): Unit = {
    serverBinding.unbind() map {_ => system.terminate()}
  }

  ResolverRegistry(system).register[HttpEndpoint]("LocalhostEndpointResolver")
    { (_, _) => Some(HttpEndpoint(s"http://localhost:$port")) }

  it should "collect request count and time metrics" in {
    val f = for(i <- 0 until 5) yield callService("sampleClient", "/hello")

    callServiceTwice("sampleClient", "/hello") flatMap { _ => Future.sequence(f) } map { _ =>
      jmxValue("sampleClient-request-count", "Count").value shouldBe 7
      jmxValue("sampleClient-request-time", "Count").value shouldBe 7
      jmxValue("sampleClient-request-time", "FifteenMinuteRate") should not be empty
    }
  }

  it should "collect metrics per http status code category" in {
    val f = for {
      hello <- callServiceTwice("sampleClient2", "/hello")
      redirect <- callService("sampleClient2", "/redirect")
      notFound <- callService("sampleClient2", "/notfound")
      internalServerError <- callServiceTwice("sampleClient2", "/internalservererror")
    } yield List(hello, redirect, notFound, internalServerError)

    f map { _ =>
      jmxValue("sampleClient2-request-count", "Count").value shouldBe 6
      jmxValue("sampleClient2-2XX-count", "Count").value shouldBe 2
      jmxValue("sampleClient2-3XX-count", "Count").value shouldBe 1
      jmxValue("sampleClient2-4XX-count", "Count").value shouldBe 1
      jmxValue("sampleClient2-5XX-count", "Count").value shouldBe 2
    }
  }

  it should "collect metrics for multiple clients" in {
    val f = for {
      hello <- callServiceTwice("sampleClient3", "/hello")
      redirect <- callService("sampleClient4", "/redirect")
      notFound <- callService("sampleClient5", "/notfound")
      internalServerError <- callServiceTwice("sampleClient3", "/internalservererror")
    } yield List(hello, redirect, notFound, internalServerError)

    f map { _ =>
      jmxValue("sampleClient3-request-count", "Count").value shouldBe 4
      jmxValue("sampleClient3-2XX-count", "Count").value shouldBe 2
      jmxValue("sampleClient3-5XX-count", "Count").value shouldBe 2

      jmxValue("sampleClient4-request-count", "Count").value shouldBe 1
      jmxValue("sampleClient4-3XX-count", "Count").value shouldBe 1

      jmxValue("sampleClient5-request-count", "Count").value shouldBe 1
      jmxValue("sampleClient5-4XX-count", "Count").value shouldBe 1
    }
  }

  it should "collect metrics for exceptions" in {
    val f = for {
      hello <- callServiceTwice("sampleClient6", "/hello")
      connectionException <- callService("sampleClient6", "/connectionException")
      connectionException2 <- callService("sampleClient6", "/connectionException")
      timeoutException <- callServiceTwice("sampleClient6", "/timeoutException")
    } yield List(hello, connectionException, connectionException2, timeoutException)

    f map { _ =>
      jmxValue("sampleClient6-request-count", "Count").value shouldBe 6
      jmxValue("sampleClient6-2XX-count", "Count").value shouldBe 2
      jmxValue("sampleClient6-PeerClosedConnectionException-count", "Count").value shouldBe 2
      jmxValue("sampleClient6-RequestTimeoutException-count", "Count").value shouldBe 2
    }
  }

  it should "collect response count metrics" in {
    val f = for(i <- 0 until 5) yield callService("sampleClient7", "/hello")

    callServiceTwice("sampleClient7", "/hello") flatMap { _ => Future.sequence(f) } map { _ =>
      jmxValue("sampleClient7-response-count", "Count").value shouldBe 7
    }
  }

  it should "collect response count metrics is equal to request count minus dropped" in {
    val f = for {
      hello <- callServiceTwice("sampleClient8", "/hello")
      dropped <- callServiceTwice("sampleClient8", "/dropped")
    } yield List(hello, dropped)

    f map { _ =>
      jmxValue("sampleClient8-request-count", "Count").value shouldBe 4
      jmxValue("sampleClient8-response-count", "Count").value shouldBe 2
    }
  }

  def jmxValue(beanName: String, key: String) = {
    val oName = ObjectName.getInstance(s"${MetricsExtension(system).Domain}:name=${MetricsExtension(system).Domain}.$beanName")
    Option(ManagementFactory.getPlatformMBeanServer.getAttribute(oName, key))
  }

  private def callService(clientName: String, path: String) = {
    val clientFlow = ClientFlow[Int](clientName)
    Source.single(HttpRequest(uri = path) -> 42)
      .via(clientFlow)
      .runWith(Sink.foreach{
        case(Success(response), _) => response.discardEntityBytes()
        case _ =>
      })
  }

  private def callServiceTwice(clientName: String, path: String) = {
    val clientFlow = ClientFlow[Int](clientName)
    Source(HttpRequest(uri = path) -> 42 :: HttpRequest(uri = path) -> 43 :: Nil)
      .via(clientFlow)
      .runWith(Sink.foreach{
        case(Success(response), _) => response.discardEntityBytes()
        case _ =>
      })
  }
}

class DefaultFlow extends PipelineFlowFactory {

  override def create(context: Context)(implicit system: ActorSystem): PipelineFlow = {
    MetricsFlow(context.name)
  }
}

class FailureFlow extends PipelineFlowFactory {

  override def create(context: Context)(implicit system: ActorSystem): PipelineFlow = {
    val inbound = Flow[RequestContext]
    val outbound = Flow[RequestContext].map { rc =>
      rc.request.getUri().path() match {
        case "/connectionException" => rc.copy(response = Some(Failure(new  PeerClosedConnectionException(0, ""))))
        case "/timeoutException" =>
          rc.copy(response = Some(Failure(new  RuntimeException(RequestTimeoutException(rc.request, "")))))
        case "/dropped" => rc.copy(response = None)
        case _ => rc
      }
    }
    BidiFlow.fromFlows(inbound, outbound)
  }
}
