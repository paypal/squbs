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

import org.apache.pekko.actor.{Props, ActorSystem}
import org.apache.pekko.stream.{Attributes, Outlet, Inlet, UniformFanOutShape}
import org.apache.pekko.stream.stage.{InHandler, OutHandler, GraphStageLogic, GraphStage}
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

abstract class BroadcastBufferBase[T, S] (private[stream] val queue: PersistentQueue[T],
                                 onPushCallback: () => Unit = () => {})(implicit serializer: QueueSerializer[T],
                                                                        system: ActorSystem)
  extends GraphStage[UniformFanOutShape[T, S]] {

  def this(config: Config)(implicit serializer: QueueSerializer[T],
                           system: ActorSystem) = this(new PersistentQueue[T](config))

  def this(persistDir: File)(implicit serializer: QueueSerializer[T],
                             system: ActorSystem) = this(new PersistentQueue[T](persistDir), () => {})

  private val outputPorts = queue.totalOutputPorts
  private val in = Inlet[T]("BroadcastBuffer.in")
  private val out = Vector.tabulate(outputPorts)(i => Outlet[S]("BroadcastBuffer.out" + i))
  private val outWithIndex = out.zipWithIndex
  val shape: UniformFanOutShape[T, S] = UniformFanOutShape(in, out: _*)
  @volatile protected var upstreamFailed = false
  @volatile protected var upstreamFinished = false
  protected val queueCloserActor = system.actorOf(Props(classOf[PersistentQueueCloserActor[T]], queue))

  protected def elementOut(e: Event[T]): S

  protected def autoCommit(outputPortId: Int, index: Long) = {}

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    private val finished = Array.fill[Boolean](outputPorts)(false)
    private val lastPushed = Array.fill[Long](outputPorts)(0)

    override def preStart(): Unit = pull(in)

    def outHandler(outlet: Outlet[S], outputPortId: Int) = new OutHandler {
      override def onPull(): Unit = {
        queue.dequeue(outputPortId) match {
          case None => if (upstreamFinished) {
            finished(outputPortId) = true
            queueCloserActor ! PushedAndCommitted(outputPortId, lastPushed(outputPortId), queue.read(outputPortId))
            if (finished.reduce(_ && _)) {
              queueCloserActor ! UpstreamFinished
              completeStage()
            }
          }
          case Some(element) =>
            push(outlet, elementOut(element))
            lastPushed(outputPortId) = element.index
            autoCommit(outputPortId, element.index)
        }
      }
    }

    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        val element = grab(in)
        queue.enqueue(element)
        onPushCallback()
        outWithIndex foreach { case (port, id) =>
          if (isAvailable(port))
            queue.dequeue(id) foreach { element =>
              push(port, elementOut(element))
              lastPushed(id) = element.index
              autoCommit(id, element.index)
            }
        }
        pull(in)
      }

      override def onUpstreamFinish(): Unit = {
        upstreamFinished = true
        var isAllAvailable = true
        outWithIndex foreach { case (port, outportId) =>
          if (isAvailable(port)) {
            finished(outportId) = true
            queueCloserActor ! PushedAndCommitted(outportId, lastPushed(outportId), queue.read(outportId))
          } else {
            isAllAvailable = false
          }
        }

        if (isAllAvailable) {
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

    outWithIndex foreach { case (currentOut, outputPortId) =>
      setHandler(currentOut, outHandler(currentOut, outputPortId))
    }
  }
}

