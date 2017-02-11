/*
 * Copyright 2017 PayPal
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

package org.squbs.streams

import akka.stream.ActorAttributes.SupervisionStrategy
import akka.stream.{Supervision, Attributes}
import akka.stream.impl.fusing.GraphStages.SimpleLinearGraphStage
import akka.stream.stage.{InHandler, OutHandler, GraphStageLogic}

import scala.util.control.NonFatal

object Deduplicate {

  def apply[T, U](key: T => U, duplicateCount: Long) =
    new Deduplicate[T, U](key, duplicateCount, new java.util.HashMap[U, MutableLong]())

  def apply[T, U](key: T => U, duplicateCount: Long, registry: java.util.Map[U, MutableLong]) =
    new Deduplicate[T, U](key, duplicateCount, registry)

  def apply[T](duplicateCount: Long = Long.MaxValue,
               registry: java.util.Map[T, MutableLong] = new java.util.HashMap[T, MutableLong]()): Deduplicate[T, T] =
    Deduplicate(t => t, duplicateCount, registry)
}

/**
  * Only pass on those elements that have not been seen so far.
  *
  * '''Emits when''' the element is not a duplicate
  *
  * '''Backpressures when''' the element is not a duplicate and downstream backpressures
  *
  * '''Completes when''' upstream completes
  *
  * '''Cancels when''' downstream cancels
  */
final class Deduplicate[T, U](key: T => U, duplicateCount: Long = Long.MaxValue,
                              registry: java.util.Map[U, MutableLong] = new java.util.HashMap[U, MutableLong]())
  extends SimpleLinearGraphStage[T] {

  require(duplicateCount >= 2)

  override def toString: String = "Deduplicate"

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with OutHandler with InHandler {
      def decider = inheritedAttributes.get[SupervisionStrategy].map(_.decider).getOrElse(Supervision.stoppingDecider)

      import scala.compat.java8.FunctionConverters._
      val incrementOrRemove: (MutableLong, MutableLong) => MutableLong = (old, default) => {
        pull(in)
        if(old.increment() == duplicateCount) null else old
      }

      override def onPush(): Unit = {
        try {
          val elem = grab(in)
          val counter = registry.merge(key(elem), MutableLong(1), incrementOrRemove.asJava)
          if(counter != null && counter.value == 1) {
            push(out, elem)
          }
        } catch {
          case NonFatal(ex) ⇒ decider(ex) match {
            case Supervision.Stop ⇒ failStage(ex)
            case _                ⇒ pull(in)
          }
        }
      }

      override def onPull(): Unit = pull(in)

      setHandlers(in, out, this)
    }
}

/**
  * [[MutableLong]] is used to avoid boxing/unboxing and also
  * to avoid [[java.util.Map#put]] operation to increment the counters in the registry.
  *
  * @param value
  */
case class MutableLong(var value: Long = 0L) {
  def increment() = {
    value += 1
    value
  }
}
