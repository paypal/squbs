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
package org.squbs.stream

import akka.Done
import akka.actor.{Actor, ActorLogging, ActorRef, Stash, Terminated}
import akka.stream.Supervision._
import akka.stream.scaladsl.RunnableGraph
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, KillSwitches, Supervision}
import org.squbs.lifecycle.{GracefulStop, GracefulStopHelper}
import org.squbs.unicomplex._

import scala.concurrent.Future

trait PerpetualStream[T] extends Actor with ActorLogging with Stash with GracefulStopHelper {

  def streamGraph: RunnableGraph[T]

  private[this] var matValueOption: Option[T] = None

  final def matValue = matValueOption match {
    case Some(v) => v
    case None => throw new IllegalStateException("Materialized value not available before streamGraph is started!")
  }

  /**
    * The decider to use. Override if not resumingDecider.
    */
  def decider: Supervision.Decider = { t =>
    log.error("Uncaught error {} from stream", t)
    t.printStackTrace()
    Resume
  }

  /**
    * The kill switch to integrate into the stream. Override this if you want a different switch
    * or one that is shared between perpetual streams.
    * @return The kill switch.
    */
  lazy val killSwitch = KillSwitches.shared(getClass.getName)


  implicit val materializer =
    ActorMaterializer(ActorMaterializerSettings(context.system).withSupervisionStrategy(decider))

  Unicomplex() ! SystemState
  Unicomplex() ! ObtainLifecycleEvents(Active, Stopping)

  context.become(starting)

  final def starting: Receive = {
    case Active =>
      context.become(running orElse receive)
      matValueOption = Option(streamGraph.run())
      unstashAll()
    case _ => stash()
  }

  final def running: Receive = {
    case Stopping | GracefulStop =>
      import context.dispatcher
      val children = context.children
      children foreach context.watch
      shutdownHook() onComplete { _ => self ! Done }
      context.become(stopped(children))
  }

  final def stopped(children: Iterable[ActorRef]): Receive = {
    case Done => materializer.shutdown()
    case Terminated(ref) =>
      val remaining = children filterNot ( _ == ref )
      if (remaining.nonEmpty) context.become(stopped(remaining))
      else context.stop(self)
  }

  def receive: Receive = PartialFunction.empty

  /**
    * Override the shutdown hook to define your own shutdown process or wait for the sink to finish.
    * @return A Future[Done] that gets completed when the whole stream is done.
    */
  def shutdownHook(): Future[Done] = {
    killSwitch.shutdown()
    Future.successful(Done)
  }
}