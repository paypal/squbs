package org.squbs.unicomplex

import java.util.Date
import java.util
import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.Try

import akka.actor._
import akka.actor.SupervisorStrategy._
import com.typesafe.config.Config
import spray.can.Http

import org.squbs.lifecycle.{ExtensionLifecycle, GracefulStopHelper, GracefulStop}

class UnicomplexExtension(system: ExtendedActorSystem) extends Extension {

  val uniActor = system.actorOf(Props[Unicomplex], "unicomplex")

  lazy val serviceRegistry = new ServiceRegistry(system)

  private var _scannedComponents: Seq[String] = null

  private[unicomplex] def setScannedComponents(components: Seq[String]): Unit = synchronized {
    // Allowing setting only once
    if (_scannedComponents != null && _scannedComponents != components)
      throw new IllegalStateException(s"_scannedComponents previously set to ${_scannedComponents}")
    else if (_scannedComponents == null)
      _scannedComponents = components
  }

  lazy val scannedComponents = _scannedComponents

  val config = system.settings.config.getConfig("squbs")

  lazy val externalConfigDir = config.getString("external-config-dir")
}

object Unicomplex extends ExtensionId[UnicomplexExtension] with ExtensionIdProvider {

  override def lookup() = Unicomplex

  override def createExtension(system: ExtendedActorSystem) = new UnicomplexExtension(system)

  type InitReport = Try[Option[String]]

  def config(implicit context: ActorContext): Config = apply(context.system).config

  def externalConfigDir(implicit context: ActorContext): String = apply(context.system).externalConfigDir

  def apply()(implicit context: ActorContext): ActorRef = apply(context.system).uniActor

  def serviceRegistry(implicit context: ActorContext) = apply(context.system).serviceRegistry

  // Unicomplex actor registry so we can find it without setting up remote or having an actor system (needed on shutdown)
  private[unicomplex] val actors = new mutable.HashMap[String, ActorRef] with mutable.SynchronizedMap[String, ActorRef]

  def apply(actorSystemName: String): ActorRef = actors(actorSystemName)
}

import Unicomplex._

private[unicomplex] case object PreStartWebService
private[unicomplex] case class  StartWebService(name: String, config: Config)
private[unicomplex] case object RoutesStarted
private[unicomplex] case class  StartCubeActor(props: Props, name: String = "", initRequired: Boolean = false)
private[unicomplex] case object CheckInitStatus
private[unicomplex] case class  InitReports(state: LifecycleState, reports: Map[ActorRef, Option[InitReport]])
private[unicomplex] case object Started
private[unicomplex] case class  CubeRegistration(name: String, fullName: String, version: String, cubeSupervisor: ActorRef)
private[unicomplex] case object ShutdownTimedout
private[unicomplex] case class Extensions(exts: Seq[(String, String, ExtensionLifecycle)])


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
case class StartCube(cubeName: String,
                     initInfoMap: Map[UnicomplexBoot.StartupType.Value, Seq[UnicomplexBoot.InitInfo]],
                     listenerAliases: Map[String, String]
                    )
case class StopTimeout(timeout: FiniteDuration)

/**
 * The Unicomplex actor is the supervisor of the Unicomplex.
 * It starts actors that are part of the Unicomplex.
 */
class Unicomplex extends Actor with Stash with ActorLogging {

  implicit val executionContext = context.dispatcher

  private var shutdownTimeout = 1.second

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case e: Exception =>
        log.warning(s"Received ${e.getClass.getName} with message ${e.getMessage} from ${sender().path}")
        Restart
    }

  private var systemStart: Option[Timestamp] = None

  private var systemStarted: Option[Timestamp] = None

  private var systemActive: Option[Timestamp] = None

  private var systemStop: Option[Timestamp] = None

  private var systemState: LifecycleState = Starting

  private var cubes = Map.empty[ActorRef, (CubeRegistration, Option[InitReports])]

  private var extensions = Seq.empty[(String, String, ExtensionLifecycle)]

  private var lifecycleListeners = Seq.empty[(ActorRef, Seq[LifecycleState])]

  private var servicesStarted: Option[Int] = None // None means no svc infra, Some(n) - n listeners initialized

  private var serviceListeners = Map.empty[String, (ActorRef, ActorRef)] // Service actor and HttpListener actor

  private var listenersBound = false

  /**
   * MXBean for exposing Unicomplex state
   */
  class SystemStateBean extends SystemStateMXBean {

    private[Unicomplex] var startTime: Date = null
    private[Unicomplex] var initDuration = -1
    private[Unicomplex] var activationDuration = -1

    override def getSystemState: String = systemState.toString

    override def getStartTime: Date = startTime

    override def getInitMillis: Int = initDuration

    override def getActivationMillis: Int = activationDuration
  }

  class CubesBean extends CubesMXBean {

    override def getCubes: util.List[CubeInfo] = {
      import collection.JavaConversions._
      cubes.values.toSeq map { c => CubeInfo(c._1.name, c._1.fullName, c._1.version, c._1.cubeSupervisor.path.name) }
    }
  }

  private val stateMXBean = new SystemStateBean

  override def preStart() {
    Unicomplex.actors += context.system.name -> self

    import JMX._
    register(stateMXBean, prefix + systemStateName)
    register(new CubesBean, prefix + cubesName)
  }

  override def postStop() {
    import JMX._ // JMX registrations
    unregister(prefix + cubesName)
    unregister(prefix + systemStateName)

    Unicomplex.actors -= context.system.name
  }

  private def shutdownState: Receive = {

    case Http.ClosedAll =>
      serviceListeners.values foreach (_._1 ! PoisonPill)

    case Terminated(target) => log.debug(s"$target is terminated")
      if (cubes contains target) {
        cubes -= target
      } else {
        serviceListeners = serviceListeners.filterNot {
          case (_, (`target`, _)) => true
          case _ => false
        }
      }

      if (cubes.isEmpty && serviceListeners.isEmpty) {
        log.info("All CubeSupervisors and services were terminated. Shutting down the system")
        updateSystemState(Stopped)
        context.system.shutdown()
      }

    case ShutdownTimedout => log.warning("Graceful shutdown timed out.")
      updateSystemState(Stopped)
      context.system.shutdown()
  }

  private def hotDeployReceive: Receive = {
    case StopCube(cubeName) =>
      log.info(s"got StopCube($cubeName) from ${sender()}")
      stopCube(cubeName) match {
        case Some(cube) =>
          log.debug(s"Shutting down cube $cubeName")
          context.become(waitCubeStop(sender()))
        case None =>
          log.warning(s"Could not find cube $cubeName")
          sender() ! Ack
      }

    case StartCube(cubeName, initInfoMap, listenerAliases) =>
      log.info(s"got StartCube($cubeName) from ${sender()}")
      startCube(cubeName, initInfoMap, listenerAliases)
      sender() ! Ack
  }

  private def waitCubeStop(originalSender: ActorRef): Receive = {
    case Terminated(cubeSupervisor) =>
      cubes -= cubeSupervisor
      originalSender ! Ack
      context.unbecome()
      unstashAll()
      log.debug(s"Cube supervisor $cubeSupervisor terminated.")


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
      log.info(s"got GracefulStop from ${sender().path}.")
      updateSystemState(Stopping)
      servicesStarted match {
        case Some(_) =>
          serviceListeners foreach { case (name, (_, httpListener)) =>
              serviceRegistry.stopWebService(name, httpListener)
          }
          servicesStarted = None
          serviceListeners = Map.empty
        case _ =>
      }
      cubes.foreach(_._1 ! GracefulStop)
      context.become(shutdownState)
      log.info(s"Set shutdown timeout $shutdownTimeout")
      context.system.scheduler.scheduleOnce(shutdownTimeout, self, ShutdownTimedout)
  }

  def receive = hotDeployReceive orElse shutdownBehavior orElse {
    case t: Timestamp => // Setting the real start time from bootstrap
      systemStart = Some(t)
      stateMXBean.startTime = new Date(t.millis)

    case Extensions(es) => // Extension registration
      extensions = es

    case r: CubeRegistration => // Cube registration requests, normally from bootstrap
      cubes = cubes + (r.cubeSupervisor -> (r, None))
      context.watch(r.cubeSupervisor)

    case PreStartWebService => // Sent from Bootstrap before Started signal to tell we have web services to start.
      if (servicesStarted == None) servicesStarted = Some(0)

    case StartWebService(name, config) => // Sent from Bootstrap to start the web service infrastructure.
      val serviceRef = serviceRegistry.startWebService(name, config, notifySender = sender())
      context.become ({
        case b: Http.Bound =>
          serviceListeners = serviceListeners + (name -> (serviceRef, sender()))
          if (serviceListeners.size == serviceRegistry.registrar().size) {
            listenersBound = true
            updateSystemState(checkInitState(cubes.values map (_._2)))            
          }
          context.unbecome()
          unstashAll()
          
        case f: Http.CommandFailed =>
          log.error(s"Failed to bind listener $name. Cleaning up. System may not function properly.")
          serviceRef ! PoisonPill
          serviceRegistry.registrar send { _ - name }
          serviceRegistry.registry send { _ - name }
          serviceRegistry.serviceActorContext send { _ - name }
          context.unbecome()
          unstashAll()
          
        case _ => stash()
      }, 
      discardOld = false)

    case RoutesStarted => // From Bootstrap -> ServiceRegistrar -> Unicomplex to signal all known WS started.
      servicesStarted = Some(servicesStarted.get + 1)
      if (servicesStarted.get == serviceRegistry.registrar().size)
        updateSystemState(checkInitState(cubes.values map (_._2)))

    case Started => // Bootstrap startup and extension init done
      updateSystemState(Initializing)

    case ir: InitReports => // Cubes initialized
      updateCubes(ir)

    case ReportStatus => // Status report request from admin tooling
      if (systemState == Active) // Stable state.
        sender ! (systemState, cubes)
      else {
        val requester = sender()
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
        log.warning(s"""Received startup report from non-registered cube "${sender().path}".""")
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
    case Some(_) if !listenersBound => true
    case _ => false
  }

  def updateSystemState(state: LifecycleState) {
    if (state != systemState) {
      systemState = state

      state match { // Record and log the times.
        case Initializing =>
          systemStarted = Some(Timestamp(System.nanoTime, System.currentTimeMillis))
          val elapsed = (systemStarted.get.nanos - systemStart.get.nanos) / 1000000
          stateMXBean.initDuration = elapsed.asInstanceOf[Int]
          log.info(s"squbs started in $elapsed milliseconds")

        case Active =>
          systemActive = Some(Timestamp(System.nanoTime, System.currentTimeMillis))
          val elapsed = (systemActive.get.nanos - systemStart.get.nanos) / 1000000
          stateMXBean.activationDuration = elapsed.asInstanceOf[Int]
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

  def services = serviceRegistry.registry().values

  // TODO use the following in hot deployment
  private[unicomplex] def stopCube(cubeName: String): Option[CubeRegistration] = {
    implicit val executionContext = context.dispatcher

    // Unregister the routes of this cube across all listeners if there are any
    serviceRegistry.registry() foreach { case (listener, registry) =>
      registry.values foreach {
        case Register(`cubeName`, _, _, routeDef) =>
          serviceRegistry.registrar()(listener) ! Unregister(routeDef.webContext)
        case _ =>
      }
    }

    // Stop the CubeSupervisor if there is one
    val cubeOption = cubes find { case (ref, (CubeRegistration(_, fullName, _, _), _)) => fullName == cubeName }

    cubeOption foreach (_._1 ! GracefulStop)

    cubeOption map { case (_, (registration, _)) => registration}
  }

  private[unicomplex] def startCube(cubeName: String,
                                    initInfoMap: Map[UnicomplexBoot.StartupType.Value, Seq[UnicomplexBoot.InitInfo]],
                                    listenerAliases: Map[String, String]): Unit = {
    import UnicomplexBoot._
    implicit val executionContext = context.dispatcher

    // TODO prevent starting an active Cube
    // Start actors if there are any
    if (cubes.exists { case (ref, (CubeRegistration(_, fullName, _ ,_), _)) => fullName == cubeName })
      println(s"[warn][Bootstrap] actors in $cubeName are already activated")
    else
      initInfoMap.getOrElse(StartupType.ACTORS, Seq.empty[InitInfo]).filter(_.symName == cubeName)
        .foreach(startActors(_)(context.system))

    // Start services if there are any
    initInfoMap.getOrElse(StartupType.SERVICES, Seq.empty[InitInfo]).filter(_.symName == cubeName)
      .foreach(startRoutes(_, listenerAliases)(context.system))
  }
}

class CubeSupervisor extends Actor with ActorLogging with GracefulStopHelper {

  val name = self.path.elements.last

  class CubeStateBean extends CubeStateMXBean {

    override def getName: String = name

    override def getCubeState: String = cubeState.toString
  }

  override def preStart() {
    import JMX._
    val cubeStateMXBean = new CubeStateBean
    register(cubeStateMXBean, prefix + cubeStateName + name)
  }

  override def postStop() {
    import JMX._
    unregister(prefix + cubeStateName + name)
  }

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case e: Exception =>
        log.warning(s"Received ${e.getClass.getName} with message ${e.getMessage} from ${sender().path}")
        Restart
    }

  private var cubeState: LifecycleState = Initializing
  private val initMap = mutable.HashMap.empty[ActorRef, Option[InitReport]]

  private var maxChildTimeout = stopTimeout
  Unicomplex() ! StopTimeout(maxChildTimeout * 2)

  private val stopSet = mutable.Set.empty[ActorRef]

  context.become(startupReceive orElse receive, discardOld = false)

  def startupReceive: Actor.Receive = {

    case StartCubeActor(props, cubeName, initRequired) =>
      val cubeActor = context.actorOf(props, cubeName)
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
        log.error(s"got GracefulStop from ${sender()} instead of ${Unicomplex()}")
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
      else log.warning(s"""Actor "${sender().path}" updating startup status is not registered. """ +
        "Please register by setting init-required = true in squbs-meta.conf")

    case CheckInitStatus => // Explicitly requested reports have an attached requested flag as a tuple
      sender ! (InitReports(cubeState, initMap.toMap), true)
  }
}