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

import akka.actor.{Props, ActorSystem}
import akka.stream._
import akka.stream.scaladsl.Flow
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

/**
  * Fan-out the stream to several streams emitting each incoming upstream element to all downstream consumers.
  *
  * '''Emits when''' one of the inputs has an element available
  *
  * '''Does not back-pressure''' upstream when downstream back-pressures, instead buffers the stream element to
  * memory mapped queue
  *
  * '''Completes when''' upstream completes and all downstream finish consuming stream elements
  *
  * '''Cancels when''' downstream cancels
  *
  */
class BroadcastBuffer[T] private(queue: PersistentQueue[T], onPushCallback: () => Unit = () => {})
                                (implicit serializer: QueueSerializer[T], system: ActorSystem)
  extends BroadcastBufferBase[T, T](queue, onPushCallback)(serializer, system) {

  def this(config: Config)(implicit serializer: QueueSerializer[T],
                           system: ActorSystem) = this(new PersistentQueue[T](config))

  def this(persistDir: File)(implicit serializer: QueueSerializer[T],
                             system: ActorSystem) = this(new PersistentQueue[T](persistDir), () => {})

  def withOnPushCallback(onPushCallback: () => Unit) = new BroadcastBuffer[T](queue, onPushCallback)

  def withOnCommitCallback(onCommitCallback: Int => Unit) =
    new BroadcastBuffer[T](queue.withOnCommitCallback(onCommitCallback), onPushCallback)


  override protected def autoCommit(outputPortId: Int, index: Long) = queue.commit(outputPortId, index)

  override protected def elementOut(e: Event[T]): T = e.entry
}

object BroadcastBuffer {
  def apply[T](config: Config)(implicit serializer: QueueSerializer[T], system: ActorSystem) =
    new BroadcastBuffer[T](config)

  def apply[T](persistDir: File)(implicit serializer: QueueSerializer[T], system: ActorSystem) =
    new BroadcastBuffer[T](persistDir)
}

/**
  * Fan-out the stream to several streams emitting each incoming upstream element to all downstream consumers.
  *
  * '''Emits when''' one of the inputs has an element available
  *
  * '''Does not back-pressure''' upstream when downstream back-pressures, instead buffers the stream element to
  * memory mapped queue
  *
  * '''Completes when''' upstream completes and all downstream finish consuming stream elements
  *
  * '''Cancels when''' downstream cancels
  *
  * A commit guarantee can be ensured to avoid data lost while consuming stream elements by adding a commit stage
  * after downstream consumer.
  *
  */
class BroadcastBufferAtLeastOnce[T] private(queue: PersistentQueue[T],
                                 onPushCallback: () => Unit = () => {})(implicit serializer: QueueSerializer[T],
                                                                        system: ActorSystem)
  extends BroadcastBufferBase[T, Event[T]](queue, onPushCallback)(serializer, system) {

  def this(config: Config)(implicit serializer: QueueSerializer[T],
                           system: ActorSystem) = this(new PersistentQueue[T](config))

  def this(persistDir: File)(implicit serializer: QueueSerializer[T],
                             system: ActorSystem) = this(new PersistentQueue[T](persistDir), () => {})

  def withOnPushCallback(onPushCallback: () => Unit) = new BroadcastBufferAtLeastOnce[T](queue, onPushCallback)

  def withOnCommitCallback(onCommitCallback: Int => Unit) =
    new BroadcastBufferAtLeastOnce[T](queue.withOnCommitCallback(onCommitCallback), onPushCallback)

  override protected def elementOut(e: Event[T]): Event[T] = e

  def commit[U] = Flow[Event[U]].map { element =>
    if (!upstreamFailed) {
      queue.commit(element.outputPortId, element.index)
      if(upstreamFinished) queueCloserActor ! Committed(element.outputPortId, element.index)
    }
    element
  }
}

object BroadcastBufferAtLeastOnce {
  def apply[T](config: Config)(implicit serializer: QueueSerializer[T], system: ActorSystem) =
    new BroadcastBufferAtLeastOnce[T](config)

  def apply[T](persistDir: File)(implicit serializer: QueueSerializer[T], system: ActorSystem) =
    new BroadcastBufferAtLeastOnce[T](persistDir)
}
