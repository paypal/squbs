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
                                 onPushCallback: () => Unit = () => {})(implicit serializer: QueueSerializer[T],
                                                                        system: ActorSystem)
  extends GraphStage[UniformFanOutShape[T, Event[T]]] {

  def this(config: Config)(implicit serializer: QueueSerializer[T],
                           system: ActorSystem) = this(new PersistentQueue[T](config))

  def this(persistDir: File)(implicit serializer: QueueSerializer[T],
                             system: ActorSystem) = this(new PersistentQueue[T](persistDir), () => {})

  def withOnPushCallback(onPushCallback: () => Unit) = new BroadcastBuffer[T](queue, onPushCallback)

  def withOnCommitCallback(onCommitCallback: Int => Unit) = new BroadcastBuffer[T](queue.withOnCommitCallback(onCommitCallback), onPushCallback)

  private[stream] val outputPorts = queue.totalOutputPorts
  private[stream] val in = Inlet[T]("BroadcastBuffer.in")
  private[stream] val out = Vector.tabulate(outputPorts)(i ⇒ Outlet[Event[T]]("BroadcastBuffer.out" + i))
  val shape: UniformFanOutShape[T, Event[T]] = UniformFanOutShape(in, out: _*)
  private var finished = IndexedSeq.fill[Boolean](outputPorts)(false)
  @volatile private var upstreamFailed = false
  private val queueCloserActor = system.actorOf(Props(classOf[PersistentQueueCloserActor[T]], queue))

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    private var upstreamFinished = false

    override def preStart(): Unit = pull(in)

    def outHandler(outlet: Outlet[Event[T]], outputPortId: Int) = new OutHandler {
      override def onPull(): Unit = {
        queue.dequeue(outputPortId) match {
          case None => if (upstreamFinished) {
              finished = finished.updated(outputPortId, true)
              if(finished.reduce(_ && _)) {
                queueCloserActor ! ReachedEndOfQueue
                completeStage()
              }
            }
          case Some(element) =>
            push(outlet, Event(outputPortId, element.index, element.entry))
            queueCloserActor ! Pushed(outputPortId, element.index)
            if(queue.autoCommit) {
              queue.commit(outputPortId, element.index)
              queueCloserActor ! Committed(outputPortId, element.index)
            }
        }
      }
    }

    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        val element = grab(in)
        queue.enqueue(element)
        onPushCallback()
        out.iterator.zipWithIndex foreach { case (port, id) =>
          if (isAvailable(port))
            queue.dequeue(id) foreach { element =>
              push(out(id), Event(id, element.index, element.entry))
              queueCloserActor ! Pushed(id, element.index)
              if(queue.autoCommit) {
                queue.commit(id, element.index)
                queueCloserActor ! Committed(id, element.index)
              }
            }
        }
        pull(in)
      }

      override def onUpstreamFinish(): Unit = {
        upstreamFinished = true
        if(out.forall(isAvailable(_))) completeStage()
        else {
          out.zipWithIndex.filter{ case (port, _) => isAvailable(port) }.
                           foreach{ case (_, index) => finished.updated(index, true) }
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

    out.zipWithIndex foreach { case (currentOut, outputPortId) =>
      setHandler(currentOut, outHandler(currentOut, outputPortId))
    }
  }

  val commit = Flow[Event[T]].map { element =>
    if(!queue.autoCommit && !upstreamFailed) {
      queue.commit(element.outputPortId, element.commitOffset)
      queueCloserActor ! Committed(element.outputPortId, element.commitOffset)
    }
    element
  }

  def clearStorage() = queue.clearStorage()
}

