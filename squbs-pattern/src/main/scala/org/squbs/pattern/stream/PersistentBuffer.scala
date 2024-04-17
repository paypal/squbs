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
package org.squbs.pattern.stream

import java.io.File

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Flow
import com.typesafe.config.Config

/**
  * Persists all incoming upstream element to a memory mapped queue before publishing it to downstream consumer.
  *
  * '''Emits when''' one of the inputs has an element available
  *
  * '''Does not Backpressure''' upstream when downstream backpressures, instead buffers the stream element to
  * memory mapped queue
  *
  * '''Completes when''' upstream completes
  *
  * '''Cancels when''' downstream cancels
  *
  */
class PersistentBuffer[T] private(queue: PersistentQueue[T], onPushCallback: () => Unit = () => {})
                                 (implicit serializer: QueueSerializer[T], system: ActorSystem)
  extends PersistentBufferBase[T, T](queue, onPushCallback)(serializer, system) {

  def this(config: Config)(implicit serializer: QueueSerializer[T], system: ActorSystem) =
    this(new PersistentQueue[T](config))

  def this(persistDir: File)(implicit serializer: QueueSerializer[T], system: ActorSystem) =
    this(new PersistentQueue[T](persistDir))

  def withOnPushCallback(onPushCallback: () => Unit) = new PersistentBuffer[T](queue, onPushCallback)

  def withOnCommitCallback(onCommitCallback: () => Unit) =
    new PersistentBuffer[T](queue.withOnCommitCallback(i => onCommitCallback()), onPushCallback)

  override protected def autoCommit(index: Long) = queue.commit(defaultOutputPort, index)

  override protected def elementOut(e: Event[T]): T = e.entry
}

object PersistentBuffer {
  def apply[T](config: Config)(implicit serializer: QueueSerializer[T], system: ActorSystem) =
    new PersistentBuffer[T](config)

  def apply[T](persistDir: File)(implicit serializer: QueueSerializer[T], system: ActorSystem) =
    new PersistentBuffer[T](persistDir)
}

/**
  * Persists all incoming upstream element to a memory mapped queue before publishing it to downstream consumer.
  *
  * '''Emits when''' one of the inputs has an element available
  *
  * '''Does not Backpressure''' upstream when downstream backpressures, instead buffers the stream element to
  * memory mapped queue
  *
  * '''Completes when''' upstream completes
  *
  * '''Cancels when''' downstream cancels
  *
  * A commit guarantee can be ensured to avoid data lost while consuming stream elements by adding a commit stage
  * after downstream consumer.
  *
  */
class PersistentBufferAtLeastOnce[T] private(queue: PersistentQueue[T], onPushCallback: () => Unit = () => {})
                                 (implicit serializer: QueueSerializer[T], system: ActorSystem)
  extends PersistentBufferBase[T, Event[T]](queue, onPushCallback)(serializer, system) {

  def this(config: Config)(implicit serializer: QueueSerializer[T], system: ActorSystem) =
    this(new PersistentQueue[T](config))

  def this(persistDir: File)(implicit serializer: QueueSerializer[T], system: ActorSystem) =
    this(new PersistentQueue[T](persistDir))

  def withOnPushCallback(onPushCallback: () => Unit) = new PersistentBufferAtLeastOnce[T](queue, onPushCallback)

  def withOnCommitCallback(onCommitCallback: () => Unit) =
    new PersistentBufferAtLeastOnce[T](queue.withOnCommitCallback(i => onCommitCallback()), onPushCallback)

  override protected def elementOut(e: Event[T]): Event[T] = e

  def commit[U] = Flow[Event[U]].map { element =>
    if (!upstreamFailed) {
      queue.commit(element.outputPortId, element.index)
      if (upstreamFinished) queueCloserActor ! Committed(element.outputPortId, element.index)
    }
    element
  }
}

object PersistentBufferAtLeastOnce {
  def apply[T](config: Config)(implicit serializer: QueueSerializer[T], system: ActorSystem) =
    new PersistentBufferAtLeastOnce[T](config)

  def apply[T](persistDir: File)(implicit serializer: QueueSerializer[T], system: ActorSystem) =
    new PersistentBufferAtLeastOnce[T](persistDir)
}
