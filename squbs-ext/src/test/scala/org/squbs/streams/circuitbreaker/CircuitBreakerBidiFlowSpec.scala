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

package org.squbs.streams.circuitbreaker

import java.lang.management.ManagementFactory
import java.util.UUID
import javax.management.ObjectName

import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.scaladsl.{BidiFlow, Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.typesafe.config.{ConfigException, ConfigFactory}
import org.scalatest.OptionValues._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FlatSpecLike, Matchers}
import org.squbs.metrics.MetricsExtension
import org.squbs.streams.FlowTimeoutException
import org.squbs.streams.circuitbreaker.impl.AtomicCircuitBreakerState

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

class CircuitBreakerBidiFlowSpec
  extends TestKit(ActorSystem("CircuitBreakerBidiFlowSpec", CircuitBreakerBidiFlowSpec.config))
  with FlatSpecLike with Matchers with ImplicitSender with ScalaFutures {

  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val timeout = 300 milliseconds
  val timeoutFailure = Failure(FlowTimeoutException("Flow timed out!"))
  val circuitBreakerOpenFailure = Failure(CircuitBreakerOpenException("Circuit Breaker is open; calls are failing fast!"))

  def delayFlow = {
    val delayActor = system.actorOf(Props[DelayActor])
    import akka.pattern.ask
    implicit val askTimeout = Timeout(5.seconds)
    Flow[(String, UUID)].mapAsyncUnordered(5) { elem =>
      (delayActor ? elem).mapTo[(String, UUID)]
    }
  }

  def flowWithCircuitBreaker(circuitBreakerState: CircuitBreakerState) = {
    Flow[String]
      .map(s => (s, UUID.randomUUID()))
      .via(CircuitBreakerBidiFlow[String, String, UUID](circuitBreakerState).join(delayFlow))
      .to(Sink.ignore)
      .runWith(Source.actorRef[String](25, OverflowStrategy.fail))
  }

  it should "increment failure count on call timeout" in {
    val circuitBreakerState = AtomicCircuitBreakerState("IncFailCount", system.scheduler, 2, timeout, 10 milliseconds)
    circuitBreakerState.subscribe(self, Open)
    val ref = flowWithCircuitBreaker(circuitBreakerState)
    ref ! "a"
    ref ! "b"
    ref ! "b"
    expectMsg(Open)
  }

  it should "reset failure count after success" in {
    val circuitBreakerState = AtomicCircuitBreakerState("ResetFailCount", system.scheduler, 2, timeout, 10 milliseconds)
    circuitBreakerState.subscribe(self, TransitionEvents)
    val ref = flowWithCircuitBreaker(circuitBreakerState)
    ref ! "a"
    ref ! "b"
    ref ! "b"
    expectMsg(Open)
    expectMsg(HalfOpen)
    ref ! "a"
    expectMsg(Closed)
  }

  it should "decide on failures based on the provided function" in {
    val circuitBreakerState = AtomicCircuitBreakerState("FailureDecider", system.scheduler, 2, timeout, 10 milliseconds)
    circuitBreakerState.subscribe(self, TransitionEvents)

    def failureDecider(elem: (Try[String], UUID)): Boolean = elem match {
      case (Success("b"), _) => true
      case _ => false
    }

    val circuitBreakerBidiFlow = BidiFlow
      .fromGraph {
        CircuitBreakerBidi[String, String, UUID](
          circuitBreakerState,
          fallback = None,
          failureDecider = Some(failureDecider))
      }

    val flow = Flow[(String, UUID)].map { case (s, uuid) => (Success(s), uuid) }

    val ref = Flow[String]
      .map(s => (s, UUID.randomUUID())).via(circuitBreakerBidiFlow.join(flow))
      .to(Sink.ignore)
      .runWith(Source.actorRef[String](25, OverflowStrategy.fail))

    ref ! "a"
    ref ! "b"
    ref ! "b"
    expectMsg(Open)
    expectMsg(HalfOpen)
    ref ! "a"
    expectMsg(Closed)
  }

  it should "respond with fail-fast exception" in {
    val circuitBreakerState = AtomicCircuitBreakerState("FailFast", system.scheduler, 2, timeout, 1 second)
    val circuitBreakerBidiFlow = BidiFlow
      .fromGraph {
        CircuitBreakerBidi[String, String, Long](circuitBreakerState)
      }


    val flowFailure = Failure(new RuntimeException("Some dummy exception!"))
    val flow = Flow[(String, Long)].map {
      case ("b", uuid) => (flowFailure, uuid)
      case (elem, uuid) => (Success(elem), uuid)
    }

    var context = 0L
    val result = Source("a" :: "b" :: "b" :: "a" :: Nil)
      .map { s => context += 1; (s, context) }
      .via(circuitBreakerBidiFlow.join(flow))
      .runWith(Sink.seq)

    val failFastFailure = Failure(CircuitBreakerOpenException())
    val expected = (Success("a"), 1) :: (flowFailure, 2) :: (flowFailure, 3) :: (failFastFailure, 4) :: Nil
    whenReady(result) { r =>
      r should contain theSameElementsInOrderAs expected
    }
  }

  it should "respond with fallback" in {
    val circuitBreakerState = AtomicCircuitBreakerState("Fallback", system.scheduler, 2, timeout, 10 milliseconds)

    val circuitBreakerBidiFlow = BidiFlow.fromGraph {
      CircuitBreakerBidi[String, String, Long](
        circuitBreakerState,
        Some((elem: (String, Long)) => (Success("fb"), elem._2)), None)
    }

    val flowFailure = Failure(new RuntimeException("Some dummy exception!"))
    val flow = Flow[(String, Long)].map {
      case ("b", uuid) => (flowFailure, uuid)
      case (elem, uuid) => (Success(elem), uuid)
    }

    var context = 0L
    val result = Source("a" :: "b" :: "b" :: "a" :: Nil)
      .map { s => context += 1; (s, context) }
      .via(circuitBreakerBidiFlow.join(flow))
      .runWith(Sink.seq)

    val expected = (Success("a"), 1) :: (flowFailure, 2) :: (flowFailure, 3) :: (Success("fb"), 4) :: Nil
    whenReady(result) { r =>
      r should contain theSameElementsInOrderAs(expected)
    }
  }

  it should "collect metrics" in {

    def jmxValue(beanName: String, key: String): Option[AnyRef] = {
      val oName = ObjectName.getInstance(s"${MetricsExtension(system).Domain}:name=$beanName")
      Option(ManagementFactory.getPlatformMBeanServer.getAttribute(oName, key))
    }

    val circuitBreakerState = AtomicCircuitBreakerState("MetricsCB", system.scheduler, 2, timeout, 10 seconds)
      .withMetricRegistry(MetricsExtension(system).metrics)

    circuitBreakerState.subscribe(self, TransitionEvents)
    val ref = flowWithCircuitBreaker(circuitBreakerState)
    jmxValue("MetricsCB.circuit-breaker.state", "Value").value shouldBe Closed
    ref ! "a"
    ref ! "b"
    ref ! "b"
    expectMsg(Open)
    jmxValue("MetricsCB.circuit-breaker.state", "Value").value shouldBe Open
    ref ! "a"
    jmxValue("MetricsCB.circuit-breaker.success-count", "Count").value shouldBe 1
    jmxValue("MetricsCB.circuit-breaker.failure-count", "Count").value shouldBe 2
    // The processing of message "a" may take longer.
    awaitAssert(jmxValue("MetricsCB.circuit-breaker.short-circuit-count", "Count").value shouldBe 1)
  }

  it should "allow a uniqueId mapper to be passed in" in {
    case class MyContext(s: String, id: Long)

    val delayActor = system.actorOf(Props[DelayActor])
    import akka.pattern.ask
    implicit val askTimeout = Timeout(5.seconds)
    val flow = Flow[(String, MyContext)].mapAsyncUnordered(10) { elem =>
      (delayActor ? elem).mapTo[(String, MyContext)]
    }

    val circuitBreakerBidiFlow = CircuitBreakerBidiFlow[String, String, MyContext](
      AtomicCircuitBreakerState("UniqueId", system.scheduler, 2, timeout, 10 milliseconds),
      None,
      None,
      (context: MyContext) => Some(context.id))


    var counter = 0L
    val result = Source("a" :: "b" :: "b" :: "a" :: Nil)
      .map { s => counter += 1; (s, MyContext("dummy", counter)) }
      .via(circuitBreakerBidiFlow.join(flow))
      .runWith(Sink.seq)

    val timeoutFailure = Failure(FlowTimeoutException("Flow timed out!"))
    val expected =
      (Success("a"), MyContext("dummy", 1)) ::
      (Success("a"), MyContext("dummy", 4)) ::
      (timeoutFailure, MyContext("dummy", 2)) ::
      (timeoutFailure, MyContext("dummy", 3)) :: Nil

    whenReady(result, timeout(Span(2, Seconds)), interval(Span(200, Millis))) { r =>
      r should contain theSameElementsAs(expected)
    }
  }

  it should "create circuit breaker from configuration" in {
    val circuitBreakerState = AtomicCircuitBreakerState("sample-circuit-breaker")
    circuitBreakerState.subscribe(self, TransitionEvents)
    val ref = flowWithCircuitBreaker(circuitBreakerState)
    ref ! "a"
    ref ! "b"
    expectMsg(Open)
    expectMsg(HalfOpen)
    ref ! "a"
    expectMsg(Closed)
  }

  a [ConfigException.Missing] should be thrownBy AtomicCircuitBreakerState("notype-circuitbreaker")
  a [ConfigException.Missing] should be thrownBy AtomicCircuitBreakerState("missing-max-failures-circuit-breaker")
  a [ConfigException.Missing] should be thrownBy AtomicCircuitBreakerState("missing-call-timeout-circuit-breaker")
  a [ConfigException.Missing] should be thrownBy AtomicCircuitBreakerState("missing-reset-timeout-circuit-breaker")

  it should "increase the reset timeout exponentially after it transits to open again" in {
    val circuitBreakerState = AtomicCircuitBreakerState("exponential-backoff-circuitbreaker")
    circuitBreakerState.subscribe(self, HalfOpen)
    circuitBreakerState.subscribe(self, Open)
    val ref = flowWithCircuitBreaker(circuitBreakerState)
    ref ! "a"
    ref ! "b"
    ref ! "b"
    expectMsg(Open)
    expectNoMsg(10 milliseconds)
    expectMsg(HalfOpen)
    ref ! "b"
    expectMsg(Open)
    expectNoMsg(10 milliseconds)
    expectMsg(HalfOpen)

    1 to 6 foreach { _ =>
      ref ! "b"
      expectMsg(Open)
      expectNoMsg(100 milliseconds)
      expectMsg(HalfOpen)
    }

    ref ! "b"
    expectMsg(Open)
    // reset-timeout should be maxed at 100 milliseconds.  Otherwise, it would have been 2.7 hours by this line.
    // Giving it 5 seconds as timing characteristics may not be as precise in different CI systems.
    expectMsg(5 seconds, HalfOpen)
  }

  it should "share the circuit breaker state across materializations" in {
    val circuitBreakerState = AtomicCircuitBreakerState(
      "MultipleMaterialization",
      system.scheduler,
      2,
      timeout,
      10 seconds)

    circuitBreakerState.subscribe(self, Open)

    val flow = Source.actorRef[String](25, OverflowStrategy.fail)
      .map(s => (s, UUID.randomUUID()))
      .via(CircuitBreakerBidiFlow[String, String, UUID](circuitBreakerState).join(delayFlow))
      .map(_._1)
      .toMat(Sink.seq)(Keep.both)

    val (ref1, result1) = flow.run()
    val (ref2, result2) = flow.run()

    ref1 ! "a"
    ref1 ! "b"
    ref1 ! "b"
    expectMsg(Open)
    ref2 ! "a"
    ref2 ! "a"
    ref1 ! akka.actor.Status.Success("a")
    ref2 ! akka.actor.Status.Success("a")
    whenReady(result2, timeout(Span(2, Seconds)), interval(Span(200, Millis))) { r2 =>
      val expected = circuitBreakerOpenFailure :: circuitBreakerOpenFailure :: Nil
      r2 should contain theSameElementsInOrderAs expected
    }
  }
}

object CircuitBreakerBidiFlowSpec {
  val config = ConfigFactory.parseString(
    """
      |sample-circuit-breaker {
      |  type = squbs.circuitbreaker
      |  max-failures = 1
      |  call-timeout = 50 ms
      |  reset-timeout = 20 ms
      |}
      |
      |exponential-backoff-circuitbreaker {
      |  type = squbs.circuitbreaker
      |  max-failures = 2
      |  call-timeout = 100 ms
      |  reset-timeout = 10 ms
      |  exponential-backoff-factor = 10.0
      |  max-reset-timeout = 101 ms
      |}
      |
      |notype-circuitbreaker {
      |  max-failures = 1
      |  call-timeout = 50 ms
      |  reset-timeout = 20 ms
      |}
      |
      |missing-max-failures-circuit-breaker {
      |  type = squbs.circuitbreaker
      |  call-timeout = 50 ms
      |  reset-timeout = 20 ms
      |}
      |
      |missing-call-timeout-circuit-breaker {
      |  type = squbs.circuitbreaker
      |  max-failures = 1
      |  reset-timeout = 20 ms
      |}
      |
      |missing-reset-timeout-circuit-breaker {
      |  type = squbs.circuitbreaker
      |  max-failures = 1
      |  call-timeout = 50 ms
      |}
      |
    """.stripMargin)
}

class DelayActor extends Actor {

  val delay = Map("a" -> 10.milliseconds, "b" -> 1000.milliseconds, "c" -> 10.milliseconds)

  import context.dispatcher

  def receive = {
    case element: String => context.system.scheduler.scheduleOnce(delay(element), sender(), element)
    case element @ (s: String, _) => context.system.scheduler.scheduleOnce(delay(s), sender(), element)
  }
}
