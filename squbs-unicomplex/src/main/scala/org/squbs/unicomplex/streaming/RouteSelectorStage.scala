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

final class RouteSelectorStage[A, B](routes: Seq[B], extractItemToMatch: A => B, isMatch: (B, B) => Boolean) extends GraphStage[UniformFanOutShape[A, A]] {

  val numOfRoutes = routes.size
  val in = Inlet[A]("RouteSelector.in")
  val out: immutable.IndexedSeq[Outlet[A]] = Vector.tabulate(numOfRoutes + 1)(i ⇒ Outlet[A]("RouteSelector.out" + i))
  override val shape: UniformFanOutShape[A, A] = UniformFanOutShape[A, A](in, out: _*)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {

      // TODO This needs to be outputspecific so that we should not emit without a pull.
      private var pullCount = 0
      private var hasPulled = false

      setHandler(in, new InHandler {
        override def onPush(): Unit = {

          val elem = grab[A](in)
          val itemToMatch = extractItemToMatch(elem)

          val matchedRoute = routes.zipWithIndex.find { case (p1, i) =>
              isMatch(itemToMatch, p1)
          }

          // If no match, give the last port's index (which is the NOT_FOUND use case)
          val outPortIndex = matchedRoute map { case(_, index) => index} getOrElse numOfRoutes
          emit(out(outPortIndex), elem)

          if(pullCount > 0) {
            tryPull(in)
            pullCount = pullCount - 1
          }
        }
      })

      out.foreach { o ⇒
        setHandler(o, new OutHandler {

          override def onPull(): Unit = {
            if(!hasPulled) {
              tryPull(in)
              hasPulled = true
            } else {
              pullCount = pullCount + 1
            }
          }

          // TODO onDownstreamFinish
        })
      }
    }
}