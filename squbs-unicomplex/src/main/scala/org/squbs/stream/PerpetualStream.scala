
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
      shutdownHook()
      materializer.shutdown()
  }

  def receive: Receive = PartialFunction.empty

  def shutdownHook(): Unit = {}
}