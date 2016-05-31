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

import akka.actor.ActorContext
import akka.stream.ClosedShape
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl._
import akka.NotUsed

import scala.concurrent.Future
import scala.language.postfixOps

object ProperShutdownStream {
  val sourceElement = 1
}

class ProperShutdownStream extends PerpetualStream[Future[Int]] {
  import ProperShutdownStream._

  def streamGraph = RunnableGraph.fromGraph(GraphDSL.create(endSink) {implicit builder =>
    sink => startSource ~> sink
      ClosedShape
  })

  private def startSource(implicit context: ActorContext): Source[Int, NotUsed] = Source.repeat(sourceElement)

  def endSink(implicit context: ActorContext): Sink[Int, Future[Int]] = {
    val sink = Sink.head[Int]
    sink
  }

  override def shutdownHook() = {
    import context.dispatcher
    val target = sender()
    matValue foreach { v => target ! v }
  }
}
