package org.squbs.unicomplex

import akka.actor._
import akka.pattern.pipe
import akka.actor.SupervisorStrategy._
import concurrent.duration._
import com.typesafe.config.ConfigFactory
import akka.actor.OneForOneStrategy
import org.squbs.lifecycle.{GracefulStopHelper, GracefulStop}
import scala.collection.mutable
import akka.pattern.GracefulStopSupport
import scala.concurrent.Future

object Unicomplex {
  
  // TODO: Make ActorSystem name configurable.
  implicit val actorSystem = ActorSystem("squbs")
  
  implicit val uniActor = actorSystem.actorOf(Props[Unicomplex], "unicomplex")

  val externalConfigDir = "squbsconfig"

  val config = ConfigFactory.load.getConfig("squbs")

}

private[unicomplex] case object StartWebService
private[unicomplex] case class StartCubeActor(props: Props, name: String = "")

case object Ack

/**
 * The Unicomplex actor is the supervisor of the Unicomplex.
 * It starts actors that are part of the Unicomplex.
 */
class Unicomplex extends Actor with ActorLogging with GracefulStopSupport{

  import Unicomplex._

  implicit val executionContext = actorSystem.dispatcher

  private val cubeSupervisors = mutable.HashMap[ActorRef, FiniteDuration]()

  private def stopCubeSupervisors(msg: Any) = {
    Future.sequence(cubeSupervisors.map({case (target, timeout) =>
      gracefulStop(target, timeout, msg)
    }))
  }

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case e: Exception => 
        log.warning(s"Received ${e.getClass.getName} with message ${e.getMessage} from ${sender.path}")
        Restart
    }

  private def shutdownState: Receive = {
    case Terminated(target) => log.debug(s"$target is terminated")
      cubeSupervisors.remove(target)
      if (cubeSupervisors.isEmpty) {
        log.info("All CubeSupervisors were terminated. Shutting down the system")
        actorSystem.shutdown()
      }

    case Status.Failure(f) => cubeSupervisors.foreach(_._1 ! PoisonPill)

    case _ => //don't care
  }

  def receive = {
    case GracefulStop =>
      log.info(s"got GracefulStop from $sender")
      stopCubeSupervisors(GracefulStop) pipeTo self
      context.become(shutdownState)

    case StopRegistry(timeout) =>
      cubeSupervisors += (sender -> timeout)

    case StartWebService =>
      ServiceRegistry.startWebService      
      sender ! Ack

    case msg: Any =>
      log.info("Received: {}", msg.toString)
  }  
}

private[squbs] case class StopRegistry(timeout: FiniteDuration)

class CubeSupervisor extends Actor with ActorLogging with GracefulStopHelper {

  import Unicomplex._
  uniActor ! StopRegistry(stopTimeout)

  private var maxTimeout: FiniteDuration = stopTimeout

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case e: Exception => 
        log.warning(s"Received ${e.getClass.getName} with message ${e.getMessage} from ${sender.path}")
        Restart
    }

  def receive = {
    case GracefulStop => // The stop message should only come from the uniActor
      if (sender != Unicomplex.uniActor)
        log.error(s"got GracefulStop from $sender instead of ${Unicomplex.uniActor}")
      else
        defaultMidActorStop(context.children, maxTimeout)

    case StopRegistry(timeout) if (timeout > maxTimeout) => maxTimeout = timeout

    case StartCubeActor(props, name) => 
      val cubeActor = context.actorOf(props, name)
      log.info("Started actor {}", cubeActor.path)

    case msg: Any =>
      log.info("Received: {}", msg.toString)
  }
  
}

/**
 * get the cube supervisor for a given ActorRef
 * @return ActorSelection of the CubeSupervisor
 */
object Supervisor {

  def apply(targetPath: ActorPath)(implicit system: ActorSystem): Option[ActorSelection] = {
    val pathElements = targetPath.elements.toSeq

    if (pathElements.size <= 2) {
      None
    }else {
      val supervisorPath = targetPath.root / pathElements(0) / pathElements(1)
      Some(system.actorSelection(supervisorPath))
    }
  }

}