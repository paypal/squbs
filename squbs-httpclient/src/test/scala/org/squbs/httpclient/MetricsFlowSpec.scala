/*
 * Copyright 2015 PayPal
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

import java.lang.management.ManagementFactory
import javax.management.ObjectName

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.PeerClosedConnectionException
import akka.http.scaladsl.model.{RequestTimeoutException, HttpResponse, StatusCodes, HttpRequest}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{BidiFlow, Flow, Sink, Source}
import com.typesafe.config.ConfigFactory
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterAll, Matchers}
import org.squbs.endpoint.{EndpointResolverRegistry, Endpoint, EndpointResolver}
import org.squbs.env.Environment
import org.squbs.metrics.{MetricsFlow, MetricsExtension}
import org.squbs.pipeline.streaming._
import org.squbs.testkit.Timeouts._

import scala.concurrent.{Future, Await}
import scala.util.{Failure, Success}

object MetricsFlowSpec {

  val config = ConfigFactory.parseString(
    s"""
       |preFlow {
       |  type = squbs.pipelineflow
       |  factory = org.squbs.httpclient.DefaultFlow
       |}
       |
       |squbs.pipeline.streaming.defaults {
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
       |failure {
       |  type = squbs.pipelineflow
       |  factory = org.squbs.httpclient.FailureFlow
       |}
    """.stripMargin
  )

  implicit val system: ActorSystem = ActorSystem("ClientMetricFlowSpec", config)
  implicit val materializer = ActorMaterializer()
  import system.dispatcher
  import akka.http.scaladsl.server.Directives._

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

  val serverBinding = Await.result(Http().bindAndHandle(route, "localhost", 0), awaitMax)
  val port = serverBinding.localAddress.getPort
}

class MetricsFlowSpec extends AsyncFlatSpec with Matchers with BeforeAndAfterAll {

  import MetricsFlowSpec._
  import system.dispatcher

  override def afterAll: Unit = {
    serverBinding.unbind() map {_ => system.terminate()}
  }

  EndpointResolverRegistry(system).register(new EndpointResolver {
    override def name: String = "LocalhostEndpointResolver"
    override def resolve(svcName: String, env: Environment) = Some(Endpoint(s"http://localhost:$port"))
  })

  it should "collect request count and time metrics" in {
    val f = for(i <- 0 until 5) yield callService("sampleClient", "/hello")

    callServiceTwice("sampleClient", "/hello") flatMap { _ => Future.sequence(f) } map { _ =>
      assertJmxValue("sampleClient-request-count", "Count", 7)
      assertJmxValue("sampleClient-request-time", "Count", 7)
      assertJmxEntryExists("sampleClient-request-time", "FifteenMinuteRate")
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
      assertJmxValue("sampleClient2-request-count", "Count", 6)
      assertJmxValue("sampleClient2-2XX-count", "Count", 2)
      assertJmxValue("sampleClient2-3XX-count", "Count", 1)
      assertJmxValue("sampleClient2-4XX-count", "Count", 1)
      assertJmxValue("sampleClient2-5XX-count", "Count", 2)
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
      assertJmxValue("sampleClient3-request-count", "Count", 4)
      assertJmxValue("sampleClient3-2XX-count", "Count", 2)
      assertJmxValue("sampleClient3-5XX-count", "Count", 2)

      assertJmxValue("sampleClient4-request-count", "Count", 1)
      assertJmxValue("sampleClient4-3XX-count", "Count", 1)

      assertJmxValue("sampleClient5-request-count", "Count", 1)
      assertJmxValue("sampleClient5-4XX-count", "Count", 1)
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
      assertJmxValue("sampleClient6-request-count", "Count", 6)
      assertJmxValue("sampleClient6-2XX-count", "Count", 2)
      assertJmxValue("sampleClient6-PeerClosedConnectionException-count", "Count", 2)
      assertJmxValue("sampleClient6-RequestTimeoutException-count", "Count", 2)
    }
  }

  def assertJmxValue(beanName: String, key: String, expectedValue: Any) = {
    val oName = ObjectName.getInstance(s"${MetricsExtension(system).Domain}:name=${MetricsExtension(system).Domain}.$beanName")
    val actualValue = ManagementFactory.getPlatformMBeanServer.getAttribute(oName, key)
    actualValue shouldEqual expectedValue
  }

  def assertJmxEntryExists(beanName: String, key: String) = {
    val oName = ObjectName.getInstance(s"${MetricsExtension(system).Domain}:name=${MetricsExtension(system).Domain}.$beanName")
    val actualValue = ManagementFactory.getPlatformMBeanServer.getAttribute(oName, key)
    actualValue.toString should not be empty
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
        case "/timeoutException" => rc.copy(response = Some(Failure(new  RuntimeException(RequestTimeoutException(rc.request, "")))))
        case _ => rc
      }
    }
    BidiFlow.fromFlows(inbound, outbound)
  }
}
