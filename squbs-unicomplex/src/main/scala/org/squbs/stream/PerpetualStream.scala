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
trait PerpetualStream[T] extends Actor with ActorLogging with Stash with GracefulStopHelper {

  /**
    * Describe your graph by implementing streamGraph
 *
    * @return The graph.
    */
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
    *
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
      shutdown() onComplete { _ => self ! Done }
      context.become(stopped(children))
  }

  final def stopped(children: Iterable[ActorRef]): Receive = {
    case Done => materializer.shutdown()

    case Terminated(ref) =>
      val remaining = children filterNot ( _ == ref )
      if (remaining.nonEmpty) context become stopped(remaining) else context stop self
  }

  def receive: Receive = PartialFunction.empty

  /**
    * Override shutdown to define your own shutdown process or wait for the sink to finish.
    * The default shutdown makes the following assumptions:<ol>
    *   <li>The stream materializes to a Future or a Product (Tuple, List, etc.)
    *       for which the last element is a Future</li>
    *   <li>This Future represents the state whether the stream is done</li>
    *   <li>The stream has the killSwitch as the first processing stage</li>
    * </ol>In which case you do not need to override this default shutdown if there are no further shutdown
    * requirements. In case you override shutdown, it is recommended that super.shutdown() be called
    * on overrides even if the stream only partially meets the requirements above.
    *
    * @return A Future[Done] that gets completed when the whole stream is done.
    */
  def shutdown(): Future[Done] = {
    killSwitch.shutdown()
    import context.dispatcher
    matValue match {
      case f: Future[_] => f.map(_ => Done)
      case p: Product if p.productArity > 0 => p.productElement(p.productArity - 1) match {
          case f: Future[_] => f.map(_ => Done)
          case _ => Future.successful { Done }
        }
      case _ => Future.successful { Done }
    }
  }
}