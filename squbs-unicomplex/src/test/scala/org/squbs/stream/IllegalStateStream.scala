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

import org.apache.pekko.{Done, NotUsed}
import org.apache.pekko.actor.ActorContext
import org.apache.pekko.stream.ClosedShape
import org.apache.pekko.stream.scaladsl.GraphDSL.Implicits._
import org.apache.pekko.stream.scaladsl._
import org.squbs.unicomplex.Initialized

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Try

class IllegalStateStream extends PerpetualStream[Future[Int]] {
    def streamGraph = RunnableGraph.fromGraph(GraphDSL.createGraph(endSink) {implicit builder =>
    sink => startSource ~> sink
  ClosedShape
  })

  // By accessing matValue here, it should generate an IllegalStateException.
  // Reporting it to the parent would cause the cube and the system to be at state Failed.
  val t = Try { Option(matValue.toString) }
  context.parent ! Initialized(t)

  private def startSource(implicit context: ActorContext): Source[Int, NotUsed] = Source(1 to 10).map(_ * 1)

  private def endSink(implicit context: ActorContext): Sink[Int, Future[Int]] = {
    val sink = Sink.fold[Int, Int](0)(_ + _)
    sink
   }
  override def shutdown() = {
    print("Neo Stream Result " +  matValue.value.get.get + "\n\n")
    Future.successful(Done)
  }

}
