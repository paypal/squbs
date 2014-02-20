package org.squbs.unicomplex

import akka.actor._
import akka.pattern.pipe
import akka.actor.SupervisorStrategy._
import concurrent.duration._
import com.typesafe.config.ConfigFactory
import akka.actor.OneForOneStrategy
import org.squbs.lifecycle.GracefulStop
import scala.collection.mutable
import akka.pattern.GracefulStopSupport
import scala.concurrent.Future

object Unicomplex {
  
  // TODO: Make ActorSystem name configurable.
  implicit val actorSystem = ActorSystem("squbs")
  
  implicit val uniActor = actorSystem.actorOf(Props[Unicomplex], "unicomplex")

  private[squbs] val reaperActor = actorSystem.actorOf(Props[Reaper], "reaper")

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
class Unicomplex extends Actor with ActorLogging {
  
  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case e: Exception => 
        log.warning(s"Received ${e.getClass.getName} with message ${e.getMessage} from ${sender.path}")
        Restart
    }
    
  def receive = {
    case StartWebService =>
      ServiceRegistry.startWebService      
      sender ! Ack
    case msg: Any =>
      log.info("Received: {}", msg.toString)
      
  }  
}

private[squbs] case class StopRegistry(timeout: FiniteDuration)

private[unicomplex] class Reaper extends Actor with ActorLogging with GracefulStopSupport {
  import scala.concurrent.ExecutionContext.Implicits.global

  private val registeredActors = mutable.HashMap[ActorRef, FiniteDuration]()

  private def stopRegisteredActors(msg: Any) = {
    Future.sequence(registeredActors.map({case (target, timeout) =>
      gracefulStop(target, timeout, msg)
    }))
  }

  private def shutdownState: Receive = {
    case Terminated(target) => log.debug(s"$target is terminated")
      registeredActors.remove(target)
      if (registeredActors.isEmpty) {
        log.info("All registered were terminated. Shutting down the system")
        Unicomplex.actorSystem.shutdown()
      }

    case Status.Failure(f) => registeredActors.foreach(_._1 ! PoisonPill)

    case _ => //don't care
  }

  def receive = {
    case GracefulStop => log.debug("reaper got graceful shutdown")
      stopRegisteredActors(GracefulStop) pipeTo self
      context.become(shutdownState)

    case StopRegistry(timeout) => log.debug(s"registered $sender")
      registeredActors.put(sender, timeout)
      context.watch(sender)

    case msg =>
      log.info("received {}", msg.toString)
  }
}

class CubeSupervisor extends Actor with ActorLogging {

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case e: Exception => 
        log.warning(s"Received ${e.getClass.getName} with message ${e.getMessage} from ${sender.path}")
        Restart
    }

  def receive = {

    case StartCubeActor(props, name) => 
      val cubeActor = context.actorOf(props, name)
      log.info("Started actor {}", cubeActor.path)

    case msg: Any =>
      log.info("Received: {}", msg.toString)
  }
  
}