package org.squbs.unicomplex

import akka.actor._
import akka.actor.SupervisorStrategy._
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory
import org.squbs.lifecycle.{GracefulStopHelper, GracefulStop}
import org.squbs.util.conversion.CubeUtil.CubeNameConversion
import scala.collection.mutable
import org.squbs.util.conversion.CubeName
import akka.actor.OneForOneStrategy
import akka.actor.Terminated
import java.util.concurrent.TimeUnit
import scala.util.{Failure, Success}
import akka.util.Timeout

object Unicomplex {
  
  // TODO: Make ActorSystem name configurable.
  implicit val actorSystem = ActorSystem("squbs")
  
  implicit val uniActor = actorSystem.actorOf(Props[Unicomplex], "unicomplex")

  val externalConfigDir = "squbsconfig"

  val config = ConfigFactory.load.getConfig("squbs")

}

private[unicomplex] case object StartWebService
private[unicomplex] case class StartCubeActor(props: Props, name: String = "")

case class StopCube(cubeName: String)
case class StartCube(cubeName: String)

case object Ack

case object ShutdownTimeout

/**
 * The Unicomplex actor is the supervisor of the Unicomplex.
 * It starts actors that are part of the Unicomplex.
 */
class Unicomplex extends Actor with ActorLogging {

  import Unicomplex._

  private val cubeSupervisors = mutable.Set.empty[ActorRef]

  implicit val executionContext = actorSystem.dispatcher

  implicit val timeout: Timeout = 10 millis

  val shutdownTimeout = FiniteDuration(config.getMilliseconds("shutdown-timeout"), TimeUnit.MILLISECONDS)

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case e: Exception => 
        log.warning(s"Received ${e.getClass.getName} with message ${e.getMessage} from ${sender.path}")
        Restart
    }

  private def stopCube(cubeName: String) = {
    // Stop the extensions of this cube if there are any
    Bootstrap.extensions.filter(_._1 == cubeName).map(_._3).foreach(extension => {/* stop the extension */})

    // Unregister the routes of this cube if there are any
    Bootstrap.services.filter(_._1 == cubeName).map(_._3).foreach(routeDef => {
      ServiceRegistry.registrar() ! Unregister(routeDef.webContext)
    })

    // Stop the CubeSupervisor if there is one
    CubeName(cubeName).cubeSupervisor() ! GracefulStop
  }

  private def startCube(cubeName: String) = {
    // TODO prevent starting an active Cube
    // PreInit extentions if there are any
    val extensionsInCube = Bootstrap.extensions.filter(_._1 == cubeName).map(_._3)
    extensionsInCube.foreach(_.preInit(Bootstrap.jarConfigs))
    // Init extentions
    extensionsInCube.foreach(_.init(Bootstrap.jarConfigs))

    // Start actors if there are any
    CubeName(cubeName).cubeSupervisor().resolveOne().onComplete({
      case Success(supervisor) => log.warning(s"actors in $cubeName are already activated")

      case Failure(_) =>
        Bootstrap.initInfoMap.getOrElse(Bootstrap.StartupType.ACTORS, Seq.empty[Bootstrap.InitInfo])
          .filter(_.symName == cubeName).foreach(Bootstrap.startActors)
    })

    // Start services if there are any
    Bootstrap.services.filter(_._1 == cubeName).map(_._3).foreach(routeDef => {
      ServiceRegistry.registrar() ! Register(routeDef)
    })

    // PostInit
    extensionsInCube.foreach(_.postInit(Bootstrap.jarConfigs))
  }

  private def shutdownState: Receive = {
    case Terminated(target) => log.debug(s"$target is terminated")
      cubeSupervisors.remove(target)
      if (cubeSupervisors.isEmpty) {
        log.info("All CubeSupervisors were terminated. Shutting down the system")
        actorSystem.shutdown()
      }

    case ShutdownTimeout => log.warning("Graceful shutdown timed out.")
      actorSystem.shutdown()
  }

  def receive = {
    case StopCube(cubeName) =>
      log.info(s"got StopCube($cubeName) from $sender")
      stopCube(cubeName)

    case StartCube(cubeName) =>
      log.info(s"got StartCube($cubeName) from $sender")
      startCube(cubeName)

    case Terminated(target) => log.info(s"$target is terminated")
      cubeSupervisors.remove(target)

    case GracefulStop =>
      log.info(s"got GracefulStop from $sender")
      cubeSupervisors.foreach(_ ! GracefulStop)
      context.become(shutdownState)
      actorSystem.scheduler.scheduleOnce(shutdownTimeout, self, ShutdownTimeout)

    case CubeSupervisorRegistry =>
      cubeSupervisors += sender
      context.watch(sender)

    case StartWebService =>
      ServiceRegistry.startWebService      
      sender ! Ack

    case msg: Any =>
      log.info("Received: {}", msg.toString)
  }  
}

private[unicomplex] case object CubeSupervisorRegistry

class CubeSupervisor extends Actor with ActorLogging with GracefulStopHelper {

  import Unicomplex._

  override def preStart() = {
    uniActor.tell(CubeSupervisorRegistry, self)
  }

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case e: Exception => 
        log.warning(s"Received ${e.getClass.getName} with message ${e.getMessage} from ${sender.path}")
        Restart
    }

  def receive = {
    case GracefulStop => // The stop message should only come from the uniActor
      if (sender != uniActor)
        log.error(s"got GracefulStop from $sender instead of ${Unicomplex.uniActor}")
      else
        defaultMidActorStop(context.children)

    case StartCubeActor(props, name) => 
      val cubeActor = context.actorOf(props, name)
      log.info("Started actor {}", cubeActor.path)

    case msg: Any =>
      log.info("Received: {}", msg.toString)
  }
}