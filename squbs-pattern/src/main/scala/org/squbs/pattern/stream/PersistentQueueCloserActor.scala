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

package org.squbs.pattern.stream

import akka.actor.{ActorLogging, Actor}

class PersistentQueueCloserActor[T](queue: PersistentQueue[T]) extends Actor with ActorLogging {

  val pushIndex = Array.ofDim[Long](queue.totalOutputPorts)
  val commitIndex = Array.ofDim[Long](queue.totalOutputPorts)

  override def receive: Receive = {
    case Pushed(outportId, index) => pushIndex(outportId) = index
    case Committed(outportId, index) => commitIndex(outportId) = index
    case UpstreamFailed =>
      queue.close()
      context.stop(self)
    case ReachedEndOfQueue =>
      if(pushIndex.sameElements(commitIndex)) {
        queue.close()
        context.stop(self)
      } else {
        context.become({
          case Committed(outportId, index) =>
            commitIndex(outportId) = index
            if (pushIndex.sameElements(commitIndex)) {
              queue.close()
              context.stop(self)
            }
        })
      }
  }
}

case class Pushed(outportId: Int, index: Long)
case class Committed(outportId: Int, index: Long)
case object UpstreamFailed
case object ReachedEndOfQueue
