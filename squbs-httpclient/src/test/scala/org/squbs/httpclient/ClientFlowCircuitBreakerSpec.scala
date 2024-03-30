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
import org.apache.pekko.http.scaladsl.model.HttpEntity.Chunked
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers.{Date, Server}
import org.apache.pekko.stream.scaladsl.{Keep, Sink, Source}
import org.apache.pekko.util.ByteString
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.squbs.resolver.ResolverRegistry
import org.squbs.streams.circuitbreaker.impl.AtomicCircuitBreakerState
import org.squbs.streams.circuitbreaker.{CircuitBreakerOpenException, CircuitBreakerSettings}
import org.squbs.testkit.Timeouts.awaitMax

import java.lang.management.ManagementFactory
import java.util.concurrent.{TimeUnit, TimeoutException}
import javax.management.ObjectName
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Await, Future, Promise}
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
      |
      |drain {
      |  type = squbs.httpclient
      |
      |  pekko.http.host-connection-pool.max-connections = 10
      |  circuit-breaker {
      |    max-failures = 10000
      |    call-timeout = 10 milliseconds
      |  }
      |}
      |
      |do-not-drain {
      |  type = squbs.httpclient
      |  pekko.http {
      |    client.idle-timeout = 10 seconds
      |    host-connection-pool {
      |      max-connections = 10
      |      response-entity-subscription-timeout = 1 minute
      |    }
      |  }
      |}
    """.stripMargin)

  implicit val system = ActorSystem("ClientFlowCircuitBreakerSpec", config)

  val defaultMaxFailures = system.settings.config.getInt("squbs.circuit-breaker.max-failures")
  val defaultMaxConnections = system.settings.config.getInt("pekko.http.host-connection-pool.max-connections")
  val numOfRequests = (defaultMaxFailures + defaultMaxConnections) * 2 // Some random large number
  val numOfPassThroughBeforeCircuitBreakerIsOpen = defaultMaxConnections + defaultMaxFailures - 1
  val numOfFailFast = numOfRequests - numOfPassThroughBeforeCircuitBreakerIsOpen

  ResolverRegistry(system).register[HttpEndpoint]("LocalhostEndpointResolver") {
    (_, _) =>Some(HttpEndpoint(s"http://localhost:$port/"))
  }

  import org.apache.pekko.http.scaladsl.server.Directives._
  import system.dispatcher
  implicit val scheduler = system.scheduler

  val InternalServerErrorResponse =
    HttpResponse(StatusCodes.InternalServerError)
      .withHeaders(Server("testServer") :: Date(DateTime(2017, 1, 1)) :: Nil)

  val route =
    path("internalServerError") {
      complete(InternalServerErrorResponse)
    } ~
    path("delay") {
      val promise = Promise[String]()
      import scala.concurrent.duration._
      val delay = 500.milliseconds
      scheduler.scheduleOnce(delay)(promise.success("delayed"))
      onComplete(promise.future) { _ =>
        complete {
          HttpResponse(entity =
            Chunked(ContentTypes.`text/plain(UTF-8)`,Source.single(ByteString("Response after delay!")))
          )
        }
      }
    }

  val serverBinding = Await.result(Http().newServerAt("localhost", 0).bind(route), awaitMax)
  val port = serverBinding.localAddress.getPort
}

class ClientFlowCircuitBreakerSpec extends AsyncFlatSpec with Matchers with BeforeAndAfterAll {

  import ClientFlowCircuitBreakerSpec._

  override def afterAll(): Unit = {
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

    combinedResponses map { responses =>
      // Because default max-open-requests = 32 is so requests will wait in the queue of connection pool
      // If max-open-requests were equal to max-connections, we would not multiply by 2.
      val maxNumOfPassThroughBeforeCircuitBreakerIsOpen = 2 * defaultMaxConnections + defaultMaxFailures - 1
      val actualNumPassThrough = responses.count(_ == Success(InternalServerErrorResponse))
      val actualNumFailFast = numOfRequests - actualNumPassThrough
      actualNumPassThrough should be >= numOfPassThroughBeforeCircuitBreakerIsOpen
      actualNumPassThrough should be <= maxNumOfPassThroughBeforeCircuitBreakerIsOpen
      actualNumFailFast should be >= numOfRequests - maxNumOfPassThroughBeforeCircuitBreakerIsOpen
      actualNumFailFast should be <= numOfRequests - numOfPassThroughBeforeCircuitBreakerIsOpen
    }
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

    combinedResponses map { responses =>
      // Because default max-open-requests = 32 is so requests will wait in the queue of connection pool
      // If max-open-requests were equal to max-connections, we would not multiply by 2.
      val maxNumOfPassThroughBeforeCircuitBreakerIsOpen = 2 * defaultMaxConnections + defaultMaxFailures - 1
      val actualNumPassThrough = responses.count(_ == Success(InternalServerErrorResponse))
      val actualNumFailFast = numOfRequests - actualNumPassThrough
      actualNumPassThrough should be >= numOfPassThroughBeforeCircuitBreakerIsOpen
      actualNumPassThrough should be <= maxNumOfPassThroughBeforeCircuitBreakerIsOpen
      actualNumFailFast should be >= numOfRequests - maxNumOfPassThroughBeforeCircuitBreakerIsOpen
      actualNumFailFast should be <= numOfRequests - numOfPassThroughBeforeCircuitBreakerIsOpen
    }
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

  it should "drain the http responses that arrive after the timeout" in {
    val start = System.nanoTime()
    val responseSeqFuture =
      Source(1 to 100)
        .map(HttpRequest(uri = "/delay") -> _)
        .via(ClientFlow[Int]("drain"))
        .map(_._1)
        .runWith(Sink.seq)

    val idleTimeoutConfig = system.settings.config.getString("do-not-drain.pekko.http.client.idle-timeout")
    val idleTimeout = Duration(idleTimeoutConfig).asInstanceOf[FiniteDuration]
    val promise = Promise[Seq[Try[HttpResponse]]]()
    import system.dispatcher
    system.scheduler.scheduleOnce(idleTimeout) {
      // Adding a timeout to make it easier to troubleshoot if draining functionality is somehow broken.  Without this
      // promise failure, the test case would just hang here when the connection pool is starved.  Failing the
      // test after a timeout and providing a helpful message should make it easier to debug the problem if that
      // ever happens.
      promise.failure(
        new TimeoutException("Test case timed out!  This happens when late arrived http responses are not drained!"))
    }

    Future.firstCompletedOf(promise.future :: responseSeqFuture :: Nil) map { seq =>
      val elapsedTime = FiniteDuration(System.nanoTime - start, TimeUnit.NANOSECONDS)
      val idleTimeout = Duration(system.settings.config.getString("pekko.http.client.idle-timeout"))
      // With a connection pool of size 10, 100 requests each taking 500 ms should be done in about 5+ seconds
      // If draining was not happening, it would keep each connection busy till idle-timeout.
      elapsedTime should be < idleTimeout
      seq.size shouldBe 100
      seq.collect {
        case Failure(ex) if ex.isInstanceOf[TimeoutException] => ex
      }.size shouldBe 100
    }
  }

  it should "saturate the connection pool when no drainer is specified" in {
    import scala.concurrent.duration._
    val circuitBreakerSettingsa =
      CircuitBreakerSettings[HttpRequest, HttpResponse, Int](
        AtomicCircuitBreakerState(
          "do-not-drain",
          maxFailures = 1000,
          callTimeout = 10 milliseconds,
          resetTimeout = 100 seconds))

    val responseSeqFuture =
      Source(1 to 20)
        .map(HttpRequest(uri = "/delay") -> _)
        .via(ClientFlow[Int]("do-not-drain", circuitBreakerSettings = Some(circuitBreakerSettingsa)))
        .map(_._1)
        .runWith(Sink.seq)

    val idleTimeoutConfig = system.settings.config.getString("do-not-drain.pekko.http.client.idle-timeout")
    val idleTimeout = Duration(idleTimeoutConfig).asInstanceOf[FiniteDuration]

    val promise = Promise[String]()
    import system.dispatcher
    system.scheduler.scheduleOnce(idleTimeout)(promise.success("idle-timeout reached!"))

    promise.future map { _ =>
      // With a connection pool of size 10, 20 requests each taking 500 ms should be done in about 1+ seconds, if late
      // arriving responses are drained (as each request times out in 100 ms).  If draining is not happening, it would
      // keep each connection busy till idle-timeout.  So, the stream would take at least 2 x idle-timeout to finish.
      responseSeqFuture.isCompleted shouldBe false
    }
  }

  def assertJmxValue(name: String, key: String, expectedValue: Any) = {
    val oName = ObjectName.getInstance(
      s"org.squbs.configuration:type=squbs.circuitbreaker,name=${ObjectName.quote(name)}")
    val actualValue = ManagementFactory.getPlatformMBeanServer.getAttribute(oName, key)
    actualValue shouldEqual expectedValue
  }
}
