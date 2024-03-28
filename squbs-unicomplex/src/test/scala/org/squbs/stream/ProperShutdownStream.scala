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
import org.apache.pekko.Done
import org.apache.pekko.actor.{ActorRef, PoisonPill}
import org.apache.pekko.stream.ClosedShape
import org.apache.pekko.stream.ThrottleMode.Shaping
import org.apache.pekko.stream.scaladsl.{Flow, GraphDSL, Keep, RunnableGraph, Sink, Source}
import org.squbs.lifecycle.GracefulStop

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

object ProperShutdownStream {
  val genCount = new AtomicLong(0L)
}

class ProperShutdownStream extends PerpetualStream[(() => ActorRef, Future[Long])] {
  import ProperShutdownStream._
  import org.squbs.unicomplex.Timeouts._

  override def stopTimeout = awaitMax

  def generator = Iterator.iterate(0){ p => if (p == Int.MaxValue) 0 else p + 1 } map { v =>
    genCount.incrementAndGet()
    v
  }

  val managedSource = LifecycleManaged().source(Source.fromIterator(() => generator))

  val throttle = Flow[Int].throttle(5000, 1 second, 1000, Shaping)

  val counter = Flow[Int].map { _ => 1L }.reduce { _ + _ }.toMat(Sink.head)(Keep.right)

  override def streamGraph = RunnableGraph.fromGraph(GraphDSL.createGraph(managedSource, counter)((a, b) => (a._2, b)) {
    implicit builder =>
    (source, sink) =>
      import GraphDSL.Implicits._
      source ~> throttle ~> sink
      ClosedShape
  })

  override def receive = {
    case NotifyWhenDone =>
      val (_, fCount) = matValue

      // Send back the future directly here, don't map the future. The map will likely happen after ActorSystem
      // shutdown so we cannot use context.dispatcher as execution context for the map as it won't be there when
      // the map is supposed to happen.
      sender() ! fCount
  }

  override def shutdown() = {
    super.shutdown()
    import context.dispatcher
    val (actorRef, fCount) = matValue
    val fStopped = gracefulStop(actorRef(), awaitMax, GracefulStop)
    for { _ <- fCount; _ <- fStopped } yield Done
  }
}
