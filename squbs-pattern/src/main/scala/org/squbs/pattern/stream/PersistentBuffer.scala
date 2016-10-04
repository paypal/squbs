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

import akka.actor.{Props, ActorSystem}
import akka.stream.scaladsl.Flow
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

/**
  * Persists all incoming upstream element to a memory mapped queue before publishing it to downstream consumer.
  *
  * '''Emits when''' one of the inputs has an element available
  *
  * '''Does not Backpressure''' upstream when downstream backpressures, instead buffers the stream element to memory mapped queue
  *
  * '''Completes when''' upstream completes
  *
  * '''Cancels when''' downstream cancels
  *
  * In addition to this, a commit guarantee can be ensured to avoid data lost while consuming stream elements,
  * to enable this, set the `auto-commit` to `false` and add a commit stage after downstream consumer.
  *
  */
class PersistentBuffer[T] private(private[stream] val queue: PersistentQueue[T],
                                  onPushCallback: () => Unit = () => {})
                                 (implicit serializer: QueueSerializer[T],
                                  system: ActorSystem) extends GraphStage[FlowShape[T, Event[T]]] {

  def this(config: Config)(implicit serializer: QueueSerializer[T], system: ActorSystem) = this(new PersistentQueue[T](config))

  def this(persistDir: File)(implicit serializer: QueueSerializer[T], system: ActorSystem) = this(new PersistentQueue[T](persistDir))

  def withOnPushCallback(onPushCallback: () => Unit) = new PersistentBuffer[T](queue, onPushCallback)

  def withOnCommitCallback(onCommitCallback: () => Unit) = new PersistentBuffer[T](queue.withOnCommitCallback(i => onCommitCallback()), onPushCallback)

  private[stream] val in = Inlet[T]("PersistentBuffer.in")
  private[stream] val out = Outlet[Event[T]]("PersistentBuffer.out")
  val shape: FlowShape[T, Event[T]] = FlowShape.of(in, out)
  val defaultOutputPort = 0
  @volatile private var upstreamFailed = false
  @volatile private var upstreamFinished = false
  private val queueCloserActor = system.actorOf(Props(classOf[PersistentQueueCloserActor[T]], queue))

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
            push(out, element)
            downstreamWaiting = false
            lastPushed = element.index
            if(queue.autoCommit) queue.commit(defaultOutputPort, element.index)
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
            push(out, element)
            lastPushed = element.index
            if(queue.autoCommit) queue.commit(defaultOutputPort, element.index)
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

  def commit[S] = if (queue.autoCommit) Flow[Event[S]]
              else {
                Flow[Event[S]].map { element =>
                  if (!upstreamFailed) {
                    queue.commit(element.outputPortId, element.index)
                    if(upstreamFinished) queueCloserActor ! Committed(element.outputPortId, element.index)
                  }
                  element
                }
              }

}