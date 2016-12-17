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

package org.squbs.streams

import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.{ActorMaterializer, FlowShape}
import akka.stream.scaladsl._
import akka.testkit.TestKit
import akka.util.Timeout
import org.scalatest.{AsyncFlatSpecLike, Matchers}
import org.squbs.streams.StreamTimeout.TimerEnvelope

import scala.concurrent.duration._
import scala.util.{Failure, Success}

class TimeoutFlowSpec extends TestKit(ActorSystem("TimeoutFlowSpec")) with AsyncFlatSpecLike with Matchers{

  implicit val materializer = ActorMaterializer()
  import StreamTimeout._

  val timeout = 30 milliseconds

  it should "timeout a message if the flow does not process it within provided timeout" in {
    import scala.concurrent.duration._

    val flow = Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val partition = b.add(Partition(3, (s: String) => s match {
        case "a" => 0
        case "b" => 1
        case "c" => 2
      }))

      val merge = b.add(Merge[String](3))

      partition.out(0).delay(10 milliseconds)  ~> merge
      partition.out(1).delay(100 milliseconds) ~> merge
      partition.out(2).delay(10 milliseconds)  ~> merge

      FlowShape(partition.in, merge.out)
    })

    val result = Source("a" :: "b" :: "c" :: Nil).via(flow.withTimeout(timeout)).runWith(Sink.seq)
    // "c" fails because of slowness of "b"
    val expected = Success("a") :: Failure(FlowTimeoutException()) :: Failure(FlowTimeoutException()) :: Nil
    result map { _ should contain theSameElementsAs expected }
  }

  it should "work for flows that keep the order of messages" in {
    val delayActor = system.actorOf(Props[DelayActor])
    import akka.pattern.ask
    implicit val askTimeout = Timeout(5.seconds)
    val flow = Flow[String].mapAsync(3)(elem => (delayActor ? elem).mapTo[String])

    val result = Source("a" :: "b" :: "c" :: Nil).via(flow.withTimeout(timeout)).runWith(Sink.seq)
    // "c" fails because of slowness of "b"
    val expected = Success("a") :: Failure(FlowTimeoutException()) :: Failure(FlowTimeoutException()) :: Nil
    result map { _ should contain theSameElementsInOrderAs expected }
  }

  it should "work for flows that do not keep the order of messages" in {
    val delayActor = system.actorOf(Props[DelayActor])
    import akka.pattern.ask
    implicit val askTimeout = Timeout(5.seconds)
    val flow = Flow[TimerEnvelope[Long, String]].mapAsyncUnordered(3) { elem =>
      (delayActor ? elem).mapTo[TimerEnvelope[Long, String]]
    }

    val result = Source("a" :: "b" :: "c" :: Nil).via(flow.withTimeout(timeout)).runWith(Sink.seq)
    // "c" does NOT fail because the original flow lets it go earlier than "b"
    val expected = Success("a") :: Failure(FlowTimeoutException()) :: Success("c") :: Nil
    result map { _ should contain theSameElementsAs expected }
  }

  it should "allow a custom id generator to be passed in" in {
    val delayActor = system.actorOf(Props[DelayActor])
    import akka.pattern.ask
    implicit val askTimeout = Timeout(5.seconds)
    val flow = Flow[String].mapAsync(3)(elem => (delayActor ? elem).mapTo[String])

    val result = Source("a" :: "b" :: "c" :: Nil).via(flow.withTimeout(timeout,
                                                      (new BigIntIdGenerator).nextId())).runWith(Sink.seq)
    // "c" fails because of slowness of "b"
    val expected = Success("a") :: Failure(FlowTimeoutException()) :: Failure(FlowTimeoutException()) :: Nil
    result map { _ should contain theSameElementsInOrderAs expected }
  }

  it should "allow a custom id generator to be passed in for flows that do not keep the order of messages" in {
    val delayActor = system.actorOf(Props[DelayActor])
    import akka.pattern.ask
    implicit val askTimeout = Timeout(5.seconds)
    val flow = Flow[TimerEnvelope[BigInt, String]].mapAsyncUnordered(3) { elem =>
      (delayActor ? elem).mapTo[TimerEnvelope[BigInt, String]]
    }

    val result = Source("a" :: "b" :: "c" :: Nil).via(flow.withTimeout(timeout,
                                                      (new BigIntIdGenerator).nextId())).runWith(Sink.seq)
    // "c" does NOT fail because the original flow lets it go earlier than "b"
    val expected = Success("a") :: Failure(FlowTimeoutException()) :: Success("c") :: Nil
    result map { _ should contain theSameElementsAs expected }
  }
}

class BigIntIdGenerator {
  // This is not concurrent, nor needs it to be
  var id = BigInt(0)

  def nextId() = () => {
    // It may overflow, which should be ok for most scenarios.  If the message with id 0 is still not completed
    // after it overflowed, then deduplication logic would be broken.  For such scenarios, a different
    // id generator should be used.
    id = id + 1
    id
  }
}

class DelayActor extends Actor {

  val delay = Map("a" -> 10.milliseconds, "b" -> 100.milliseconds, "c" -> 10.milliseconds)

  def receive = {
    case element: String =>
      import context.dispatcher
      context.system.scheduler.scheduleOnce(delay(element), sender(), element)
    case element: TimerEnvelope[Long, String] =>
      import context.dispatcher
      context.system.scheduler.scheduleOnce(delay(element.value), sender(), element)
  }
}
