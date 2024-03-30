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

import org.apache.pekko.Done
import org.apache.pekko.actor.{Actor, ActorSystem, Props}
import org.apache.pekko.stream.{CompletionStrategy, OverflowStrategy}
import org.apache.pekko.stream.scaladsl.{BidiFlow, Flow, Keep, Sink, Source}
import org.apache.pekko.testkit.{ImplicitSender, TestKit}
import org.apache.pekko.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues._
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.metrics.MetricsExtension
import org.squbs.streams.FlowTimeoutException
import org.squbs.streams.circuitbreaker.impl.AtomicCircuitBreakerState

import java.lang.management.ManagementFactory
import java.util.UUID
import javax.management.ObjectName
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.language.postfixOps
import scala.util.{Failure, Success}

class CircuitBreakerSpec
  extends TestKit(ActorSystem("CircuitBreakerBidiFlowSpec", CircuitBreakerSpec.config))
  with AnyFlatSpecLike with Matchers with ImplicitSender {

  import Timing._

  implicit val askTimeout = Timeout(10.seconds)
  import system.dispatcher
  implicit val scheduler = system.scheduler

  val timeoutFailure = Failure(FlowTimeoutException())
  val circuitBreakerOpenFailure = Failure(CircuitBreakerOpenException())

  val completionMatcher: PartialFunction[Any, CompletionStrategy] = { case Done => CompletionStrategy.draining }

  def delayFlow() = {
    val delayActor = system.actorOf(Props[DelayActor]())
    import org.apache.pekko.pattern.ask
    Flow[(String, UUID)].mapAsyncUnordered(5) { elem =>
      (delayActor ? elem).mapTo[(String, UUID)]
    }
  }

  def flowWithCircuitBreaker(circuitBreakerState: CircuitBreakerState) = {
    Flow[String]
      .map(s => (s, UUID.randomUUID()))
      .via(CircuitBreaker[String, String, UUID](CircuitBreakerSettings(circuitBreakerState)).join(delayFlow()))
      .to(Sink.ignore)
      .runWith(Source.actorRef[String](completionMatcher, failureMatcher = PartialFunction.empty,
        25, OverflowStrategy.fail))
  }

  it should "increment failure count on call timeout" in {
    val circuitBreakerState = AtomicCircuitBreakerState("IncFailCount", 2, timeout, 10 minutes)
    circuitBreakerState.subscribe(self, Open)
    val ref = flowWithCircuitBreaker(circuitBreakerState)
    ref ! "a"
    ref ! "b"
    ref ! "b"
    expectMsg(Open)
  }

  it should "reset failure count after success" in {
    val circuitBreakerState = AtomicCircuitBreakerState("ResetFailCount", 2, timeout, 10.milliseconds)
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
    // Not testing call timeout here.  So, settng it to a high number just to remove any potential effect.
    val circuitBreakerState = AtomicCircuitBreakerState("FailureDecider", 2, 1 hour, 10.milliseconds)
    circuitBreakerState.subscribe(self, TransitionEvents)

    val circuitBreakerBidiFlow = BidiFlow
      .fromGraph {
        new CircuitBreaker[String, String, UUID](
          CircuitBreakerSettings(circuitBreakerState)
            .withFailureDecider(out => out.isFailure || out.equals(Success("b"))))
      }

    val flow = Flow[(String, UUID)].map { case (s, uuid) => (Success(s), uuid) }

    val ref = Flow[String]
      .map(s => (s, UUID.randomUUID())).via(circuitBreakerBidiFlow.join(flow))
      .to(Sink.ignore)
      .runWith(Source.actorRef[String](completionMatcher, failureMatcher = PartialFunction.empty,
        25, OverflowStrategy.fail))

    ref ! "a"
    ref ! "b"
    ref ! "b"
    expectMsg(Open)
    expectMsg(HalfOpen)
    ref ! "a"
    expectMsg(Closed)
  }

  it should "respond with fail-fast exception" in {
    val circuitBreakerState = AtomicCircuitBreakerState("FailFast", 2, timeout, 1 second)
    val circuitBreakerBidiFlow = BidiFlow
      .fromGraph {
        new CircuitBreaker[String, String, Long](CircuitBreakerSettings(circuitBreakerState))
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
    assertFuture(result) { r => r should contain theSameElementsInOrderAs expected }
  }

  it should "respond with fallback" in {
    val circuitBreakerState = AtomicCircuitBreakerState("Fallback", 2, timeout, 10.milliseconds)

    val circuitBreakerBidiFlow = BidiFlow.fromGraph {
      new CircuitBreaker[String, String, Long](
        CircuitBreakerSettings(circuitBreakerState).withFallback((elem: String) => Success("fb")))
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
    assertFuture(result) { r => r should contain theSameElementsInOrderAs(expected) }
  }

  it should "collect metrics" in {

    def jmxValue(beanName: String, key: String): Option[AnyRef] = {
      val oName = ObjectName.getInstance(s"${MetricsExtension(system).Domain}:name=$beanName")
      Option(ManagementFactory.getPlatformMBeanServer.getAttribute(oName, key))
    }

    val circuitBreakerState = AtomicCircuitBreakerState("MetricsCB", 2, timeout, 10.seconds)
      .withMetricRegistry(MetricsExtension(system).metrics)

    circuitBreakerState.subscribe(self, Open)
    val ref = flowWithCircuitBreaker(circuitBreakerState)
    jmxValue("MetricsCB.circuit-breaker.state", "Value").value shouldBe Closed
    ref ! "a"
    ref ! "b"
    ref ! "b"
    expectMsg(Open)
    // The state query is volatile underneath.
    awaitAssert(jmxValue("MetricsCB.circuit-breaker.state", "Value").value shouldBe Open, 1 minute)
    ref ! "a"
    jmxValue("MetricsCB.circuit-breaker.success-count", "Count").value shouldBe 1
    jmxValue("MetricsCB.circuit-breaker.failure-count", "Count").value shouldBe 2
    // The processing of message "a" may take longer.
    awaitAssert(jmxValue("MetricsCB.circuit-breaker.short-circuit-count", "Count").value shouldBe 1)
  }

  it should "allow a uniqueId mapper to be passed in" in {
    case class MyContext(s: String, id: Long)

    val delayActor = system.actorOf(Props[DelayActor]())
    import org.apache.pekko.pattern.ask
    val flow = Flow[(String, MyContext)].mapAsyncUnordered(5) { elem =>
      (delayActor ? elem).mapTo[(String, MyContext)]
    }

    val circuitBreakerSettings =
      CircuitBreakerSettings[String, String, MyContext](
        AtomicCircuitBreakerState("UniqueId", 2, timeout, 10.milliseconds))
        .withUniqueIdMapper(context => context.id)
    val circuitBreakerBidiFlow = CircuitBreaker(circuitBreakerSettings)


    var counter = 0L
    val result = Source("a" :: "b" :: "b" :: "a" :: Nil)
      .map { s => counter += 1; (s, MyContext("dummy", counter)) }
      .via(circuitBreakerBidiFlow.join(flow))
      .runWith(Sink.seq)

    val timeoutFailure = Failure(FlowTimeoutException())
    val expected =
      (Success("a"), MyContext("dummy", 1)) ::
      (Success("a"), MyContext("dummy", 4)) ::
      (timeoutFailure, MyContext("dummy", 2)) ::
      (timeoutFailure, MyContext("dummy", 3)) :: Nil

    assertFuture(result) { r => r should contain theSameElementsAs expected }
  }

  it should "allow a clean up callback function to be passed in" in {
    case class MyContext(s: String, id: Long)

    val promiseMap = Map(
      "a" -> Promise[Boolean](),
      "b" -> Promise[Boolean](),
      "c" -> Promise[Boolean]()
    )

    val isCleanedUp = Future.sequence(promiseMap.values.map(_.future))

    val cleanUpFunction = (s: String) => promiseMap.get(s).foreach(_.success(true))
    val notCleanedUpFunction = (s: String) => promiseMap.get(s).foreach(_.trySuccess(false))

    val delayActor = system.actorOf(Props[DelayActor]())
    import org.apache.pekko.pattern.ask
    val flow = Flow[(String, MyContext)].mapAsyncUnordered(5) { elem =>
      (delayActor ? elem).mapTo[(String, MyContext)]
    }

    val circuitBreakerSettings =
      CircuitBreakerSettings[String, String, MyContext](
        AtomicCircuitBreakerState("UniqueId", 2, timeout, 10.milliseconds))
        .withCleanUp(cleanUpFunction)
    val circuitBreakerBidiFlow = CircuitBreaker(circuitBreakerSettings)


    var counter = 0L
    val result = Source("a" :: "b" :: "c" :: Nil)
      .map { s => counter += 1; (s, MyContext("dummy", counter)) }
      .map { case (s, uuid) =>
        system.scheduler.scheduleOnce(checkCleanedUpTime)(notCleanedUpFunction(s))
        s -> uuid
      }
      .via(circuitBreakerBidiFlow.join(flow))
      .runWith(Sink.seq)

    val timeoutFailure = Failure(FlowTimeoutException())
    val expected =
      (Success("a"), MyContext("dummy", 1)) ::
        (Success("c"), MyContext("dummy", 3)) ::
        (timeoutFailure, MyContext("dummy", 2)) :: Nil

    assertFuture(result) {
      _ should contain theSameElementsAs expected
    }
    assertFuture(isCleanedUp) {
      _ should contain theSameElementsAs List(false, true, false)
    }
  }

  it should "increase the reset timeout exponentially after it transits to open again" in {
    val circuitBreakerState =
      AtomicCircuitBreakerState(
        "exponential-backoff-circuitbreaker",
        system.settings.config.getConfig("exponential-backoff-circuitbreaker"))
    circuitBreakerState.subscribe(self, HalfOpen)
    circuitBreakerState.subscribe(self, Open)
    val ref = flowWithCircuitBreaker(circuitBreakerState)
    ref ! "a"
    ref ! "b"
    ref ! "b"
    expectMsg(Open)
    expectNoMessage(10.milliseconds)
    expectMsg(HalfOpen)
    ref ! "b"
    expectMsg(Open)
    expectNoMessage(10.milliseconds)
    expectMsg(HalfOpen)

    1 to 6 foreach { i =>
      ref ! "b"
      expectMsg(Open)
      expectNoMessage(100.milliseconds)
      expectMsg(HalfOpen)
    }

    ref ! "b"
    expectMsg(Open)
    // reset-timeout should be maxed at 100 milliseconds.  Otherwise, it would have been 2.7 hours by this line.
    // Giving it 5 seconds as timing characteristics may not be as precise in different CI systems.
    expectMsg(5.seconds, HalfOpen)
  }

  it should "share the circuit breaker state across materializations" in {
    val circuitBreakerState = AtomicCircuitBreakerState(
      "MultipleMaterialization",
      2,
      timeout,
      10 * timeout)

    circuitBreakerState.subscribe(self, Open)

    val flow = Source.actorRef[String](completionMatcher, failureMatcher = PartialFunction.empty,
      25, OverflowStrategy.fail)
      .map(s => (s, UUID.randomUUID()))
      .via(CircuitBreaker[String, String, UUID](CircuitBreakerSettings(circuitBreakerState)).join(delayFlow()))
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
    ref1 ! Done
    ref2 ! Done

    assertFuture(result2) { r2 =>
      val expected = circuitBreakerOpenFailure :: circuitBreakerOpenFailure :: Nil
      r2 should contain theSameElementsInOrderAs expected
    }
  }

  private def assertFuture[T](future: Future[T])(assertion: T => Unit) = assertion(Await.result(future, 60.seconds))
}

object CircuitBreakerSpec {
  val config = ConfigFactory.parseString(
    """
      |pekko.test.single-expect-default = 30 seconds
      |
      |exponential-backoff-circuitbreaker {
      |  type = squbs.circuitbreaker
      |  max-failures = 2
      |  call-timeout = 1 s
      |  reset-timeout = 100 ms
      |  exponential-backoff-factor = 10.0
      |  max-reset-timeout = 2 s
      |}
    """.stripMargin)
}

class DelayActor extends Actor {

  import Timing._

  val delay = Map("a" -> shorterThenTimeout, "b" -> longerThenTimeout, "c" -> shorterThenTimeout)

  import context.dispatcher

  def receive = {
    case element: String => context.system.scheduler.scheduleOnce(delay(element), sender(), element)
    case element @ (s: String, _) => context.system.scheduler.scheduleOnce(delay(s), sender(), element)
  }
}

object Timing {
  val timeout = 1 second
  val shorterThenTimeout = timeout / 100
  val longerThenTimeout = timeout + (2.seconds)
  val checkCleanedUpTime = longerThenTimeout + (500 millisecond)
}