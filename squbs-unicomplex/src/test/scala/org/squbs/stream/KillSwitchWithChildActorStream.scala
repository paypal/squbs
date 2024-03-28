/*
 *  Copyright 2017 PayPal
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.squbs.stream
import java.util.concurrent.atomic.AtomicLong

import org.apache.pekko.actor.{Actor, Props}
import org.apache.pekko.stream.ClosedShape
import org.apache.pekko.stream.ThrottleMode.Shaping
import org.apache.pekko.stream.scaladsl.{Flow, GraphDSL, Keep, RunnableGraph, Sink, Source}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

object KillSwitchWithChildActorStream {
  val genCount = new AtomicLong(0L)
}

class DummyChildActor extends Actor {
  def receive = PartialFunction.empty
}

class KillSwitchWithChildActorStream extends PerpetualStream[Future[Long]] {
  import KillSwitchWithChildActorStream._
  import org.squbs.unicomplex.Timeouts._

  val dummyChildActor = context.actorOf(Props[DummyChildActor]())

  override def stopTimeout = awaitMax

  def generator = Iterator.iterate(0){ p => if (p == Int.MaxValue) 0 else p + 1 } map { v =>
    genCount.incrementAndGet()
    v
  }

  val source = Source.fromIterator(() => generator)

  val throttle = Flow[Int].throttle(5000, 1 second, 1000, Shaping)

  val counter = Flow[Int].map { _ => 1L }.reduce { _ + _ }.toMat(Sink.head)(Keep.right)

  override def streamGraph = RunnableGraph.fromGraph(GraphDSL.createGraph(counter) {
    implicit builder =>
      sink =>
        import GraphDSL.Implicits._
        source ~> killSwitch.flow[Int] ~> throttle ~> sink
        ClosedShape
  })

  override def receive = {
    case NotifyWhenDone =>

      // Send back the future directly here, don't map the future. The map will likely happen after ActorSystem
      // shutdown so we cannot use context.dispatcher as execution context for the map as it won't be there when
      // the map is supposed to happen.
      sender() ! matValue
  }

  override def shutdown() = {
    val f = super.shutdown()
    defaultMidActorStop(Seq(dummyChildActor))
    f
  }
}
