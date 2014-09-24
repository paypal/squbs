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
import akka.pattern.pipe
import spray.can.Http.Bound
import spray.can.Http
import Unicomplex._
import java.io.File
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.Config

object Unicomplex {

  type InitReport = Try[Option[String]]
  
  val externalConfigDir = "squbsconfig"
  
  val externalConfigKey = "external-config-files"

  val config = getFullConfig

  val actorSystemName = config.getString("actorsystem-name")

  implicit val actorSystem = ActorSystem(actorSystemName, config)

  val uniActor = actorSystem.actorOf(Props[Unicomplex], "unicomplex")

  def apply() = uniActor
  
  def getFullConfig: Config = {
	  val baseConfig = ConfigFactory.load
	  val configDir = new File(externalConfigDir)
	  val configNames = baseConfig.getConfig("squbs").getStringList(externalConfigKey)
	  val parseOptions = ConfigParseOptions.defaults.setAllowMissing(true)
	  import scala.collection.JavaConversions._
	  val addonConfig = configNames map { name =>
	 	  ConfigFactory.parseFileAnySyntax(new File(configDir, name), parseOptions)
	  }
	  if (addonConfig.isEmpty) baseConfig
	  else ConfigFactory.load((addonConfig :\ baseConfig) (_ withFallback _))
  }
}

private[unicomplex] case object PreStartWebService
private[unicomplex] case object StartWebService
private[unicomplex] case object WebServicesStarted
private[unicomplex] case class  StartCubeActor(props: Props, name: String = "", initRequired: Boolean = false)
private[unicomplex] case object CheckInitStatus
private[unicomplex] case class  InitReports(state: LifecycleState, reports: Map[ActorRef, Option[InitReport]])
private[unicomplex] case object Started
private[unicomplex] case class  CubeRegistration(name: String, fullName: String, version: String, cubeSupervisor: ActorRef)
private[unicomplex] case object ShutdownTimedout


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
case class Timestamp(nanos: Long, millis: Long)
case object SystemState
case object LifecycleTimesRequest
case class LifecycleTimes(start: Option[Timestamp], started: Option[Timestamp],
                          active: Option[Timestamp], stop: Option[Timestamp])
case class ObtainLifecycleEvents(states: LifecycleState*)

case class StopCube(cubeName: String)
case class StartCube(cubeName: String)
case class StopTimeout(timeout: FiniteDuration)

/**
 * The Unicomplex actor is the supervisor of the Unicomplex.
 * It starts actors that are part of the Unicomplex.
 */
class Unicomplex extends Actor with Stash with ActorLogging {

  implicit val executionContext = actorSystem.dispatcher

  private var shutdownTimeout = 1.second

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case e: Exception =>
        log.warning(s"Received ${e.getClass.getName} with message ${e.getMessage} from ${sender.path}")
        Restart
    }

  private var systemStart: Option[Timestamp] = None

  private var systemStarted: Option[Timestamp] = None

  private var systemActive: Option[Timestamp] = None

  private var systemStop: Option[Timestamp] = None

  private var systemState: LifecycleState = Starting

  private var cubes = Map.empty[ActorRef, (CubeRegistration, Option[InitReports])]

  private var lifecycleListeners = Seq.empty[(ActorRef, Seq[LifecycleState])]

  private var servicesStarted: Option[Boolean] = None // None means no svc infra, Some(false) = not yet initialized.

  private var serviceRef: Option[ActorRef] = None

  private var serviceBound = false

  private def shutdownState: Receive = {

    case Http.ClosedAll | Http.Unbound =>
      serviceRef foreach (_ ! PoisonPill)

    case Terminated(target) => log.debug(s"$target is terminated")
      if (cubes contains target) {
        cubes -= target
      } else if (serviceRef.exists(_ == target)) {
        serviceRef = None
      }
      if (cubes.isEmpty && serviceRef == None) {
        log.info("All CubeSupervisors and services were terminated. Shutting down the system")
        updateSystemState(Stopped)
        actorSystem.shutdown()
      }

    case ShutdownTimedout => log.warning("Graceful shutdown timed out.")
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

  def shutdownBehavior: Receive = {
    case StopTimeout(timeout) => if (shutdownTimeout < timeout) shutdownTimeout = timeout

    case GracefulStop =>
      log.info(s"got GracefulStop from ${sender.path}.")
      updateSystemState(Stopping)
      serviceRef match {
        case Some(_) =>
          ServiceRegistry.stopWebService
        case _ =>
      }
      cubes.foreach(_._1 ! GracefulStop)
      context.become(shutdownState)
      log.info(s"Set shutdown timeout $shutdownTimeout")
      actorSystem.scheduler.scheduleOnce(shutdownTimeout, self, ShutdownTimedout)
  }

  def receive = hotDeployReceive orElse shutdownBehavior orElse {
    case t: Timestamp => // Setting the real start time from bootstrap
      systemStart = Some(t)

    case r: CubeRegistration => // Cube registration requests, normally from bootstrap
      cubes = cubes + (r.cubeSupervisor -> (r, None))
      context.watch(r.cubeSupervisor)

    case PreStartWebService => // Sent from Bootstrap before Started signal to tell we have web services to start.
      if (servicesStarted == None) servicesStarted = Some(false)

    case StartWebService => // Sent from Bootstrap to start the web service infrastructure.
      serviceRef = Option(ServiceRegistry.startWebService)
      sender ! Ack

    case WebServicesStarted => // From Bootstrap -> ServiceRegistrar -> Unicomplex to signal all know WS started.
      servicesStarted = Some(true)
      updateSystemState(checkInitState(cubes.values map (_._2)))

    case b: Bound =>
      serviceBound = true
      updateSystemState(checkInitState(cubes.values map (_._2)))


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

    case SystemState =>
      sender ! systemState

    case r: ObtainLifecycleEvents => // Registration of lifecycle listeners
      lifecycleListeners = lifecycleListeners :+ (sender -> r.states)

    case LifecycleTimesRequest => // Obtain all timestamps.
      sender ! LifecycleTimes(systemStart, systemStarted, systemActive, systemStop)
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
    else if (pendingServiceStarts) Initializing
    else Active
  }

  def pendingServiceStarts = servicesStarted match {
    case Some(false) => true
    case Some(true) if !serviceBound => true
    case _ => false
  }

  def updateSystemState(state: LifecycleState) {
    if (state != systemState) {
      systemState = state

      state match { // Record and log the times.
        case Initializing =>
          systemStarted = Some(Timestamp(System.nanoTime, System.currentTimeMillis))
          val elapsed = (systemStarted.get.nanos - systemStart.get.nanos) / 1000000
          log.info(s"squbs started in $elapsed milliseconds")

        case Active =>
          systemActive = Some(Timestamp(System.nanoTime, System.currentTimeMillis))
          val elapsed = (systemActive.get.nanos - systemStart.get.nanos) / 1000000
          log.info(s"squbs active in $elapsed milliseconds")

        case Stopping =>
          systemStop = Some(Timestamp(System.nanoTime, System.currentTimeMillis))
          val elapsed = (systemStop.get.nanos - systemActive.getOrElse(systemStarted.get).nanos) / 1000000
          log.info(s"squbs has been running for $elapsed milliseconds")

        case Stopped =>
          val current = Timestamp(System.nanoTime, System.currentTimeMillis)
          val elapsed = (current.nanos - systemStop.get.nanos) / 1000000
          log.info(s"squbs stopped in $elapsed milliseconds")

        case _ =>
      }

      if (state != Stopped) // don't care about Stopped
        lifecycleListeners foreach {case (actorRef, states) => // Send state to all listeners.
          if (states.isEmpty || states.contains(state)) actorRef ! state
        }
    }
  }
}

class CubeSupervisor extends Actor with ActorLogging with GracefulStopHelper {

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case e: Exception =>
        log.warning(s"Received ${e.getClass.getName} with message ${e.getMessage} from ${sender.path}")
        Restart
    }

  private var cubeState: LifecycleState = Initializing
  private val initMap = mutable.HashMap.empty[ActorRef, Option[InitReport]]

  private var maxChildTimeout = stopTimeout
  Unicomplex() ! StopTimeout(maxChildTimeout * 2)

  private val stopSet = mutable.Set.empty[ActorRef]

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
    case StopTimeout(timeout) =>
      if (maxChildTimeout < timeout) {
        maxChildTimeout = timeout
        Unicomplex() ! StopTimeout(maxChildTimeout * 2)
      }
      stopSet += sender

    case GracefulStop => // The stop message should only come from the uniActor
      if (sender != Unicomplex())
        log.error(s"got GracefulStop from $sender instead of ${Unicomplex()}")
      else
        defaultMidActorStop(stopSet, maxChildTimeout)

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