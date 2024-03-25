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
package org.squbs.pattern.stream

import java.io.File

import org.apache.pekko.actor.{ActorSystem, Props}
import org.apache.pekko.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import org.apache.pekko.stream.{Attributes, FlowShape, Inlet, Outlet}
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

abstract class PersistentBufferBase[T, S] (private[stream] val queue: PersistentQueue[T],
                                      onPushCallback: () => Unit = () => {})
                                     (implicit serializer: QueueSerializer[T],
                                  system: ActorSystem) extends GraphStage[FlowShape[T, S]] {

  def this(config: Config)(implicit serializer: QueueSerializer[T], system: ActorSystem) =
    this(new PersistentQueue[T](config))

  def this(persistDir: File)(implicit serializer: QueueSerializer[T], system: ActorSystem) =
    this(new PersistentQueue[T](persistDir))

  private[stream] val in = Inlet[T]("PersistentBuffer.in")
  private[stream] val out = Outlet[S]("PersistentBuffer.out")
  val shape: FlowShape[T, S] = FlowShape.of(in, out)
  val defaultOutputPort = 0
  @volatile protected var upstreamFailed = false
  @volatile protected var upstreamFinished = false
  protected val queueCloserActor = system.actorOf(Props(classOf[PersistentQueueCloserActor[T]], queue))

  protected def elementOut(e: Event[T]): S

  protected def autoCommit(index: Long) = {}

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    private var lastPushed = 0L
    var downstreamWaiting = false

    override def preStart(): Unit = {
      // Start upstream demand
      pull(in)
    }

    setHandler(in, new InHandler {

      override def onPush(): Unit = {
        val element = grab(in)
        queue.enqueue(element)
        onPushCallback()
        if (downstreamWaiting) {
          queue.dequeue() foreach { element =>
            push(out, elementOut(element))
            downstreamWaiting = false
            lastPushed = element.index
            autoCommit(element.index)
          }
        }
        pull(in)
      }

      override def onUpstreamFinish(): Unit = {
        upstreamFinished = true

        if (downstreamWaiting) {
          queueCloserActor ! PushedAndCommitted(defaultOutputPort, lastPushed, queue.read(defaultOutputPort))
          queueCloserActor ! UpstreamFinished
          completeStage()
        }
      }

      override def onUpstreamFailure(ex: Throwable): Unit = {
        val logger = Logger(LoggerFactory.getLogger(this.getClass))
        logger.error("Received upstream failure signal: " + ex)
        upstreamFailed = true
        queueCloserActor ! UpstreamFailed
        completeStage()
      }
    })

    setHandler(out, new OutHandler {

      override def onPull(): Unit = {
        queue.dequeue() match {
          case Some(element) =>
            push(out, elementOut(element))
            lastPushed = element.index
            autoCommit(element.index)
          case None =>
            if (upstreamFinished) {
              queueCloserActor ! PushedAndCommitted(defaultOutputPort, lastPushed, queue.read(defaultOutputPort))
              queueCloserActor ! UpstreamFinished
              completeStage()
            } else downstreamWaiting = true
        }
      }
    })
  }
}
