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

package org.squbs.streams

import org.apache.pekko.actor.{Actor, ActorSystem, Props}
import org.apache.pekko.stream.scaladsl._
import org.apache.pekko.stream.{Attributes, FlowShape}
import org.apache.pekko.testkit.TestKit
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.streams.UniqueId.{Envelope, Provider}

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import scala.compat.java8.FunctionConverters._
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.language.postfixOps
import scala.util.{Failure, Success}

class TimeoutSpec extends TestKit(ActorSystem("TimeoutBidiFlowSpec")) with AsyncFlatSpecLike with Matchers{
  import Timing._

  implicit val askTimeout = org.apache.pekko.util.Timeout(10.seconds)

  val timeoutFailure = Failure(FlowTimeoutException())

  it should "timeout a message if the flow does not process it within provided timeout" in {

    val flow = Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val partition = b.add(Partition(3, (s: String) => s match {
        case "a" => 0
        case "b" => 1
        case "c" => 2
      }))

      val merge = b.add(Merge[String](3))

      partition.out(0).delay(shorterThenTimeout)  ~> merge
      partition.out(1).delay(longerThenTimeout)   ~> merge
      partition.out(2).delay(shorterThenTimeout)  ~> merge

      FlowShape(partition.in, merge.out)
    })

    val timeoutFlow = TimeoutOrdered[String, String](timeout)

    val result = Source("a" :: "b" :: "c" :: Nil).via(timeoutFlow.join(flow)).runWith(Sink.seq)
    // "c" fails because of slowness of "b"
    val expected = Success("a") :: timeoutFailure :: Success("c") :: Nil
    result map { _ should contain theSameElementsAs expected }
  }

  it should "timeout a message and call clean up function" in {

    val promiseMap = Map(
      "a" -> Promise[Boolean](),
      "b" -> Promise[Boolean](),
      "c" -> Promise[Boolean]()
    )

    val isCleanedUp = Future.sequence(promiseMap.values.map(_.future))

    val cleanUpFunction = (s: String) => promiseMap.get(s).foreach(_.success(true))
    val notCleanedUpFunction = (s: String) => promiseMap.get(s).foreach(_.trySuccess(false))

    val flow = Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val partition = b.add(Partition(3, (s: String) => s match {
        case "a" => 0
        case "b" => 1
        case "c" => 2
      }))

      val merge = b.add(Merge[String](3))

      partition.out(0).delay(shorterThenTimeout)  ~> merge
      partition.out(1).delay(longerThenTimeout)   ~> merge
      partition.out(2).delay(shorterThenTimeout)  ~> merge

      FlowShape(partition.in, merge.out)
    })

    val timeoutFlow = TimeoutOrdered[String, String](timeout, cleanUp = cleanUpFunction)

    val result = Source("a" :: "b" :: "c" :: Nil)
      .map{ s =>
        system.scheduler.scheduleOnce(checkCleanedUpTime)(notCleanedUpFunction(s))
        s
      }
      .via(timeoutFlow.join(flow))
      .runWith(Sink.seq)
    // "c" fails because of slowness of "b"
    val expected = Success("a") :: timeoutFailure :: Success("c") :: Nil
    result map { _ should contain theSameElementsAs expected } flatMap {_ =>
      isCleanedUp.map{ _ should contain theSameElementsAs List(false, true, false)}
    }
  }

  it should "timeout elements for flows that keep the order of messages" in {
    val delayActor = system.actorOf(Props[DelayActor]())
    import org.apache.pekko.pattern.ask
    val flow = Flow[String].mapAsync(3)(elem => (delayActor ? elem).mapTo[String])

    val timeoutBidiFlow = TimeoutOrdered[String, String](timeout)

    val result = Source("a" :: "b" :: "c" :: Nil).via(timeoutBidiFlow.join(flow)).runWith(Sink.seq)
    // "c" fails because of slowness of "b"
    val expected = Success("a") :: timeoutFailure :: timeoutFailure :: Nil
    result map { _ should contain theSameElementsInOrderAs expected }
  }

  it should "let the wrapped ordered flow control the demand" in {
    val delayActor = system.actorOf(Props[DelayActor]())
    import org.apache.pekko.pattern.ask
    val flow = Flow[String]
      .withAttributes(Attributes.inputBuffer(initial = 2, max = 2))
      .mapAsync(2)(elem => (delayActor ? elem).mapTo[String])

    val timeoutBidiFlow = TimeoutOrdered[String, String](timeout)

    val result = Source("a" :: "b" :: "c" :: "c" :: "a" :: "a" :: Nil).via(timeoutBidiFlow.join(flow)).runWith(Sink.seq)
    // The first "c" fails because of slowness of "b".  With mapAsync(2), subsequent messages should not be delayed.
    val expected = Success("a") :: timeoutFailure :: timeoutFailure :: Success("c") :: Success("a") :: Success("a") :: Nil
    result map { _ should contain theSameElementsInOrderAs expected }
  }

  it should "let the wrapped unordered flow control the demand" in {
    val delayActor = system.actorOf(Props[DelayActor]())
    import org.apache.pekko.pattern.ask
    val flow = Flow[(String, Long)]
      .withAttributes(Attributes.inputBuffer(initial = 1, max = 1))
      .mapAsyncUnordered(1) { elem =>
      (delayActor ? elem).mapTo[(String, Long)]
    }

    var id = 0L
    val timeoutBidiFlow = Timeout[String, String, Long](timeout)
    val result = Source("a" :: "b" :: "c" :: "c" :: "a" :: "a" :: Nil)
      .map { s => id += 1; (s, id) }
      .via(timeoutBidiFlow.join(flow))
      .map { case(s, _) => s }
      .runWith(Sink.seq)
    val expected = Success("a") :: timeoutFailure :: Success("c") :: Success("c") :: Success("a") :: Success("a") :: Nil
    result map { _ should contain theSameElementsAs expected }
  }

  it should "not complete the flow until timeout messages are sent when the ordered wrapped flow drop messages" in {
    val flow = Flow[String].filter(_ => false)
    val timeoutBidiFlow = TimeoutOrdered[String, String](timeout)

    val result = Source("a" :: "b" :: "c" :: Nil).via(timeoutBidiFlow.join(flow)).runWith(Sink.seq)
    val expected = timeoutFailure :: timeoutFailure :: timeoutFailure :: Nil
    result map { _ should contain theSameElementsInOrderAs expected }
  }

  it should "not complete the flow until timeout messages are sent when the unordered wrapped flow drop messages" in {
    val flow = Flow[(String, Long)].filter(_ => false)
    val timeoutBidiFlow = Timeout[String, String, Long](timeout)
    var id = 0L
    val result = Source("a" :: "b" :: "c" :: Nil)
      .map { s => id += 1; (s, id) }
      .via(timeoutBidiFlow.join(flow))
      .map { case(s, _) => s }
      .runWith(Sink.seq)
    val expected = timeoutFailure :: timeoutFailure :: timeoutFailure :: Nil
    result map { _ should contain theSameElementsAs expected }
  }

  it should "timeout elements for flows that do not keep the order of messages" in {
    val delayActor = system.actorOf(Props[DelayActor]())
    import org.apache.pekko.pattern.ask
    val flow = Flow[(String, UUID)].mapAsyncUnordered(3) { elem =>
      (delayActor ? elem).mapTo[(String, UUID)]
    }

    val timeoutBidiFlow = Timeout[String, String, UUID](timeout)
    val result = Source("a" :: "b" :: "c" :: Nil)
      .map { s => (s, UUID.randomUUID()) }
      .via(timeoutBidiFlow.join(flow))
      .map { case(s, _) => s }
      .runWith(Sink.seq)
    // "c" does NOT fail because the original flow lets it go earlier than "b"
    val expected = Success("a") :: timeoutFailure :: Success("c") :: Nil
    result map { _ should contain theSameElementsAs expected }
  }

  it should "clean up timed out elements for flows that do not keep the order of messages" in {
    val delayActor = system.actorOf(Props[DelayActor]())
    val promiseMap = Map(
      "a" -> Promise[Boolean](),
      "b" -> Promise[Boolean](),
      "c" -> Promise[Boolean]()
    )

    val isCleanedUp = Future.sequence(promiseMap.values.map(_.future))

    val notCleanedUpFunction = (s: String) => promiseMap.get(s).foreach(_.trySuccess(false))

    import org.apache.pekko.pattern.ask
    val flow = Flow[(String, UUID)].mapAsyncUnordered(3) { elem =>
      (delayActor ? elem).mapTo[(String, UUID)]
    }

    val settings = TimeoutSettings[String, String, UUID](timeout)
      .withCleanUp(asJavaConsumer((s: String) => promiseMap.get(s).foreach(_.success(true))))
    val timeoutBidiFlow = Timeout(settings)
    val result = Source("a" :: "b" :: "c" :: Nil)
      .map { s => (s, UUID.randomUUID()) }
      .map { case (s, uuid) =>
        system.scheduler.scheduleOnce(checkCleanedUpTime)(notCleanedUpFunction(s))
        s -> uuid
      }
      .via(timeoutBidiFlow.join(flow))
      .map { case(s, _) => s }
      .runWith(Sink.seq)
    // "c" does NOT fail because the original flow lets it go earlier than "b"
    val expected = Success("a") :: timeoutFailure :: Success("c") :: Nil
    result map { _ should contain theSameElementsAs expected } flatMap{ _ =>
      isCleanedUp.map {
        _ should contain theSameElementsAs List(false, true, false)
      }
    }
  }

  it should "allow a custom uniqueId mapper to be passed in" in {
    val counter = new AtomicInteger(0)

    case class MyContext(s: String, uuid: UUID) {
      override def hashCode(): Int = counter.incrementAndGet() // On purpose, a problematic hashcode
    }

    val delayActor = system.actorOf(Props[DelayActor]())
    import org.apache.pekko.pattern.ask
    val flow = Flow[(String, MyContext)].mapAsyncUnordered(3) { elem =>
      (delayActor ? elem).mapTo[(String, MyContext)]
    }

    val settings = TimeoutSettings[String, String, MyContext](timeout).withUniqueIdMapper(context => context.uuid)
    val timeoutBidiFlow = Timeout(settings)

    val result = Source("a" :: "b" :: "c" :: Nil)
      .map { s => (s, MyContext("dummy", UUID.randomUUID())) }
      .via(timeoutBidiFlow.join(flow))
      .map { case(s, _) => s }
      .runWith(Sink.seq)
    // "c" does NOT fail because the original flow lets it go earlier than "b"
    val expected = Success("a") :: timeoutFailure :: Success("c") :: Nil
    result map { _ should contain theSameElementsAs expected }
  }

  it should "get the id from uniqueId function" in {
    val counter = new AtomicInteger(0)

    case class MyContext(s: String, uuid: UUID) extends Provider {
      override def uniqueId: Any = uuid
      override def hashCode(): Int = counter.incrementAndGet() // On purpose, a problematic hashcode
    }

    val delayActor = system.actorOf(Props[DelayActor]())
    import org.apache.pekko.pattern.ask
    val flow = Flow[(String, MyContext)].mapAsyncUnordered(3) { elem =>
      (delayActor ? elem).mapTo[(String, MyContext)]
    }

    val timeoutBidiFlow = Timeout[String, String, MyContext](timeout)

    val result = Source("a" :: "b" :: "c" :: Nil)
      .map { s => (s, MyContext("dummy", UUID.randomUUID())) }
      .via(timeoutBidiFlow.join(flow))
      .map { case(s, _) => s }
      .runWith(Sink.seq)
    // "c" does NOT fail because the original flow lets it go earlier than "b"
    val expected = Success("a") :: timeoutFailure :: Success("c") :: Nil
    result map { _ should contain theSameElementsAs expected }
  }

  it should "use the id that is passed with UniqueIdEnvelope" in {
    val delayActor = system.actorOf(Props[DelayActor]())
    import org.apache.pekko.pattern.ask
    val flow = Flow[(String, Envelope)].mapAsyncUnordered(3) { elem =>
      (delayActor ? elem).mapTo[(String, Envelope)]
    }

    val timeoutBidiFlow = Timeout[String, String, Envelope](timeout)

    val result = Source("a" :: "b" :: "c" :: Nil)
      .map { s => (s, Envelope("dummy", UUID.randomUUID())) }
      .via(timeoutBidiFlow.join(flow))
      .map { case(s, _) => s }
      .runWith(Sink.seq)
    // "c" does NOT fail because the original flow lets it go earlier than "b"
    val expected = Success("a") :: timeoutFailure :: Success("c") :: Nil
    result map { _ should contain theSameElementsAs expected }
  }

  it should "retrieve the unique id correctly" in {
    case class MyContext(id: Int) extends Provider {
      override def uniqueId: Any = id
    }

    (new Timeout(TimeoutSettings[String, String, Int](timeout))).uniqueId(1) should be(1)
    (new Timeout(TimeoutSettings[String, String, MyContext](timeout))).uniqueId(MyContext(2)) should be(2)
    (new Timeout(TimeoutSettings[String, String, Envelope](timeout))).uniqueId(Envelope("dummy", 3)) should be(3)
    (new Timeout(TimeoutSettings[String, String, MyContext](timeout).withUniqueIdMapper(context => context.id + 1))).uniqueId(MyContext(4)) should be(5)
  }
}


class DelayActor extends Actor {
  import Timing._

  val delay = Map("a" -> shorterThenTimeout, "b" -> longerThenTimeout, "c" -> shorterThenTimeout)
  import context.dispatcher

  def receive = {
    case element: String =>
      context.system.scheduler.scheduleOnce(delay(element), sender(), element)
    case element @ (s: String, _) =>
      context.system.scheduler.scheduleOnce(delay(s), sender(), element)
    case element @ org.apache.pekko.japi.Pair(s: String, _) =>
      context.system.scheduler.scheduleOnce(delay(s), sender(), element)
  }
}

object Timing {
  val timeout = 1 second
  val shorterThenTimeout = timeout / 100
  val longerThenTimeout = timeout + (2.seconds)
  val checkCleanedUpTime = longerThenTimeout + (500 millisecond)
}
