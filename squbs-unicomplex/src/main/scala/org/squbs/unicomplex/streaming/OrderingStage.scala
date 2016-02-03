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

package org.squbs.unicomplex.streaming

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}

import scala.annotation.tailrec
import scala.collection.mutable

class OrderingStage[A, B](initialState: B, updateState: B => B, getState: A => B)(implicit val ordering: Ordering[A])
  extends GraphStage[FlowShape[A, A]] {

  val in = Inlet[A]("Filter.in")
  val out = Outlet[A]("Filter.out")
  val shape = FlowShape.of(in, out)

  @tailrec private def elemsToPush(l: List[A], state: B, pq: mutable.PriorityQueue[A]): List[A] = {

    pq.headOption match {
      case Some(e) if state == e => pq.dequeue()
        elemsToPush(e :: l, updateState(state), pq)
      case _ => l
    }
  }

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {

      var state = initialState

      val pq = mutable.PriorityQueue.empty[A]

      setHandler(in, new InHandler {
        override def onPush(): Unit = {

          val elem = grab[A](in)

          if(state == getState(elem)) {
            val elems = elemsToPush(elem :: Nil, updateState(state), pq)

            for(i <- 0 until elems.size) state = updateState(state)

            emitMultiple(out, elems.reverse)
          }
          else {
            pq.enqueue(elem)
            tryPull(in)
          }
        }
      })

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          tryPull(in)
        }
      })
    }
}