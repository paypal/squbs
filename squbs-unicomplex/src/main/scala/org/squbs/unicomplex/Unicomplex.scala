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

package org.squbs.unicomplex


import java.io.{PrintWriter, StringWriter}
import java.util
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

import akka.NotUsed
import akka.actor.Actor.Receive
import akka.actor.SupervisorStrategy._
import akka.actor.{Extension => AkkaExtension, _}
import akka.agent.Agent
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.pattern._
import akka.stream.scaladsl.Flow
import akka.util.Timeout
import com.typesafe.config.Config
import org.squbs.lifecycle.{ExtensionLifecycle, GracefulStop, GracefulStopHelper}
import org.squbs.pipeline.PipelineSetting
import org.squbs.unicomplex.UnicomplexBoot.StartupType

import scala.annotation.varargs
import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

class UnicomplexExtension(system: ExtendedActorSystem) extends AkkaExtension {

  val uniActor = system.actorOf(Props[Unicomplex], "unicomplex")

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

  val boot = Agent[UnicomplexBoot](null)(system.dispatcher)
}

object Unicomplex extends ExtensionId[UnicomplexExtension] with ExtensionIdProvider {

  override def lookup() = Unicomplex

  override def createExtension(system: ExtendedActorSystem) = new UnicomplexExtension(system)

  type InitReport = Try[Option[String]]

  def config(implicit context: ActorContext): Config = apply(context.system).config

  def externalConfigDir(implicit context: ActorContext): String = apply(context.system).externalConfigDir

  def apply()(implicit context: ActorContext): ActorRef = apply(context.system).uniActor

  // Unicomplex actor registry so we can find it without setting up remote or having an actor system (needed on shutdown)
  private[unicomplex] val actors = new ConcurrentHashMap[String, ActorRef]

  def apply(actorSystemName: String): ActorRef = actors.get(actorSystemName)

}

import org.squbs.unicomplex.Unicomplex._


private[unicomplex] case class PreStartWebService(listeners: Map[String, Config])
private[unicomplex] case object StartWebService
private[unicomplex] case class  StartListener(name: String, config: Config)
private[unicomplex] case object RoutesStarted
private[unicomplex] case class  StartCubeActor(props: Props, name: String = "", initRequired: Boolean = false)
private[unicomplex] case class  StartCubeService(webContext: String, listeners: Seq[String], props: Props,
                                                 name: String = "", ps: PipelineSetting, initRequired: Boolean = false)
private[unicomplex] case class  StartFailure(t: Throwable)

private[unicomplex] case object CheckInitStatus
private[unicomplex] case object Started
private[unicomplex] case object Activate
private[unicomplex] case object ActivateTimedOut
private[unicomplex] case object ShutdownTimedOut

case class Cube(name: String, fullName: String, version: String, jarPath: String)
case class InitReports(state: LifecycleState, reports: Map[ActorRef, Option[InitReport]])
case class CubeRegistration(info: Cube, cubeSupervisor: ActorRef)
case class Extension(info: Cube, sequence: Int, extLifecycle: Option[ExtensionLifecycle],
                     exceptions: Seq[(String, Throwable)])
case class Extensions(extensions: Seq[Extension])


sealed trait LifecycleState {
  // for Java
  def instance = this
}
case object Starting extends LifecycleState // uniActor starts from Starting state
case object Initializing extends LifecycleState // Cubes start from Initializing state
case object Active extends LifecycleState
case object Failed extends LifecycleState
case object Stopping extends LifecycleState
case object Stopped extends LifecycleState

case class Initialized(report: InitReport)
case object Ack
case object ReportStatus
case class StatusReport(state: LifecycleState, cubes: Map[ActorRef, (CubeRegistration, Option[InitReports])],
                        extensions: Seq[Extension])
case class Timestamp(nanos: Long, millis: Long)
case object SystemState {
  // for Java
  def instance = this
}
case object LifecycleTimesRequest
case class LifecycleTimes(start: Option[Timestamp], started: Option[Timestamp],
                          active: Option[Timestamp], stop: Option[Timestamp])
case class ObtainLifecycleEvents(states: LifecycleState*)
// for Java
object ObtainLifecycleEvents {
  @varargs def create(states: LifecycleState*) = new ObtainLifecycleEvents(states : _*)
}
case class StopTimeout(timeout: FiniteDuration)
case class StopCube(name: String)
case class StartCube(name: String)

private[unicomplex] case object HttpBindSuccess
private[unicomplex] case object HttpBindFailed

case object PortBindings

case class FlowWrapper(flow: Flow[HttpRequest, HttpResponse, NotUsed], actor: ActorRef)

/**
 * The Unicomplex actor is the supervisor of the Unicomplex.
 * It starts actors that are part of the Unicomplex.
 */
class Unicomplex extends Actor with Stash with ActorLogging {

  import context.dispatcher

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

  private var activated = false

  private var cubes = Map.empty[ActorRef, (CubeRegistration, Option[InitReports])]

  private var extensions = Seq.empty[Extension]

  private var lifecycleListeners = Seq.empty[(ActorRef, Seq[LifecycleState], Boolean)] // Last boolean is flag whether to remove

  private var servicesStarted= false

  import org.squbs.util.ConfigUtil._

  lazy val serviceRegistry = new ServiceRegistry(log)

  private val unicomplexExtension = Unicomplex(context.system)
  import unicomplexExtension._

  // $COVERAGE-OFF$
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



  // $COVERAGE-ON$

  class CubesBean extends CubesMXBean {

    override def getCubes: util.List[CubeInfo] = {
      import scala.collection.JavaConversions._

      cubes.values.toSeq map { c =>
        CubeInfo(c._1.info.name, c._1.info.fullName, c._1.info.version, c._1.cubeSupervisor.toString())
      }
    }
  }

  class ExtensionsBean extends ExtensionsMXBean {
    override def getExtensions: util.List[ExtensionInfo] = {
      import scala.collection.JavaConversions._
      extensions map { e =>
        val (phase, ex) = e.exceptions.headOption map {
          case (iphase, exception) => (iphase, exception.toString)
        } getOrElse (("", ""))
        ExtensionInfo(e.info.name, e.sequence, phase, ex)
      }
    }
  }


  private val stateMXBean = new SystemStateBean


  override def preStart() {
    Unicomplex.actors.put(context.system.name, self)

    import org.squbs.unicomplex.JMX._
    register(stateMXBean, prefix + systemStateName)
    register(new CubesBean, prefix + cubesName)
    register(new SystemSettingBean(context.system.settings.config), prefix + systemSettingName)
    register(new ExtensionsBean, prefix + extensionsName)
  }

  override def postStop() {
    import org.squbs.unicomplex.JMX._ // JMX registrations
    unregister(prefix + extensionsName)
    unregister(prefix + cubesName)
    unregister(prefix + systemStateName)
    unregister(prefix + systemSettingName)

    Unicomplex.actors.remove(context.system.name)
  }

  private def shutdownState: Receive = {

    case Terminated(target) => log.debug(s"$target is terminated")
      if (cubes contains target) {
        cubes -= target
      } else {
        serviceRegistry.listenerTerminated(target)
      }

      if (cubes.isEmpty && serviceRegistry.isShutdownComplete) {
        log.info("All CubeSupervisors and services were terminated. Shutting down the system")
        updateSystemState(Stopped)
        context.system.shutdown()
      }

    case ShutdownTimedOut => log.warning("Graceful shutdown timed out.")
      updateSystemState(Stopped)
      context.system.shutdown()
  }

  def shutdownBehavior: Receive = {
    case StopTimeout(timeout) => if (shutdownTimeout < timeout) shutdownTimeout = timeout

    case GracefulStop =>
      log.info(s"got GracefulStop from ${sender().path}.")
      updateSystemState(Stopping)
      if (servicesStarted) {
          serviceRegistry.stopAll()
          servicesStarted = false
      }

      cubes.foreach(_._1 ! GracefulStop)
      context.become(shutdownState orElse serviceRegistry.shutdownState)
      log.info(s"Set shutdown timeout $shutdownTimeout")
      context.system.scheduler.scheduleOnce(shutdownTimeout, self, ShutdownTimedOut)
  }

  def stopAndStartCube: Receive = {
    case StopCube(name) =>
      val responder = sender()
      boot.get().cubes.find(_.info.name == name) flatMap {cube =>
        cube.components.get(StartupType.SERVICES)
      } map {configs =>
        configs.map(_.getString("web-context"))
      } match {
        case Some(webContexts) =>
          serviceRegistry.deregisterContext(webContexts)
          self ! Ack
        case None => self ! Ack
      }
      context.become({
        case Ack =>
          context.actorSelection(s"/user/$name") ! Identify(name)
          context.become({
            case ActorIdentity(`name`, Some(cubeSupervisor)) =>
              cubes get cubeSupervisor match {
                case Some(cube) =>
                  cubes -= cubeSupervisor
                  cubeSupervisor ! GracefulStop
                  context.become({
                    case Terminated(`cubeSupervisor`) =>
                      responder ! Ack
                      unstashAll()
                      context.unbecome()
                    case other => stash()
                  }, true)
                case None =>
                  unstashAll()
                  context.unbecome()
              }
            case ActorIdentity(`name`, None) =>
              log.warning(s"Cube $name does not exist")
              unstashAll()
              context.unbecome()
            case other => stash()
          }, true)
        case Status.Failure(e) =>
          log.warning(s"Failed to unregister web-contexts. Cause: $e")
          unstashAll()
          context.unbecome()
        case other => stash()
      }, false)

    case StartCube(name) =>
      val responder = sender()
      context.actorSelection(s"/user/$name") ! Identify(name)
      context.become({
        case ActorIdentity(cubeName, Some(cubeSupervisor)) =>
          log.warning(s"Cube $cubeName is already started")
          unstashAll()
          context.unbecome()
        case ActorIdentity(`name`, None) =>
          boot.get().cubes.find(_.info.name == name) foreach {cube =>
            UnicomplexBoot.startComponents(cube, boot.get().listenerAliases)(context.system)
          }
          responder ! Ack
          unstashAll()
          context.unbecome()
        case other => stash()
      }, false)
  }

  def receive = stopAndStartCube orElse shutdownBehavior orElse {
    case t: Timestamp => // Setting the real start time from bootstrap
      systemStart = Some(t)
      stateMXBean.startTime = new Date(t.millis)

    case Extensions(es) => // Extension registration
      extensions = es
      updateSystemState(checkInitState())

    case r: CubeRegistration => // Cube registration requests, normally from bootstrap
      cubes = cubes + (r.cubeSupervisor -> (r, None))
      context.watch(r.cubeSupervisor)

    // Sent from Bootstrap before Started signal to tell we have web services to start.
    case PreStartWebService(listeners) =>
      if (!servicesStarted) {
        servicesStarted = true
        serviceRegistry.prepListeners(listeners.keys)
      }

    case RegisterContext(listeners, webContext, serviceHandler, ps) =>
      sender() ! Try { serviceRegistry.registerContext(listeners, webContext, serviceHandler, ps) }

    case StartListener(name, conf) => // Sent from Bootstrap to start the web service infrastructure.

      Try { serviceRegistry.startListener(name, conf, notifySender = sender()) } match {
        case Success(startupBehavior) =>
          context.become(startupBehavior orElse {
            case HttpBindSuccess =>
              if (serviceRegistry.isListenersBound) updateSystemState(checkInitState())
              context.unbecome()
              unstashAll()
            case HttpBindFailed =>
              updateSystemState(checkInitState())
              context.unbecome()
              unstashAll()

            case _ => stash()
          },
            discardOld = false)

        case Failure(t) => updateSystemState(checkInitState())
      }

    case Started => // Bootstrap startup and extension init done
      updateSystemState(Initializing)

    case Activate => // Bootstrap is done. Register for callback when system is active or failed. Remove afterwards
      lifecycleListeners = lifecycleListeners :+ (sender(), Seq(Active, Failed), true)
      activated = true
      updateSystemState(checkInitState())

    case ActivateTimedOut =>
      // Deploy failFastStrategy for checking once activate times out.
      checkInitState = failFastStrategy _
      updateSystemState(checkInitState())
      sender() ! systemState

    case ir: InitReports => // Cubes initialized
      updateCubes(ir)

    case ReportStatus => // Status report request from admin tooling
      if (systemState == Active) sender ! StatusReport(systemState, cubes, extensions)
      else {
        val requester = sender()
        var pendingCubes = cubes collect {
          case (actorRef, (_, None)) => actorRef
          case (actorRef, (_, Some(InitReports(state, _)))) if state != Active => actorRef
        }

        if (pendingCubes.isEmpty) sender() ! StatusReport(systemState, cubes, extensions)
        else {
          pendingCubes foreach (_ ! CheckInitStatus)

          val expected: Actor.Receive = {
            case ReportStatus => stash() // Stash concurrent ReportStatus requests, handle everything else.

            case (ir: InitReports, true) =>
              updateCubes(ir)
              pendingCubes = pendingCubes.filter(_ != sender())
              if (pendingCubes.isEmpty) {
                requester ! StatusReport(systemState, cubes, extensions)
                unstashAll()
                context.unbecome()
              }
          }
          context.become(expected orElse receive, discardOld = false)
        }
      }

    case SystemState =>
      sender() ! systemState

    case r: ObtainLifecycleEvents => // Registration of lifecycle listeners
      lifecycleListeners = lifecycleListeners :+ (sender(), r.states, false)

    case LifecycleTimesRequest => // Obtain all timestamps.
      sender() ! LifecycleTimes(systemStart, systemStarted, systemActive, systemStop)

    case PortBindings => // Obtain listener names and port bindings, mainly used for tests
      sender() ! serviceRegistry.portBindings
  }

  def updateCubes(reports: InitReports) {
    val reg = cubes get sender()
    reg match {
      case Some((registration, _)) =>
        cubes = cubes + (sender() -> (registration, Some(reports)))
        updateSystemState(checkInitState())
      case _ =>
        log.warning(s"""Received startup report from non-registered cube "${sender().path}".""")
    }
  }

  def cubeStates: Iterable[LifecycleState] = {
    val reportOptions = cubes.values map (_._2)
    reportOptions map {
      case None => Initializing
      case Some(reports) => reports.state
    }
  }

  val checkStateFailed: PartialFunction[Iterable[LifecycleState], LifecycleState] = {
    case states if states exists (_ == Failed) =>
      if (systemState != Failed) log.warning("Some cubes failed to initialize. Marking system state as Failed")
      Failed
    case _ if serviceRegistry.isAnyFailedToInitialize =>
      if (systemState != Failed) log.warning("Some listeners failed to initialize. Marking system state as Failed")
      Failed
    case _ if extensions exists (_.exceptions.nonEmpty) =>
      if (systemState != Failed) log.warning("Some extensions failed to initialize. Marking the system state as Failed")
      Failed
  }

  val checkStateInitializing: PartialFunction[Iterable[LifecycleState], LifecycleState] = {
    case states if states exists (_ == Initializing) => Initializing
    case _ if pendingServiceStarts => Initializing
    case _ if !activated => Initializing
  }

  val active = { _: Any => Active }

  def failFastStrategy = checkStateFailed orElse checkStateInitializing applyOrElse (cubeStates, active)

  def lenientStrategy  = checkStateInitializing orElse checkStateFailed applyOrElse (cubeStates, active)

  var checkInitState = lenientStrategy _

  def pendingServiceStarts = servicesStarted && !serviceRegistry.isListenersBound

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
        lifecycleListeners = lifecycleListeners filterNot { case (actorRef, states, remove) =>
          if (states.isEmpty || states.contains(state)) {
            actorRef ! state // Send state to all listeners.
            remove
          } else false
        }
    }
  }


}

class CubeSupervisor extends Actor with ActorLogging with GracefulStopHelper {
  import context.dispatcher

  import scala.collection.JavaConversions._

  val cubeName = self.path.name
  val actorErrorStatesAgent = Agent[Map[String, ActorErrorState]](Map())

  implicit val timeout = UnicomplexBoot.defaultStartupTimeout

  class CubeStateBean extends CubeStateMXBean {

    override def getName: String = cubeName

    override def getCubeState: String = cubeState.toString

    override def getWellKnownActors: String = context.children.mkString(",")

    override def getActorErrorStates: util.List[ActorErrorState] = actorErrorStatesAgent().values.toList
  }

  override def preStart() {
    import org.squbs.unicomplex.JMX._
    val cubeStateMXBean = new CubeStateBean
    register(cubeStateMXBean, prefix + cubeStateName + cubeName)

  }

  override def postStop() {
    import org.squbs.unicomplex.JMX._
    unregister(prefix + cubeStateName + cubeName)
  }

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case e: Exception =>
        val actorPath = sender().path.toStringWithoutAddress
        log.warning(s"Received ${e.getClass.getName} with message ${e.getMessage} from $actorPath")
        actorErrorStatesAgent.send{states =>
          val stringWriter = new StringWriter()
          e.printStackTrace(new PrintWriter(stringWriter))
          val stackTrace = stringWriter.toString
          val state = states.get(actorPath) match {
            case Some(s) => s.copy(errorCount = s.errorCount + 1, latestException = stackTrace)
            case _ => ActorErrorState(actorPath, 1, stackTrace)
          }
          states + (actorPath -> state)
        }
        Restart
    }

  private var cubeState: LifecycleState = Initializing
  private var pendingContexts = 0
  private var pendingNotifiees = Seq.empty[ActorRef]
  private val initMap = mutable.HashMap.empty[ActorRef, Option[InitReport]]

  private var maxChildTimeout = stopTimeout
  Unicomplex() ! StopTimeout(maxChildTimeout * 2)

  private val stopSet = mutable.Set.empty[ActorRef]

  context.become(startupReceive orElse receive, discardOld = false)

  case class ContextRegistrationResult(cubeActor: ActorRef, state: Try[RegisterContext])

  def startupReceive: Actor.Receive = {

    case StartCubeActor(props, name, initRequired) =>
      val cubeActor = context.actorOf(props, name)

      if (initRequired) initMap += cubeActor -> None
      log.info(s"Started actor ${cubeActor.path}")

    case StartCubeService(webContext, listeners, props, name, ps, initRequired)
        if classOf[FlowSupplier].isAssignableFrom(props.actorClass) =>
      val hostActor = context.actorOf(props, name)
      initMap += hostActor -> None
      pendingContexts += 1
      (hostActor ? FlowRequest).mapTo[Try[Flow[HttpRequest, HttpResponse, NotUsed]]] onSuccess {
        case Success(flow) =>
          val reg = RegisterContext(listeners, webContext, FlowWrapper(flow, hostActor), ps)
          (Unicomplex() ? reg).mapTo[Try[_]].map {
            case Success(_) => ContextRegistrationResult(hostActor, Success(reg))
            case Failure(t) => ContextRegistrationResult(hostActor, Failure(t))
          }   .pipeTo(self)
        case Failure(e) =>
          self ! ContextRegistrationResult(hostActor, Failure(e))
      }

    case StartCubeService(webContext, listeners, props, name, ps, initRequired) =>
      val hostActor = context.actorOf(props, name)
      import org.squbs.util.ConfigUtil._
      val concurrency = context.system.settings.config.get[Int]("akka.http.server.pipelining-limit")
      val flow = Flow[HttpRequest].mapAsync(concurrency) { request => (hostActor ? request).mapTo[HttpResponse] }
      val wrapper = FlowWrapper(flow, hostActor)

      if (initRequired && !(initMap contains hostActor)) initMap += hostActor -> None
      val reg = RegisterContext(listeners, webContext, wrapper, ps)
      pendingContexts += 1
      (Unicomplex() ? reg).mapTo[Try[_]].map {
        case Success(_) => ContextRegistrationResult(hostActor, Success(reg))
        case Failure(t) => ContextRegistrationResult(hostActor, Failure(t))
      }   .pipeTo(self)

    case ContextRegistrationResult(cubeActor, tr) =>
      tr match {
        case Failure(t) =>
          initMap += cubeActor -> Some(Failure(t))
          cubeState = Failed
          Unicomplex() ! InitReports(cubeState, initMap.toMap)
        case Success(reg) =>
          log.info(s"Started service actor ${cubeActor.path} for context ${reg.webContext}")
      }
      pendingContexts -= 1
      if (pendingContexts == 0 && pendingNotifiees.nonEmpty) {
        pendingNotifiees.foreach(_ ! Started)
        pendingNotifiees = Seq.empty
        context.unbecome()
      }

    case StartFailure(t) =>
      // Register the failure with a bogus noop actor so we have all failures.
      // The real servicing actor was never created.
      initMap += context.actorOf(Props[NoopActor]) -> Some(Failure(t))
      cubeState = Failed
      Unicomplex() ! InitReports(cubeState, initMap.toMap)

    case Started => // Signals end of StartCubeActor messages. No more allowed after this.
      if (initMap.isEmpty) {
        cubeState = Active
        Unicomplex() ! InitReports(cubeState, initMap.toMap)
      }
      if (pendingContexts == 0) {
        sender() ! Started
        context.unbecome()
      }
      else pendingNotifiees :+= sender()
  }

  def receive = {
    case StopTimeout(timeout) =>
      if (maxChildTimeout < timeout) {
        maxChildTimeout = timeout
        Unicomplex() ! StopTimeout(maxChildTimeout * 2)
      }
      stopSet += sender()

    case GracefulStop => // The stop message should only come from the uniActor
      if (sender() != Unicomplex())
        log.error(s"got GracefulStop from ${sender()} instead of ${Unicomplex()}")
      else
        defaultMidActorStop(stopSet, maxChildTimeout)

    case Initialized(report) =>
      initMap get sender() match {
        case Some(entry) => // Registered cube

          entry match {
            // First report or nothing previously marked as failure, just add/overwrite report
            case None =>
              initMap += sender() -> Some(report)

            case Some(prevReport) if prevReport.isSuccess =>
              initMap += sender() -> Some(report)

            case _ => // There is some issue previously marked, usually a failure. Don't record new report.
              // Only first failure should be recorded. Just leave it as failed. Don't touch.
          }

          // Check that all is initialized and whether it is all good.
          if (!(initMap exists (_._2.isEmpty))) {
            val finalMap = (initMap mapValues (_.get)).toMap
            if (finalMap.exists(_._2.isFailure)) cubeState = Failed else cubeState = Active
            Unicomplex() ! InitReports(cubeState, initMap.toMap)
          }

        case None => // Never registered cube
          log.warning(s"""Actor "${sender().path}" updating startup status is not registered. """ +
            "Please register by setting init-required = true in squbs-meta.conf")
      }

    case CheckInitStatus => // Explicitly requested reports have an attached requested flag as a tuple
      sender() ! (InitReports(cubeState, initMap.toMap), true)

  }
}

/**
  * A noop actor used when a stub ActorRef is needed to register failures.
  */
private[unicomplex] class NoopActor extends Actor {
  def receive = PartialFunction.empty
}
