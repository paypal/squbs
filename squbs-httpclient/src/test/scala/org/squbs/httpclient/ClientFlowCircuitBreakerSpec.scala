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

import java.lang.management.ManagementFactory
import javax.management.ObjectName

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Date, Server}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import com.typesafe.config.ConfigFactory
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterAll, Matchers}
import org.squbs.resolver.ResolverRegistry
import org.squbs.streams.circuitbreaker.impl.AtomicCircuitBreakerState
import org.squbs.streams.circuitbreaker.{CircuitBreakerOpenException, CircuitBreakerSettings}
import org.squbs.testkit.Timeouts.awaitMax

import scala.concurrent.Await
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

object ClientFlowCircuitBreakerSpec {

  val config = ConfigFactory.parseString(
    """
      |squbs.circuit-breaker {
      |  reset-timeout = 2 minutes
      |}
      |
      |clientWithConfig {
      |  type = squbs.httpclient
      |
      |  circuit-breaker {
      |    max-failures = 2
      |    call-timeout = 10 milliseconds
      |    reset-timeout = 100 seconds
      |  }
      |}
      |
      |clientWithConfigWithParam {
      |  type = squbs.httpclient
      |
      |  circuit-breaker {
      |    max-failures = 2
      |    call-timeout = 10 milliseconds
      |    reset-timeout = 100 seconds
      |  }
      |}
      |
      |disableCircuitBreaker {
      |  type = squbs.httpclient
      |}
      |
      |multipleMaterializations {
      |  type = squbs.httpclient
      |
      |  circuit-breaker {
      |    reset-timeout = 2 minutes
      |  }
      |}
    """.stripMargin)

  implicit val system = ActorSystem("ClientFlowCircuitBreakerSpec", config)
  implicit val materializer = ActorMaterializer()

  val defaultMaxFailures = system.settings.config.getInt("squbs.circuit-breaker.max-failures")
  val defaultMaxConnections = system.settings.config.getInt("akka.http.host-connection-pool.max-connections")
  val numOfRequests = (defaultMaxFailures + defaultMaxConnections) * 2 // Some random large number
  val numOfPassThroughBeforeCircuitBreakerIsOpen = defaultMaxConnections + defaultMaxFailures - 1
  val numOfFailFast = numOfRequests - numOfPassThroughBeforeCircuitBreakerIsOpen

  ResolverRegistry(system).register[HttpEndpoint]("LocalhostEndpointResolver") {
    (_, _) =>Some(HttpEndpoint(s"http://localhost:$port/"))
  }

  import akka.http.scaladsl.server.Directives._
  import system.dispatcher
  implicit val scheduler = system.scheduler

  val InternalServerErrorResponse =
    HttpResponse(StatusCodes.InternalServerError)
      .withHeaders(Server("testServer") :: Date(DateTime(2017, 1, 1)) :: Nil)

  val route =
    path("internalServerError") {
      complete(InternalServerErrorResponse)
    }

  val serverBinding = Await.result(Http().bindAndHandle(route, "localhost", 0), awaitMax)
  val port = serverBinding.localAddress.getPort
}

class ClientFlowCircuitBreakerSpec extends AsyncFlatSpec with Matchers with BeforeAndAfterAll {

  import ClientFlowCircuitBreakerSpec._

  override def afterAll: Unit = {
    serverBinding.unbind() map {_ => system.terminate()}
  }

  it should "fail fast using default failure decider" in {

    val circuitBreakerState =
      AtomicCircuitBreakerState("internalServerError", system.settings.config.getConfig("squbs.circuit-breaker"))

    val circuitBreakerSettings = CircuitBreakerSettings[HttpRequest, HttpResponse, Int](circuitBreakerState)

    val responseSeq =
      Source(1 to numOfRequests)
        .map(HttpRequest(uri = "/internalServerError") -> _)
        .via(ClientFlow[Int](s"http://localhost:$port/", circuitBreakerSettings = Some(circuitBreakerSettings)))
        .map(_._1) // In case the ordering changes
        .runWith(Sink.seq)

    val expected =
      List.fill(numOfPassThroughBeforeCircuitBreakerIsOpen)(Success(InternalServerErrorResponse)) ++
      List.fill(numOfFailFast)(Failure(CircuitBreakerOpenException()))

    responseSeq map { _ should contain theSameElementsAs expected }
  }

  it should "disable circuit breaker" in {
    val clientFlow = ClientFlow[Int]("disableCircuitBreaker")

    val responseSeq =
      Source(1 to numOfRequests)
        .map(HttpRequest(uri = "/internalServerError") -> _)
        .via(clientFlow)
        .map(_._1)
        .runWith(Sink.seq)

    val expected = List.fill(numOfRequests)(Success(InternalServerErrorResponse))

    responseSeq map { _ should contain theSameElementsAs expected}
  }

  it should "fallback" in {
    val circuitBreakerSettings =
      CircuitBreakerSettings[HttpRequest, HttpResponse, Int](
        AtomicCircuitBreakerState("fallbackClient", ConfigFactory.empty))
        .withFallback((_: HttpRequest) => Try(HttpResponse(entity = "Fallback Response")))

    val clientFlow = ClientFlow[Int]("fallbackClient", circuitBreakerSettings = Some(circuitBreakerSettings))

    val responseSeq =
      Source(1 to numOfRequests)
        .map(HttpRequest(uri = "/internalServerError") -> _)
        .via(clientFlow)
        .map(_._1) // In case the ordering changes
        .runWith(Sink.seq)

    val expected =
      List.fill(numOfPassThroughBeforeCircuitBreakerIsOpen)(Success(InternalServerErrorResponse)) ++
      List.fill(numOfFailFast)(Success(HttpResponse(entity = "Fallback Response")))

    responseSeq map { _ should contain theSameElementsAs expected }
  }

  it should "use the provided failure decider" in {
    val circuitBreakerSettings =
      CircuitBreakerSettings[HttpRequest, HttpResponse, Int](
        AtomicCircuitBreakerState("customFailureDeciderClient", ConfigFactory.empty))
        .withFailureDecider((_: Try[HttpResponse]) => false)

    val clientFlow = ClientFlow[Int]("customFailureDeciderClient", circuitBreakerSettings = Some(circuitBreakerSettings))

    val responseSeq =
      Source(1 to numOfRequests)
        .map(HttpRequest(uri = "/internalServerError") -> _)
        .via(clientFlow)
        .map(_._1)
        .runWith(Sink.seq)

    // Circuit Breaker should never be OPEN because we do not consider anything as failure
    val expected = List.fill(numOfRequests)(Success(InternalServerErrorResponse))

    responseSeq map { _ should contain theSameElementsAs expected }
  }

  it should "share the circuit breaker state across materializations" in {
    val graph =
      Source(1 to numOfRequests / 2)
        .map(HttpRequest(uri = "/internalServerError") -> _)
        .via(ClientFlow[Int]("multipleMaterializations"))
        .map(_._1) // In case the ordering changes
        .toMat(Sink.seq)(Keep.right)

    val responseSeqFuture1 = graph.run()
    val responseSeqFuture2 = graph.run()

    val combinedResponses =
      for {
        responseSeq1 <- responseSeqFuture1
        responseSeq2 <- responseSeqFuture2
      } yield responseSeq1 ++ responseSeq2

    // Because default max-open-requests = 32 is so requests will wait in the queue of connection pool
    // If max-open-requests were equal to max-connections, we would not multiply by 2.
    val numOfPassThroughBeforeCircuitBreakerIsOpen = 2 * defaultMaxConnections + defaultMaxFailures - 1
    val numOfFailFast = numOfRequests - numOfPassThroughBeforeCircuitBreakerIsOpen

    val expected =
      List.fill(numOfPassThroughBeforeCircuitBreakerIsOpen)(Success(InternalServerErrorResponse)) ++
      List.fill(numOfFailFast)(Failure(CircuitBreakerOpenException()))

    combinedResponses map { _ should contain theSameElementsAs expected }
  }

  it should "share the circuit breaker state across multiple flows" in {
    val circuitBreakerSettings =
      CircuitBreakerSettings[HttpRequest, HttpResponse, Int](
        AtomicCircuitBreakerState("multipleFlows", ConfigFactory.empty))

    val responseSeqFuture1 =
      Source(1 to numOfRequests / 2)
        .map(HttpRequest(uri = "/internalServerError") -> _)
        .via(ClientFlow[Int]("multipleFlows", circuitBreakerSettings = Some(circuitBreakerSettings)))
        .map(_._1) // In case the ordering changes
        .runWith(Sink.seq)

    val responseSeqFuture2 =
      Source(1 to numOfRequests / 2)
        .map(HttpRequest(uri = "/internalServerError") -> _)
        .via(ClientFlow[Int]("multipleFlows", circuitBreakerSettings = Some(circuitBreakerSettings)))
        .map(_._1) // In case the ordering changes
        .runWith(Sink.seq)

    val combinedResponses =
      for {
        responseSeq1 <- responseSeqFuture1
        responseSeq2 <- responseSeqFuture2
      } yield responseSeq1 ++ responseSeq2

    // Because default max-open-requests = 32 is so requests will wait in the queue of connection pool
    // If max-open-requests were equal to max-connections, we would not multiply by 2.
    val numOfPassThroughBeforeCircuitBreakerIsOpen = 2 * defaultMaxConnections + defaultMaxFailures - 1
    val numOfFailFast = numOfRequests - numOfPassThroughBeforeCircuitBreakerIsOpen

    val expected =
      List.fill(numOfPassThroughBeforeCircuitBreakerIsOpen)(Success(InternalServerErrorResponse)) ++
      List.fill(numOfFailFast)(Failure(CircuitBreakerOpenException()))

    combinedResponses map { _ should contain theSameElementsAs expected }
  }

  it should "show circuit breaker configuration on JMX" in {
    ClientFlow("clientWithConfig")
    assertJmxValue("clientWithConfig-httpclient", "Name", "clientWithConfig-httpclient")
    assertJmxValue(
      "clientWithConfig-httpclient",
      "ImplementationClass",
      "org.squbs.streams.circuitbreaker.impl.AtomicCircuitBreakerState")
    assertJmxValue("clientWithConfig-httpclient", "MaxFailures", 2)
    assertJmxValue("clientWithConfig-httpclient", "CallTimeout", "10 milliseconds")
    assertJmxValue("clientWithConfig-httpclient", "ResetTimeout", "100 seconds")
    assertJmxValue("clientWithConfig-httpclient", "MaxResetTimeout", "36500 days")
    assertJmxValue("clientWithConfig-httpclient", "ExponentialBackoffFactor", 1.0)
  }

  it should "give priority to passed in parameter" in {
    import scala.concurrent.duration._
    val circuitBreakerState =
      AtomicCircuitBreakerState(
        "clientWithConfigWithParam-httpclient",
        11,
        12 seconds,
        13 minutes,
        14 days,
        16.0)

    val cbs = CircuitBreakerSettings[HttpRequest, HttpResponse, Int](circuitBreakerState)
    ClientFlow("clientWithConfigWithParam", circuitBreakerSettings = Some(cbs))
    assertJmxValue("clientWithConfigWithParam-httpclient", "Name", "clientWithConfigWithParam-httpclient")
    assertJmxValue(
      "clientWithConfigWithParam-httpclient",
      "ImplementationClass",
      "org.squbs.streams.circuitbreaker.impl.AtomicCircuitBreakerState")
    assertJmxValue("clientWithConfigWithParam-httpclient", "MaxFailures", 11)
    assertJmxValue("clientWithConfigWithParam-httpclient", "CallTimeout", "12 seconds")
    assertJmxValue("clientWithConfigWithParam-httpclient", "ResetTimeout", "13 minutes")
    assertJmxValue("clientWithConfigWithParam-httpclient", "MaxResetTimeout", "14 days")
    assertJmxValue("clientWithConfigWithParam-httpclient", "ExponentialBackoffFactor", 16.0)
  }

  def assertJmxValue(name: String, key: String, expectedValue: Any) = {
    val oName = ObjectName.getInstance(
      s"org.squbs.configuration:type=squbs.circuitbreaker,name=${ObjectName.quote(name)}")
    val actualValue = ManagementFactory.getPlatformMBeanServer.getAttribute(oName, key)
    actualValue shouldEqual expectedValue
  }
}
