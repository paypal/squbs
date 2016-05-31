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

import akka.actor.{Actor, ActorLogging}
import akka.stream.Supervision._
import akka.stream.scaladsl.RunnableGraph
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import org.squbs.unicomplex._

trait PerpetualStream[T] extends Actor with ActorLogging {

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

  implicit val materializer =
    ActorMaterializer(ActorMaterializerSettings(context.system).withSupervisionStrategy(decider))

  Unicomplex() ! SystemState
  Unicomplex() ! ObtainLifecycleEvents(Active, Stopping)

  context.become(starting)

  final def starting: Receive = {
    case Active =>
      context.become(running orElse receive)
      matValueOption = Option(streamGraph.run())
  }

  final def running: Receive = {
    case Stopping =>
      materializer.shutdown()
      shutdownHook()
  }

  def receive: Receive = PartialFunction.empty

  def shutdownHook(): Unit = {}
}