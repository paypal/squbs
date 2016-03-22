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

package org.squbs.unicomplex.streaming

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream._

import scala.annotation.tailrec
import scala.collection.{immutable, mutable}

object RouteSelectorStage {

  def apply[A, B](routes: Seq[B], extractItemToMatch: A => B, isMatch: (B, B) => Boolean): RouteSelectorStage[A, B] = {
    new RouteSelectorStage(routes, extractItemToMatch, isMatch)
  }
}

/**
  * Fan-out the stream to several streams emitting each incoming upstream element to the right downstream
  * based on the [[extractItemToMatch]] and [[isMatch]] calls
  *
  * '''Emits when''' all of the outputs stops backpressuring and there is an input element available
  *
  * '''Backpressures when''' any of the outputs backpressures
  *
  * '''Completes when''' upstream completes
  *
  * '''Cancels when''' any downstream cancels
  */
final class RouteSelectorStage[A, B](routes: Seq[B], extractItemToMatch: A => B, isMatch: (B, B) => Boolean) extends GraphStage[UniformFanOutShape[A, A]] {

  val numOfRoutes = routes.size
  val in = Inlet[A]("RouteSelector.in")
  val out: immutable.IndexedSeq[Outlet[A]] = Vector.tabulate(numOfRoutes + 1)(i ⇒ Outlet[A]("RouteSelector.out" + i))
  override val shape: UniformFanOutShape[A, A] = UniformFanOutShape[A, A](in, out: _*)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {

      private val outportPulled = Array.ofDim[Boolean](numOfRoutes + 1)

      setHandler(in, new InHandler {
        override def onPush(): Unit = {

          val elem = grab[A](in)
          val matchedRoute = routes.zipWithIndex.find { case (p1, i) => isMatch(extractItemToMatch(elem), p1) }
          // If no match, give the last port's index (which is the NOT_FOUND use case)
          val outPortIndex = matchedRoute map { case(_, index) => index} getOrElse numOfRoutes
          push(out(outPortIndex), elem)
          outportPulled(outPortIndex) = false
        }
      })

      out.zipWithIndex.foreach { case(o, i) ⇒
        setHandler(o, new OutHandler {

          override def onPull(): Unit = {
            outportPulled(i) = true
            if(outportPulled.forall(b => b)) tryPull(in)
          }
        })
      }
    }
}