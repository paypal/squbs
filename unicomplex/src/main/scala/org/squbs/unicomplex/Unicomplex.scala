package org.squbs.unicomplex

import akka.actor._
import akka.actor.SupervisorStrategy._
import concurrent.duration._
import com.typesafe.config.ConfigFactory
import akka.actor.OneForOneStrategy
import org.squbs.lifecycle.{GracefulStopHelper, GracefulStop}
import scala.collection.mutable
import akka.pattern.GracefulStopSupport
import org.squbs.util.threadless.Future
import scala.util.{Failure, Success}

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

  private val registeredActors = mutable.HashMap[ActorRef, FiniteDuration]()

  private def gracefulShutdown: Unit = {

    def stopRegisteredActors(msg: Any) = {
      Future.sequence(registeredActors.map({case (target, timeout) =>
        gracefulStop(target, timeout, msg).asInstanceOf[Future[Boolean]]
      }))
    }

    stopRegisteredActors(GracefulStop).onComplete({
      case Success(_) => Unicomplex.actorSystem.shutdown()

      case Failure(e) => log.warning(s"[squbs][reaper] graceful shutdown failed with $e")
        stopRegisteredActors(PoisonPill).onComplete(_ => Unicomplex.actorSystem.shutdown())
    })
  }

  def receive = {
    case GracefulStop => gracefulShutdown

    case StopRegistry(timeout) =>
      registeredActors.put(sender, timeout)
      context.watch(sender)

    case Terminated(target) => registeredActors.remove(target)

    case msg =>
      log.info("[squbs][Reaper] received {}", msg.toString)
  }
}

class CubeSupervisor extends Actor with ActorLogging with GracefulStopHelper{

  override def stopTimeout = 8 second

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case e: Exception => 
        log.warning(s"Received ${e.getClass.getName} with message ${e.getMessage} from ${sender.path}")
        Restart
    }

  def receive = {
    case GracefulStop => defaultStopStrategy
    case StartCubeActor(props, name) => 
      val cubeActor = context.actorOf(props, name)
      log.info("Started actor {}", cubeActor.path)
    case msg: Any =>
      log.info("Received: {}", msg.toString)
  }
  
}