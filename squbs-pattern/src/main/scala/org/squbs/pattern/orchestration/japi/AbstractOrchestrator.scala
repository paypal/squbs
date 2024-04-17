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
package org.squbs.pattern.orchestration.japi

import java.util.concurrent.CompletableFuture

import org.apache.pekko.actor.{Actor, ActorRef, ActorSelection}
import org.apache.pekko.pattern.{AskableActorRef, AskableActorSelection}
import org.apache.pekko.util.Timeout
import org.squbs.pattern.orchestration.Orchestrator

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

@deprecated("The Orchestration module is deprecated. Please use Pekko streams for safer orchestration instead.",
since = "0.15.0")
abstract class AbstractOrchestrator extends Actor with Orchestrator {

  override def receive = super.receive

  def ask(actor: ActorRef, message: Any, timeout: Timeout): Ask = {
    val future = new AskableActorRef(actor).ask(message)(timeout)
    new Ask(future)
  }

  def ask(actor: ActorSelection, message: Any, timeout: Timeout): Ask = {
    val future = new AskableActorSelection(actor).ask(message)(timeout)
    new Ask(future)
  }

  class Ask private[japi](private val future: Future[Any]) {

    def thenComplete[T](cFuture: CompletableFuture[T]): Unit = {
      // Dragons here: DO NOT call nextMessageId from inside future.onComplete as that executes
      // outside the context of the actor. Instead, obtain the (val) id eagerly inside the actor and
      // give it to the function so it becomes pre-assigned.
      val nextId = nextMessageId
      import context.dispatcher
      future onComplete { self ! UniqueTryWrapper(nextId, _) }
      expectOnce {
        case UniqueTryWrapper(`nextId`, tt: Try[_]) =>
          tt match {
            case Success(t) => cFuture.complete(t.asInstanceOf[T])
            case Failure(e) => cFuture.completeExceptionally(e)
          }
      }
    }
  }
}
