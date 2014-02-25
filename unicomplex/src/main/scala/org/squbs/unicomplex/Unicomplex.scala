package org.squbs.unicomplex

import scala.concurrent.duration._
import scala.util.Try
import akka.actor._
import akka.actor.SupervisorStrategy._
import com.typesafe.config.ConfigFactory
import org.squbs.lifecycle.{GracefulStopHelper, GracefulStop}
import scala.collection.mutable
import akka.actor.OneForOneStrategy
import akka.actor.Terminated
import java.util.concurrent.TimeUnit
import akka.pattern.pipe

object Unicomplex {

  type InitReport = Try[Option[String]]

  val config = ConfigFactory.load.getConfig("squbs")

  implicit val actorSystem = ActorSystem(config.getString("actorsystem-name"))

  val uniActor = actorSystem.actorOf(Props[Unicomplex], "unicomplex")

  val externalConfigDir = "squbsconfig"

  def apply() = uniActor
}

import Unicomplex._

private[unicomplex] case object StartWebService
private[unicomplex] case class  StartCubeActor(props: Props, name: String = "", initRequired: Boolean = false)
private[unicomplex] case object CheckInitStatus
private[unicomplex] case class  InitReports(state: LifecycleState, reports: Map[ActorRef, Option[InitReport]])
private[unicomplex] case object Started
private[unicomplex] case class  CubeRegistration(name: String, fullName: String, version: String, cubeSupervisor: ActorRef)
private[unicomplex] case class  StartTime(nanos: Long, millis: Long)
private[unicomplex] case object ShutdownTimeout


sealed trait LifecycleState
case object Starting extends LifecycleState // uniActor starts from Starting state
case object Initializing extends LifecycleState // Cubes start from Initializing state
case object Active extends LifecycleState
case object Failed extends LifecycleState
case object Stopping extends LifecycleState
case object Stopped extends LifecycleState

case class Initialized(report: InitReport)
case object Ack
case object ReportStatus
case class ObtainLifecycleEvents(states: LifecycleState*)

case class StopCube(cubeName: String)
case class StartCube(cubeName: String)

/**
 * The Unicomplex actor is the supervisor of the Unicomplex.
 * It starts actors that are part of the Unicomplex.
 */
class Unicomplex extends Actor with Stash with ActorLogging {

  implicit val executionContext = actorSystem.dispatcher

  val shutdownTimeout = FiniteDuration(config.getMilliseconds("shutdown-timeout"), TimeUnit.MILLISECONDS)

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case e: Exception =>
        log.warning(s"Received ${e.getClass.getName} with message ${e.getMessage} from ${sender.path}")
        Restart
    }

  private var systemStart: Option[StartTime] = None

  private var systemStarted: Option[StartTime] = None

  private var systemActive: Option[StartTime] = None

  private var systemStop: Option[StartTime] = None

  private var systemState: LifecycleState = Starting

  private var cubes = Map.empty[ActorRef, (CubeRegistration, Option[InitReports])]

  private var lifecycleListeners = Seq.empty[(ActorRef, Seq[LifecycleState])]

  private def shutdownState: Receive = {
    case Terminated(target) => log.debug(s"$target is terminated")
      cubes -= target
      if (cubes.isEmpty) {
        log.info("All CubeSupervisors were terminated. Shutting down the system")
        updateSystemState(Stopped)
        actorSystem.shutdown()
      }

    case ShutdownTimeout => log.warning("Graceful shutdown timed out.")
      updateSystemState(Stopped)
      actorSystem.shutdown()
  }

  private def hotDeployReceive: Receive = {
    case StopCube(cubeName) =>
      log.info(s"got StopCube($cubeName) from $sender")
      Bootstrap.stopCube(cubeName) pipeTo self
      context.become(waitCubeStop(sender))

    case StartCube(cubeName) =>
      log.info(s"got StartCube($cubeName) from $sender")
      Bootstrap.startCube(cubeName)
      sender ! Ack
  }

  private def waitCubeStop(originalSender: ActorRef): Receive = {
    case cubeSupervisor: ActorRef => cubeSupervisor ! GracefulStop
      context.become({
        case Terminated(`cubeSupervisor`) => cubes -= cubeSupervisor
          originalSender ! Ack
          context.unbecome()
          unstashAll()

        case other => stash()
      }, true)

    // This cube doesn't contain any cube actors or cube already stopped
    case Status.Failure(e) => originalSender ! Ack
      context.unbecome()
      unstashAll()

    case other => stash()
  }

  def receive = hotDeployReceive orElse {
    case GracefulStop =>
      log.info(s"got GracefulStop from $sender")
      updateSystemState(Stopping)
      cubes.foreach(_._1 ! GracefulStop)
      context.become(shutdownState)
      actorSystem.scheduler.scheduleOnce(shutdownTimeout, self, ShutdownTimeout)

    case t: StartTime => // Setting the real start time from bootstrap
      systemStart = Some(t)

    case r: CubeRegistration => // Cube registration requests, normally from bootstrap
      cubes = cubes + (r.cubeSupervisor -> (r, None))
      context.watch(r.cubeSupervisor)

    case StartWebService =>
      ServiceRegistry.startWebService
      sender ! Ack

    case Started => // Bootstrap startup and extension init done
      updateSystemState(Initializing)

    case ir: InitReports => // Cubes initialized
      updateCubes(ir)

    case ReportStatus => // Status report request from admin tooling
      if (systemState == Active) // Stable state.
        sender ! (systemState, cubes)
      else {
        val requester = sender
        var pendingCubes = cubes collect {
          case (actorRef, (_, None)) => actorRef
          case (actorRef, (_, Some(InitReports(state, _)))) if state != Active => actorRef
        }

        pendingCubes foreach (_ ! CheckInitStatus)

        val expected: Actor.Receive = {
          case ReportStatus => stash() // Stash concurrent ReportStatus requests, handle everything else.

          case (ir: InitReports, true) =>
            updateCubes(ir)
            pendingCubes = pendingCubes.filter(_ != sender)
            if (pendingCubes.isEmpty) {
              requester ! (systemState, cubes)
              unstashAll()
              context.unbecome()
            }
        }

        context.become(expected orElse receive, discardOld = false)
      }

    case r: ObtainLifecycleEvents => // Registration of lifecycle listeners
      lifecycleListeners = lifecycleListeners :+ (sender -> r.states)
  }

  def updateCubes(reports: InitReports) {
    val reg = cubes get sender
    reg match {
      case Some((registration, _)) =>
        cubes = cubes + (sender -> (registration, Some(reports)))
        updateSystemState(checkInitState(cubes.values map (_._2)))
      case _ =>
        log.warning(s"""Received startup report from non-registered cube "${sender.path}".""")
    }
  }

  def checkInitState(reportOptions: Iterable[Option[InitReports]]): LifecycleState = {
    val states: Iterable[LifecycleState] = reportOptions map {
      case None => Initializing
      case Some(reports) => reports.state
    }
    if (states exists (_ == Failed)) Failed
    else if (states exists (_ == Initializing)) Initializing
    else Active
  }

  def updateSystemState(state: LifecycleState) {
    if (state != systemState) {
      systemState = state

      state match { // Record and log the times.
        case Initializing =>
          systemStarted = Some(StartTime(System.nanoTime, System.currentTimeMillis))
          val elapsed = (systemStarted.get.nanos - systemStart.get.nanos) / 1000000
          log.info(s"squbs started in $elapsed milliseconds")

        case Active =>
          systemActive = Some(StartTime(System.nanoTime, System.currentTimeMillis))
          val elapsed = (systemActive.get.nanos - systemStarted.get.nanos) / 1000000
          log.info(s"squbs active in $elapsed milliseconds")

        case Stopping =>
          systemStop = Some(StartTime(System.nanoTime, System.currentTimeMillis))
          val elapsed = (systemStop.get.nanos - systemActive.getOrElse(systemStarted.get).nanos) / 1000000
          log.info(s"squbs has been running in ${elapsed} milliseconds")

        case Stopped =>
          val current = StartTime(System.nanoTime, System.currentTimeMillis)
          val elapsed = (current.nanos - systemStop.get.nanos) / 1000000
          log.info(s"squbs stopped in $elapsed milliseconds")

        case _ =>
      }

      lifecycleListeners foreach {case (actorRef, states) => // Send state to all listeners.
        if (states.isEmpty || states.contains(state)) actorRef ! state
      }
    }
  }
}

private[unicomplex] case object CubeSupervisorRegistry

class CubeSupervisor extends Actor with ActorLogging with GracefulStopHelper {

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case e: Exception =>
        log.warning(s"Received ${e.getClass.getName} with message ${e.getMessage} from ${sender.path}")
        Restart
    }

  var cubeState: LifecycleState = Initializing
  val initMap = mutable.HashMap.empty[ActorRef, Option[InitReport]]

  context.become(startupReceive orElse receive, discardOld = false)

  def startupReceive: Actor.Receive = {

    case StartCubeActor(props, name, initRequired) =>
      val cubeActor = context.actorOf(props, name)
      if (initRequired) initMap += cubeActor -> None
      log.info(s"Started actor ${cubeActor.path}")

    case Started => // Signals end of StartCubeActor messages. No more allowed after this.
      if (initMap.isEmpty) {
        cubeState = Active
        Unicomplex() ! InitReports(cubeState, initMap.toMap)
      }
      context.unbecome()
  }

  def receive = {
    case GracefulStop => // The stop message should only come from the uniActor
      if (sender != uniActor)
        log.error(s"got GracefulStop from $sender instead of ${Unicomplex()}")
      else
        defaultMidActorStop(context.children)

    case Initialized(report) =>
      if (initMap contains sender) {
        initMap += sender -> Some(report)
        if (!(initMap exists (_._2 == None))) {
          val finalMap = (initMap mapValues (_.get)).toMap
          if (finalMap.exists(_._2.isFailure)) cubeState = Failed else cubeState = Active
          Unicomplex() ! InitReports(cubeState, initMap.toMap)
        }
      }
      else log.warning(s"""Actor "${sender.path}" updating startup status is not registered. """ +
        "Please register by setting init-required = true in squbs-meta.conf")

    case CheckInitStatus => // Explicitly requested reports have an attached requested flag as a tuple
      sender ! (InitReports(cubeState, initMap.toMap), true)
  }
}