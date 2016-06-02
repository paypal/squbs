/*
 *  Copyright 2015 PayPal
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

import akka.Done
import akka.actor.{ActorRef, PoisonPill}
import akka.stream.ClosedShape
import akka.stream.ThrottleMode.Shaping
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, RunnableGraph, Sink, Source}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

object ProperShutdownStream {
  val genCount = new AtomicLong(0L)
}

class ProperShutdownStream extends PerpetualStream[(ActorRef, Future[Long])] {
  import ProperShutdownStream._
  import context.system

  val generator = Iterator.iterate(0){ p =>
    genCount.incrementAndGet()
    if (p == Int.MaxValue) 0 else p + 1
  }

  val managedSource = LifecycleManaged().source(Source.fromIterator { () => generator })

  val throttle = Flow[Int].throttle(5000, 1 second, 1000, Shaping)

  val counter = Flow[Int].map { _ => 1L }.reduce { _ + _ }.toMat(Sink.head)(Keep.right)

  override def streamGraph = RunnableGraph.fromGraph(GraphDSL.create(managedSource, counter)((a, b) => (a._2, b)) {
    implicit builder =>
    (source, sink) =>
      import GraphDSL.Implicits._
      source ~> throttle ~> sink
      ClosedShape
  })

  override def receive = {
    case NotifyWhenDone =>
      val (_, fCount) = matValue
      sender() ! fCount
  }

  override def shutdownHook() = {
    import context.dispatcher
    val (actorRef, fCount) = matValue
    actorRef ! PoisonPill    // Cancel the stream head.
    fCount map { _ => Done } // Return a Future[Done] indicating the stream is finished.
  }
}
