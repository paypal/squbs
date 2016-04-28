/*
 *  Copyright 2015 PayPal
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

import java.net.BindException

import akka.actor.Actor.Receive
import akka.actor.SupervisorStrategy._
import akka.actor._
import akka.agent.Agent
import akka.event.LoggingAdapter
import akka.io.IO
import com.typesafe.config.Config
import org.squbs.pipeline.streaming.PipelineSetting
import spray.can.Http
import spray.can.server.ServerSettings
import spray.http.StatusCodes.NotFound
import spray.http.Uri.Path
import spray.http._
import spray.io.ServerSSLEngineProvider
import spray.routing.{Route, _}

import scala.collection.mutable
import scala.language.postfixOps
import scala.util.{Try, Failure, Success}

case class RegisterContext(listeners: Seq[String], webContext: String, actor: ActorWrapper, ps: PipelineSetting)

object RegisterContext {

  /**
   * the input path should be normalized, i.e: remove the leading slash
   */
  private[unicomplex] def pathMatch(path: Path, target: Path): Boolean = {
    if(path.length < target.length) false
    else {
      def innerMatch(path: Path, target:Path):Boolean = {
        if (target.isEmpty) true
        else target.head.equals(path.head) match {
          case true => innerMatch(path.tail, target.tail)
          case _ => false
        }
      }
      innerMatch(path, target)
    }
  }
}

case class SqubsRawHeader(name: String, value: String, lowercaseName: String) extends HttpHeader {
  def render[R <: Rendering](r: R): r.type = r ~~ name ~~ ':' ~~ ' ' ~~ value
}


object WebContextHeader {
  val name = classOf[WebContextHeader].getName
  val lowerName = name.toLowerCase

  def apply(webCtx: String) = new WebContextHeader(webCtx)
}

class WebContextHeader(webCtx: String) extends SqubsRawHeader(WebContextHeader.name, webCtx, WebContextHeader.lowerName)

object LocalPortHeader {
  val name: String = classOf[LocalPortHeader].getName
  val lowerName = name.toLowerCase

  def apply(port: Int) = new LocalPortHeader(port)
}

class LocalPortHeader(port: Int) extends SqubsRawHeader(LocalPortHeader.name, port.toString, LocalPortHeader.lowerName)

/**
  * Spray based [[ServiceRegistryBase]] implementation.
  */
class ServiceRegistry(val log: LoggingAdapter) extends ServiceRegistryBase[Path] {

  case class ServiceListenerInfo(squbsListener: Option[ActorRef], httpListener: Option[ActorRef], exception: Option[Throwable] = None)

  private var serviceListeners = Map.empty[String, ServiceListenerInfo]

  private var listenerRoutesVar = Map.empty[String, Agent[Seq[(Path, ActorWrapper, PipelineSetting)]]]

  override protected def listenerRoutes: Map[String, Agent[Seq[(Path, ActorWrapper, PipelineSetting)]]] = listenerRoutesVar

  override protected def listenerRoutes_=[B](newListenerRoutes: Map[String, Agent[Seq[(B, ActorWrapper, PipelineSetting)]]]): Unit =
    listenerRoutesVar = newListenerRoutes.asInstanceOf[Map[String, Agent[Seq[(Path, ActorWrapper, PipelineSetting)]]]]

  override protected def pathCompanion(s: String) = Path(s)

  override protected def pathLength(p: Path) = p.length

  /**
   * Starts the web service. This should be called from the Unicomplex actor
   * upon seeing the first service registration.
   */
  override private[unicomplex] def startListener(name: String, config: Config, notifySender: ActorRef)
                                       (implicit context: ActorContext): Receive = {

    val BindConfig(interface, port, localPort, sslContext, needClientAuth) = Try { bindConfig(config) } match {
      case Success(bc) => bc
      case Failure(ex) =>
        serviceListeners = serviceListeners + (name -> ServiceListenerInfo(None, None, Some(ex)))
        notifySender ! Failure(ex)
        throw ex
    }

    val props = Props(classOf[ListenerActor], name, listenerRoutes(name), localPort)
    val listenerRef = context.actorOf(props, name)

    listenerRef ! notifySender // listener needs to send the notifySender an ack when it is ready.

    // Create a new HttpServer using our handler and tell it where to bind to
    implicit val self = context.self
    implicit val system = context.system

    sslContext match {
      case Some(sslCtx) =>
        val settings = ServerSettings(system).copy(sslEncryption = true)
        implicit val serverEngineProvider = ServerSSLEngineProvider { engine =>
          engine.setNeedClientAuth(needClientAuth)
          engine
        }
        IO(Http) ! Http.Bind(listenerRef, interface, port, settings = Option(settings))

      case None => IO(Http) ! Http.Bind(listenerRef, interface, port) // Non-SSL
    }

    context.watch(listenerRef)

    {
      case _: Http.Bound =>
        import org.squbs.unicomplex.JMX._
        JMX.register(new ServerStats(name, context.sender()), prefix + serverStats + name)
        serviceListeners = serviceListeners + (name -> ServiceListenerInfo(Some(listenerRef), Some(context.sender())))
        context.self ! HttpBindSuccess
      case failed: Http.CommandFailed =>
        serviceListeners = serviceListeners + (name -> ServiceListenerInfo(None, None, Some(new BindException(failed.toString))))
        log.error(s"Failed to bind listener $name. Cleaning up. System may not function properly.")
        context.unwatch(listenerRef)
        listenerRef ! PoisonPill
        context.self ! HttpBindFailed
      }
  }

  override private[unicomplex] def registerContext(listeners: Iterable[String], webContext: String, servant: ActorWrapper,
                                                   ps: PipelineSetting)(implicit context: ActorContext) {
    listeners foreach { listener =>
      val agent = listenerRoutes(listener)
      agent.send {
        currentSeq =>
          merge(currentSeq, webContext, servant, ps, {
            log.warning(s"Web context $webContext already registered on $listener. Override existing registration.")
          })
      }
    }
  }

  override private[unicomplex] def stopAll()(implicit context: ActorContext): Unit = {
    serviceListeners foreach {
      case (name, ServiceListenerInfo(_, Some(httpListener), None)) =>
        stopListener(name, httpListener)
        import JMX._
        JMX.unregister(prefix + serverStats + name)
      case _ =>
    }
  }

  // In very rare cases, we block. Shutdown is one where we want to make sure it is stopped.
  private def stopListener(name: String, httpListener: ActorRef)(implicit context: ActorContext) = {
    implicit val self = context.self
    implicit val system = context.system
    listenerRoutes = listenerRoutes - name
    httpListener ! Http.Unbind
    if (listenerRoutes.isEmpty) {
      IO(Http) ! Http.CloseAll

      import org.squbs.unicomplex.JMX._
      unregister(prefix + listenersName)
    }
  }

  override private[unicomplex] def isShutdownComplete = serviceListeners.isEmpty

  override private[unicomplex] def isAnyFailedToInitialize = serviceListeners.values exists (_.exception.nonEmpty)

  override private[unicomplex] def shutdownState: Receive = {
    case Http.ClosedAll =>
      serviceListeners.values foreach {
        case ServiceListenerInfo(Some(svcActor), Some(_), None) => svcActor ! PoisonPill
        case _ =>
      }
  }

  override private[unicomplex] def listenerTerminated(listenerActor: ActorRef): Unit = {
    serviceListeners = serviceListeners.filterNot {
      case (_, ServiceListenerInfo(Some(`listenerActor`), Some(_), None)) => true
      case _ => false
    }
  }

  override private[unicomplex] def isListenersBound = serviceListeners.size == listenerRoutes.size

  override protected def listenerStateMXBean(): ListenerStateMXBean = {
    new ListenerStateMXBean {
      import scala.collection.JavaConversions._
      override def getListenerStates: java.util.List[ListenerState] = {
        serviceListeners map { case (name, ServiceListenerInfo(squbsListener, httpListener, exception)) =>
          ListenerState(name, squbsListener.map(_ => "Success").getOrElse("Failed"), exception.getOrElse("").toString)
        } toSeq
      }
    }
  }
}

private[unicomplex] class RouteActor(webContext: String, clazz: Class[RouteDefinition])
  extends Actor with HttpService with ActorLogging {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test

  import scala.concurrent.duration._

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case e: Exception =>
        log.error(s"Received ${e.getClass.getName} with message ${e.getMessage} from ${sender().path}")
        Escalate
    }

  def actorRefFactory = context

  val routeDef =
    try {
      val d = RouteDefinition.startRoutes {
        WebContext.createWithContext[RouteDefinition](webContext) {
          clazz.newInstance
        }
      }
      context.parent ! Initialized(Success(None))
      d
    } catch {
      case e: Exception =>
        log.error(e, s"Error instantiating route from {}: {}", clazz.getName, e)
        context.parent ! Initialized(Failure(e))
        context.stop(self)
        RouteDefinition.startRoutes(new RejectRoute)
    }


  implicit val rejectionHandler:RejectionHandler = routeDef.rejectionHandler.getOrElse(PartialFunction.empty[List[Rejection], Route])
  implicit val exceptionHandler:ExceptionHandler = routeDef.exceptionHandler.getOrElse(PartialFunction.empty[Throwable, Route])

  lazy val route = if (webContext.nonEmpty) {
    pathPrefix(separateOnSlashes(webContext)) {routeDef.route}
  } else {
    // don't append pathPrefix if webContext is empty, won't be null due to the top check
    routeDef.route
  }

  def receive = {
    case request =>
      runRoute(route).apply(request)
  }
}

private[unicomplex] class ListenerActor(name: String, routes: Agent[Seq[(Path, ActorWrapper, PipelineSetting)]],
                                        localPort: Option[Int] = None) extends Actor with ActorLogging {
  import RegisterContext._

  val pendingRequests = mutable.WeakHashMap.empty[ActorRef, ActorRef]
  val localPortHeader = localPort.map(LocalPortHeader(_))

  // Finds the route/request handling actor and patches the request with required headers in one shot, if found.
  def reqAndActor(request: HttpRequest): Option[(HttpRequest, ActorRef)] = {

    import request.uri.path
    val normPath = if (path.startsWithSlash) path.tail else path //normalize it to make sure not start with '/'
    val routeOption = routes() find { case (contextPath, _, _) => pathMatch(normPath, contextPath) }

    routeOption flatMap {
      case (webCtx, ProxiedActor(actor), _) => Some(patchHeaders(request, Some(webCtx.toString())), actor)
      case (_, SimpleActor(actor), _) => Some(patchHeaders(request), actor)
      case _ => None
    }
  }

  // internal method for patch listener's related headers
  def patchHeaders(request: HttpRequest, webCtx: Option[String] = None): HttpRequest =
    webCtx.map(WebContextHeader(_)) ++ localPortHeader match {
      case headers if headers.nonEmpty => request.mapHeaders(headers ++: _)
      case _ => request
    }

  def receive = {
    // Notify the real sender for completion, but in lue of the parent
    case ref: ActorRef =>
      ref.tell(Ack, context.parent)
      context.become(wsReceive)
  }

  def wsReceive: Receive = {

    case _: Http.Connected => sender() ! Http.Register(self)

    case request: HttpRequest =>
      reqAndActor(request) match {
        case Some((req, actor)) => actor forward req
        case _ => sender() ! HttpResponse(NotFound, "The requested resource could not be found.")
      }

    case reqStart: ChunkedRequestStart =>
      reqAndActor(reqStart.request) match {
        case Some((req, actor)) =>
          actor forward reqStart.copy(request = req)
          pendingRequests += sender() -> actor
          context.watch(sender())
        case _ => sender() ! HttpResponse(NotFound, "The requested resource could not be found.")
      }

    case chunk: MessageChunk =>
      pendingRequests.get(sender()) match {
        case Some(actor) => actor forward chunk
        case None => log.warning("Received request chunk from unknown request. Possibly already timed out.")
      }

    case chunkEnd: ChunkedMessageEnd =>
      pendingRequests.get(sender()) match {
        case Some(actor) =>
          actor forward chunkEnd
          pendingRequests -= sender()
          context.unwatch(sender())
        case None => log.warning("Received request chunk end from unknown request. Possibly already timed out.")
      }

    case timedOut@Timedout(request: HttpRequest) =>
      reqAndActor(request) match {
        case Some((req, actor)) => actor forward Timedout(req)
        case _ => log.warning(s"Received Timedout message for unknown context ${request.uri.path.toString()} .")
      }

    case timedOut@Timedout(reqStart: ChunkedRequestStart) =>
      reqAndActor(reqStart.request) match {
        case Some((req, actor)) =>
          actor forward Timedout(reqStart.copy(request = req))
          pendingRequests -= sender()
          context.unwatch(sender())
        case _ => log.warning(
          s"Received Timedout message for unknown context ${reqStart.request.uri.path.toString()} .")
      }

    case timedOut: Timedout =>
      pendingRequests.get(sender()) match {
        case Some(actor) =>
          actor forward timedOut
          pendingRequests -= sender()
          context.unwatch(sender())
        case None => log.warning(s"Received unknown Timedout message.")
      }

    case Terminated(responder) =>
      log.info("Chunked input responder terminated.")
      pendingRequests -= responder
  }

}

object RouteDefinition {

  private[unicomplex] val localContext = new ThreadLocal[Option[ActorContext]] {
    override def initialValue(): Option[ActorContext] = None
  }

  def startRoutes[T](fn: => T)(implicit context: ActorContext): T = {
    localContext.set(Some(context))
    val r = fn
    localContext.set(None)
    r
  }
}

trait RouteDefinition {
  protected implicit final val context: ActorContext = RouteDefinition.localContext.get.get
  implicit final lazy val self = context.self

  def route: Route

  def rejectionHandler: Option[RejectionHandler] = None

  def exceptionHandler: Option[ExceptionHandler] = None
}

class RejectRoute extends RouteDefinition {

  import spray.routing.directives.RouteDirectives.reject
  val route: Route = reject
}

/**
 * Other media types beyond what Spray supports.
 */
object MediaTypeExt {
  val `text/event-stream` = MediaTypes.register(
    MediaType.custom("text", "event-stream", compressible = true))
}
