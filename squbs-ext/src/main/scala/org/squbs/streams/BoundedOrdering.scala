/*
 *  Copyright 2018 PayPal
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

package org.squbs.streams

import java.util.Comparator
import java.util.function.{Function => JFunc}

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.javadsl.{Flow => JFlow}
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import org.apache.pekko.stream.{Attributes, FlowShape, Inlet, Outlet}

import scala.annotation.tailrec
import scala.collection.immutable.Queue
import scala.collection.mutable

object BoundedOrdering {

  /**
   * Scala API
   *
   * Creates a [[Flow]] representing an [[BoundedOrdering]]. Orders the stream within a sliding window of n
   * elements specified by the maxBounded parameter. Each element passing through the [[BoundedOrdering]] has
   * an id. The elements can be ordered by the ordering function and the next expected id can be derived from
   * the current id.
   *
   * @param maxBounded    The number of elements to wait for an out-of-order element.
   * @param initialId The id of the first element getting to the [[BoundedOrdering]].
   * @param nextId    Function to calculate the subsequent id from the current id.
   * @param getId     Function to get the id from an element.
   * @param ordering  Defines how the element ids are being ordered.
   * @tparam A The input and output type of this stream component.
   * @tparam B The type of the element's id.
   * @return The [[Flow]] representing the given [[BoundedOrdering]]
   */
  def apply[A, B](maxBounded: Int, initialId: B, nextId: B => B, getId: A => B)(implicit ordering: Ordering[B]):
  Flow[A, A, NotUsed] =
    Flow.fromGraph(new BoundedOrdering(maxBounded, initialId, nextId, getId))

  /**
   * Java API
   *
   * Creates a [[Flow]] representing an [[BoundedOrdering]] using the natural ordering of the element id.
   * Orders the stream within a sliding window of n elements specified by the maxBounded parameter.
   * Each element passing through the [[BoundedOrdering]] has an id. The elements can be ordered by comparing the id
   * and the next expected id can be derived from the current id.
   *
   * @param maxBounded    The number of elements to wait for an out-of-order element.
   * @param initialId The id of the first element getting to the [[BoundedOrdering]].
   * @param nextId    Function to calculate the subsequent id from the current id.
   * @param getId     Function to get the id from an element.
   * @tparam A The input and output type of this stream component.
   * @tparam B The type of the element's id.
   * @return The [[Flow]] representing the given [[BoundedOrdering]]
   */
  def create[A, B <: Comparable[B]](maxBounded: Int, initialId: B, nextId: JFunc[B, B], getId: JFunc[A, B]):
  JFlow[A, A, NotUsed] =
    JFlow.fromGraph(new BoundedOrdering(maxBounded, initialId, nextId.apply, getId.apply)
    (new Ordering[B] {
      override def compare(x: B, y: B): Int = x.compareTo(y)
    }))

  /**
   * Java API
   *
   * Creates a [[Flow]] representing an [[BoundedOrdering]]. Orders the stream within a sliding window of n
   * elements specified by the maxBounded parameter. Each element passing through the [[BoundedOrdering]] has
   * an id. The elements can be ordered by the id comparator and the next expected id can be derived from the
   * current id.
   *
   * @param maxBounded     The number of elements to wait for an out-of-order element.
   * @param initialId  The id of the first element getting to the [[BoundedOrdering]].
   * @param nextId     Function to calculate the subsequent id from the current id.
   * @param getId      Function to get the id from an element.
   * @param comparator The comparator used to find the ordering of the elements' ids.
   * @tparam A The input and output type of this stream component.
   * @tparam B The type of the element's id.
   * @return The [[Flow]] representing the given [[BoundedOrdering]]
   */
  def create[A, B](maxBounded: Int, initialId: B, nextId: JFunc[B, B], getId: JFunc[A, B], comparator: Comparator[B]):
  JFlow[A, A, NotUsed] =
    JFlow.fromGraph(new BoundedOrdering[A, B](maxBounded, initialId, nextId.apply, getId.apply)
    (Ordering.comparatorToOrdering(comparator)))
}

/**
 * Orders the stream within a sliding window of [[maxBounded]] elements. Each element passing through the
 * [[BoundedOrdering]] has an id. The elements can be ordered by the ordering function and the next expected
 * id can be derived from the current id.
 *
 * It takes an element and emits downstream if the [[getId(elem)]] matches the currently stored id, if not enqueues to
 * a priority queue. When an element is emitted, the stored id is updated by calling [[nextId]] to match the next
 * expected element and an element is dequeued from the priority queue and emitted if it matches the updated id.
 * If the out-of-order element arrives at a later point than [[maxBounded]] then it is emitted straight downstream.
 *
 * @param maxBounded    The number of elements to wait for an out-of-order element.
 * @param initialId The id of the first element getting to the [[BoundedOrdering]].
 * @param nextId    Function to calculate the subsequent id from the current id.
 * @param getId     Function to get the id from an element.
 * @param ordering  Defines how the element ids are being ordered.
 * @tparam A The input and output type of this stream component.
 * @tparam B The type of the element's id.
 *
 * '''Emits when''' [[getId(elem)]] matches the current state, if not enqueues it to priority queue
 *
 * '''Backpressures when''' downstream backpressures
 *
 * '''Completes when''' upstream completes
 *
 * '''Cancels when''' downstream cancels
 */
class BoundedOrdering[A, B](maxBounded: Int, initialId: B, nextId: B => B, getId: A => B)
                           (implicit val ordering: Ordering[B])
  extends GraphStage[FlowShape[A, A]] {

  require(maxBounded > 0)

  val in: Inlet[A] = Inlet("Filter.in")
  val out: Outlet[A] = Outlet("Filter.out")
  val shape: FlowShape[A, A] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {

      var expectedId: B = initialId

      implicit val elementPriority: Ordering[A] = Ordering.by(getId).reverse
      val pq: mutable.PriorityQueue[A] = mutable.PriorityQueue.empty

      @tailrec
      def elemsToPush(q: Queue[A], nextExpectedId: B): (Queue[A], B) =
        pq.headOption match {
          case Some(e) if nextExpectedId == getId(e) =>
            pq.dequeue()
            elemsToPush(q :+ e, nextId(nextExpectedId))
          case _ => (q, nextExpectedId)
        }

      def outOfBand(element: A): Boolean = ordering.lt(getId(element), expectedId)

      setHandler(in, new InHandler {
        override def onPush(): Unit = {

          val elem = grab[A](in)

          // 1. Element id matches expected id, send it down and unwind pq as long as element ids match expected.
          // 2. Element is out-of-band and missed buffer, should have arrived long ago, just send it down.
          // 3. Buffer full, need to flush elements downstream. Potentially causes out-of-band later.
          // 4. Element id does not match expected id, queue up in priority queue.

          if(expectedId == getId(elem)) {
            val (elems, nextExpectedId) = elemsToPush(Queue(elem), nextId(expectedId))
            expectedId = nextExpectedId
            emitMultiple(out, elems)
          } else if (outOfBand(elem)) {
            emit(out, elem)
          } else if (pq.size >= maxBounded) {
            pq += elem
            val (elems, nextExpectedId) = elemsToPush(Queue.empty, getId(pq.head))
            expectedId = nextExpectedId
            emitMultiple(out, elems)
          } else {
            pq += elem
            tryPull(in)
          }
        }
      })

      setHandler(out, new OutHandler {
        override def onPull(): Unit = tryPull(in)
      })
    }
}
