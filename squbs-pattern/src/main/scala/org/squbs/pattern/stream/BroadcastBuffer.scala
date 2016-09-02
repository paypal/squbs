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
package org.squbs.pattern.stream

import java.io.File

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
  * '''Does not back-pressure''' upstream when downstream back-pressures, instead buffers the stream element to memory mapped queue
  *
  * '''Completes when''' upstream completes and all downstream finish consuming stream elements
  *
  * '''Cancels when''' downstream cancels
  *
  * In addition to this, a commit guarantee can be ensured to avoid data lost while consuming stream elements,
  * to enable this, set the `auto-commit` to `false` and add a commit stage after downstream consumer.
  *
  */
class BroadcastBuffer[T] private(private[stream] val queue: PersistentQueue[T],
                                 onPushCallback: () => Unit = (() => {}))(implicit serializer: QueueSerializer[T])
  extends GraphStage[UniformFanOutShape[T, Event[T]]] {

  def this(config: Config)(implicit serializer: QueueSerializer[T]) = this(new PersistentQueue[T](config))

  def this(persistDir: File)(implicit serializer: QueueSerializer[T]) = this(new PersistentQueue[T](persistDir), () => {})

  def withOnPushCallback(onPushCallback: () => Unit) = new BroadcastBuffer[T](queue, onPushCallback)

  def withOnCommitCallback(onCommitCallback: Int => Unit) = new BroadcastBuffer[T](queue.withOnCommitCallback(onCommitCallback), onPushCallback)

  private[stream] val outputPorts = queue.totalOutputPorts
  private[stream] val in = Inlet[T]("BroadcastBuffer.in")
  private[stream] val out = Vector.tabulate(outputPorts)(i ⇒ Outlet[Event[T]]("BroadcastBuffer.out" + i))
  val shape: UniformFanOutShape[T, Event[T]] = UniformFanOutShape(in, out: _*)

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    private var upstreamFinished = false
    private var downstreamFinished = false
    private val finished = Array.fill[Boolean](outputPorts)(false)

    override def preStart(): Unit = pull(in)

    def outHandler(outlet: Outlet[Event[T]], outputPortId: Int) = new OutHandler {
      override def onPull(): Unit = {
        queue.dequeue(outputPortId) match {
          case None => if (upstreamFinished) {
              finished(outputPortId) = true
              if (finished.reduce(_ && _)) {
                queue.close()
                completeStage()
              }
            }
          case Some(element) => push(outlet, Event(outputPortId, element.index, element.entry))
        }
      }

      override def onDownstreamFinish(): Unit = {
        val logger = Logger(LoggerFactory.getLogger(this.getClass))
        logger.error("Received down steam finish signal")
        finished(outputPortId) = true
        if (finished.reduce(_ && _)) {
          queue.close()
        }
        super.onDownstreamFinish()
      }
    }

    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        val element = grab(in)
        queue.enqueue(element)
        onPushCallback()
        out.iterator.zipWithIndex foreach { case (port, id) =>
          if (isAvailable(port))
            queue.dequeue(id) foreach { element => push(out(id), Event(id, element.index, element.entry)) }
        }
        pull(in)
      }

      override def onUpstreamFinish(): Unit = upstreamFinished = true

      override def onUpstreamFailure(ex: Throwable): Unit = {
        val logger = Logger(LoggerFactory.getLogger(this.getClass))
        logger.error("Received upstream failure signal: " + ex)
        queue.close()
        completeStage()
      }
    })

    out.zipWithIndex foreach { case (currentOut, outputPortId) =>
      setHandler(currentOut, outHandler(currentOut, outputPortId))
    }
  }

  val commit = Flow[Event[T]].map { element =>
    queue.commit(element.outputPortId, element.commitOffset)
    element
  }

  def clearStorage() = queue.clearStorage()
}

