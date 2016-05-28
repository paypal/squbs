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

import java.util.concurrent.atomic.AtomicInteger

import akka.{Done, NotUsed}
import akka.actor.ActorContext
import akka.stream.ClosedShape
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl._

import scala.concurrent.Future
import scala.language.postfixOps

class ThrowExceptionStream extends PerpetualStream[Future[Done]] {

  def streamGraph = RunnableGraph.fromGraph(GraphDSL.create(updateCounter()) {implicit builder =>
    sink => startSource ~> injectError ~> sink
      ClosedShape
  })

  val injectError = Flow[Int].map { n =>
    if (n == 30000) throw new NumberFormatException("This is a fake exception")
    else n
  }
  val recordCount = new AtomicInteger(0)
  def updateCounter() = Sink.foreach[Any] { _ => recordCount.incrementAndGet() }

  private def startSource(implicit context: ActorContext): Source[Int, NotUsed] = Source(1 to 100000)

  override def shutdownHook() = { print ("Neo Stream Result " +  recordCount.get + "\n\n") }

}
