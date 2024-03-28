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
package org.squbs.stream

import org.apache.pekko.Done
import org.apache.pekko.actor._
import org.apache.pekko.stream._
import org.apache.pekko.util.Timeout
import org.squbs.lifecycle.{GracefulStop, GracefulStopHelper}
import org.squbs.unicomplex._

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Traits for perpetual streams that start and stop with the server. The simplest conforming implementation
  * follows these requirements:<ol>
  *   <li>The stream materializes to a Future or a Product (Tuple, List, etc.)
  *       for which the last element is a Future</li>
  *   <li>This Future represents the state whether the stream is done</li>
  *   <li>The stream has the killSwitch as the first processing stage right behind the source</li>
  * </ol>Non-conforming implementations need to implement one or more of the provided hooks while
  * conforming implementations can well be supported by the default implementations.
  * @tparam T The type of the materialized value of the stream.
  */
trait PerpetualStreamBase[T] extends Actor with ActorLogging with Stash with GracefulStopHelper {

  private[stream] var matValueOption: Option[T] = None

  final def matValue: T = matValueOption match {
    case Some(v) => v
    case None => throw new IllegalStateException("Materialized value not available before streamGraph is started!")
  }

  /**
    * The kill switch to integrate into the stream. Override this if you want a different switch
    * or one that is shared between perpetual streams.
    *
    * @return The kill switch.
    */
  lazy val killSwitch: SharedKillSwitch = KillSwitches.shared(getClass.getName)

  /**
    * By default the stream is run when system state is Active.  Override this if you want it to run in a different
    * lifecycle phase.
    */
  lazy val streamRunLifecycleState: LifecycleState = Active

  context.become(starting)

  Unicomplex() ! ObtainLifecycleEvents(streamRunLifecycleState, Stopping)
  Unicomplex() ! SystemState

  private[stream] def runGraph(): T

  private[stream] def shutdownAndNotify(): Unit

  final def starting: Receive = {
    case `streamRunLifecycleState` =>
      context.become(running orElse flowMatValue orElse receive orElse catchAnyLifecycleState)
      matValueOption = Option(runGraph())
      unstashAll()
    case _ => stash()
  }

  final def running: Receive = {
    case Stopping | GracefulStop =>

      val children = context.children
      children foreach context.watch
      shutdownAndNotify()
      context.become(stopped(children))
  }

  final def stopped(children: Iterable[ActorRef]): Receive = {
    case Done => // Just accept the fact. Do nothing.

    case Terminated(ref) =>
      val remaining = children filterNot ( _ == ref )
      if (remaining.nonEmpty) context become stopped(remaining) else context stop self
  }

  final def flowMatValue: Receive = {
    case MatValueRequest => sender() ! matValue
  }

  final def catchAnyLifecycleState: Receive = {
    case _: LifecycleState => // Stashed lifecycle states without meaning or anyone caring should be ignored.
  }
}

case object MatValueRequest {
  def instance: MatValueRequest.type = this
}

object SafeSelect {
  def apply(path: String)(implicit refFactory: ActorRefFactory, timeout: Timeout): ActorRef =
    refFactory.actorOf(Props(classOf[SafeSelect], path, timeout.duration.toNanos + System.nanoTime))
}

class SafeSelect(wellKnownActorPath: String, endTime: Long) extends Actor with ActorLogging {

  case class Request(msg: Any, from: ActorRef)

  import context.dispatcher
  private val selection = context.actorSelection(wellKnownActorPath)

  override def receive: Receive = {
    case ActorIdentity(request: Request, None) =>
      if (System.nanoTime < endTime) {
        context.system.scheduler.scheduleOnce(50 millis, self, request)
      } else {
        val err = ActorNotFound(selection)
        request.from ! Status.Failure(err)
        log.error(err, "Actor at {} not started after timeout!", wellKnownActorPath)
        context.stop(self)
      }
    case ActorIdentity(Request(msg, from), Some(actorRef)) =>
      actorRef.tell(msg, from)
      context.stop(self)
    case request: Request => selection ! Identify(request)
    case msg => selection ! Identify(Request(msg, sender()))
  }
}