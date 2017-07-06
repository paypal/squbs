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

import akka.Done
import akka.actor.{Actor, ActorContext, ActorIdentity, ActorLogging, ActorRef, Identify, Props, Stash, Terminated}
import akka.stream.Supervision._
import akka.stream.scaladsl.RunnableGraph
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, KillSwitches, Supervision}
import akka.util.Timeout
import org.squbs.lifecycle.{GracefulStop, GracefulStopHelper}
import org.squbs.unicomplex._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.reflect.ClassTag

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

  /**
    * By default the stream is run when system state is Active.  Override this if you want it to run in a different
    * lifecycle phase.
    */
  lazy val streamRunLifecycleState: LifecycleState = Active

  implicit val materializer =
    ActorMaterializer(ActorMaterializerSettings(context.system).withSupervisionStrategy(decider))

  Unicomplex() ! SystemState
  Unicomplex() ! ObtainLifecycleEvents(streamRunLifecycleState, Stopping)

  context.become(starting)

  final def starting: Receive = {
    case `streamRunLifecycleState` =>
      context.become(running orElse flowMatValue orElse receive)
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

  final def flowMatValue: Receive = {
    case MatValueRequest => sender() ! matValue
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

case object MatValueRequest

trait PerpetualStreamMatValue[T] {
  protected val context: ActorContext

  def matValue(perpetualStreamName: String)(implicit classTag: ClassTag[T]): T = {
    implicit val timeout = Timeout(10 seconds)
    import akka.pattern.ask

    val responseF =
      (context.actorOf(Props(classOf[MatValueRetrieverActor[T]], perpetualStreamName)) ? MatValueRequest).mapTo[T]

    // Exception! This code is executed only at startup. We really need a better API, though.
    Await.result(responseF, timeout.duration)
  }
}

class MatValueRetrieverActor[T](wellKnownActorName: String) extends Actor {
  val identifyId = 1
  var flowActor: ActorRef = _

  case object RetryMatValueRequest

  override def receive: Receive = {
    case MatValueRequest =>
      self ! RetryMatValueRequest
      flowActor = sender()
    case RetryMatValueRequest =>
      context.actorSelection(wellKnownActorName) ! Identify(identifyId)
    case ActorIdentity(`identifyId`, Some(ref)) =>
      ref ! MatValueRequest
    case ActorIdentity(`identifyId`, None) =>
      import context.dispatcher
      context.system.scheduler.scheduleOnce(1 second, self, RetryMatValueRequest)
    case matValue: T =>
      flowActor ! matValue
      context.stop(self)
  }
}