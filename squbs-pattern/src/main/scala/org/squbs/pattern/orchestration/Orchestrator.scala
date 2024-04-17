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

package org.squbs.pattern.orchestration

import org.apache.pekko.actor.Actor

import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.Try

@deprecated("The Orchestration module is deprecated. Please use Pekko streams for safer orchestration instead.",
  since = "0.15.0")
trait Orchestrator extends Aggregator { this: Actor =>

  protected case class UniqueTryWrapper[T](id: Long, result: Try[T])

  private[this] var _messageId = 0L
  protected def nextMessageId: Long = { _messageId += 1L; _messageId }

  implicit def toOFuture[T](future: Future[T]): OFuture[T] = {
    // Dragons here: DO NOT call nextMessageId from inside future.onComplete as that executes
    // outside the context of the actor. Instead, obtain the (val) id eagerly inside the actor and
    // give it to the function so it becomes pre-assigned.
    val nextId = nextMessageId

    val oPromise = OPromise[T]()
    import context.dispatcher
    future onComplete { self ! UniqueTryWrapper(nextId, _) }
    expectOnce {
      case UniqueTryWrapper(`nextId`, t: Try[_]) => oPromise complete t.asInstanceOf[Try[T]]
    }
    oPromise.future
  }

  implicit def toFuture[T](future: OFuture[T]): Future[T] = future.toFuture
}

