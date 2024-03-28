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

import java.util.concurrent.atomic.AtomicInteger

import org.apache.pekko.{Done, NotUsed}
import org.apache.pekko.actor.ActorContext
import org.apache.pekko.stream.ClosedShape
import org.apache.pekko.stream.scaladsl.GraphDSL.Implicits._
import org.apache.pekko.stream.scaladsl._

import scala.concurrent.Future
import scala.language.postfixOps

case object NotifyWhenDone {
  def getInstance: NotifyWhenDone.type = this
}

object ThrowExceptionStream {

  val limit = 50000
  val exceptionAt = limit * 3 / 10
  val recordCount = new AtomicInteger(0)
}

class ThrowExceptionStream extends PerpetualStream[Future[Int]] {

  import ThrowExceptionStream._

  def streamGraph = RunnableGraph.fromGraph(GraphDSL.createGraph(counter) { implicit builder =>
    sink =>
      startSource ~> injectError ~> sink
      ClosedShape
  })

  val injectError = Flow[Int].map { n =>
    if (n == exceptionAt) throw new NumberFormatException("This is a fake exception")
    else n
  }

  def counter = Flow[Any].map{ _ => recordCount.incrementAndGet(); 1 }.reduce{ _ + _ }.toMat(Sink.head)(Keep.right)

  override def receive = {
    case NotifyWhenDone =>
      import context.dispatcher
      val target = sender()
      matValue foreach { v => target ! v }
  }

  private def startSource(implicit context: ActorContext): Source[Int, NotUsed] = Source(1 to limit)

  override def shutdown() = {
    println("Neo Stream Result " + recordCount.get + "\n\n")
    Future.successful(Done)
  }

}
