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

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

class PersistentBuffer[T] private(private[stream] val queue: PersistentQueue[T])
                                 (implicit serializer: QueueSerializer[T]) extends GraphStage[FlowShape[T, T]] {

  def this(config: Config)(implicit serializer: QueueSerializer[T]) = this(new PersistentQueue[T](config))

  def this(persistDir: File)(implicit serializer: QueueSerializer[T]) = this(new PersistentQueue[T](persistDir))

  private[stream] val in = Inlet[T]("PersistentBuffer.in")
  private[stream] val out = Outlet[T]("PersistentBuffer.out")
  val shape: FlowShape[T, T] = FlowShape.of(in, out)

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    var downstreamWaiting = false
    var upstreamFinished = false

    override def preStart(): Unit = {
      // Start upstream demand
      pull(in)
    }

    setHandler(in, new InHandler {

      override def onPush(): Unit = {
        val element = grab(in)
        queue.enqueue(element)
        if (downstreamWaiting) {
          queue.dequeue foreach { entry =>
            push(out, entry)
            downstreamWaiting = false
          }
        }
        pull(in)
      }

      override def onUpstreamFinish(): Unit = upstreamFinished = true

      override def onUpstreamFailure(ex: Throwable): Unit = {
        val logger = Logger(LoggerFactory.getLogger(this.getClass))
        logger.error("Received upstream failure signal: " + ex)
        queue.close()
      }
    })

    setHandler(out, new OutHandler {

      override def onPull(): Unit = {
        queue.dequeue match {
          case Some(entry) =>
            push(out, entry)
          case None =>
            if (upstreamFinished) {
              queue.close()
              completeStage()
            } else downstreamWaiting = true
        }
      }
    })
  }
}