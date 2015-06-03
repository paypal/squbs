/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the AUTHORS file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.squbs.unicomplex

import javax.net.ssl.SSLContext

import akka.actor.SupervisorStrategy.Stop
import akka.actor._
import akka.agent.Agent
import akka.event.LoggingAdapter
import akka.io.IO
import com.typesafe.config.Config
import spray.can.Http
import spray.can.server.ServerSettings
import spray.http.HttpHeaders.RawHeader
import spray.http.StatusCodes.NotFound
import spray.http._
import spray.io.ServerSSLEngineProvider
import spray.routing.Route
import spray.routing._

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import ConfigUtil._

case class RegisterContext(listeners: Seq[String], webContext: String, actor: ActorWrapper)

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

class ServiceRegistry(log: LoggingAdapter) {

  var listenerRoutes = Map.empty[String, Agent[Map[String, ActorWrapper]]]

  class ListenerBean extends ListenerMXBean {

    override def getListeners: java.util.List[ListenerInfo] = {
      import scala.collection.JavaConversions._
      listenerRoutes.flatMap { case (listenerName, agent) =>
        agent() map { case (webContext, servant) =>
          ListenerInfo(listenerName, webContext, servant.actor.toString)
        }
      }.toSeq
    }
  }

  private[unicomplex] def prepListeners(listenerNames: Iterable[String])(implicit context: ActorContext) {
    import context.dispatcher
    listenerRoutes = listenerNames.map { listener =>
      listener -> Agent[Map[String, ActorWrapper]](Map.empty)
    }.toMap

    import org.squbs.unicomplex.JMX._
    register(new ListenerBean, prefix + listenersName)
  }

  private[unicomplex] def registerContext(listeners: Iterable[String], webContext: String, servant: ActorWrapper) {
    listeners foreach { listener =>
      val agent = listenerRoutes(listener)
      agent.send { currentMap =>
        currentMap get webContext match {
          case Some(ref) =>
            log.warning(s"Web context $webContext already registered on $listener. Ignoring new registration.")
            currentMap
          case None => currentMap + (webContext -> servant)
        }
      }
    }
  }

  private[unicomplex] def deregisterContext(webContexts: Seq[String])
                                           (implicit ec: ExecutionContext): Future[Ack.type] = {
    val futures = listenerRoutes flatMap {
      case (_, agent) => webContexts map {ctx => agent.alter(_ - ctx)}
    }
    Future.sequence(futures) map {_ => Ack}
  }

  /**
   * Starts the web service. This should be called from the Unicomplex actor
   * upon seeing the first service registration.
   */
  private[unicomplex] def startListener(name: String, config: Config, notifySender: ActorRef)
                                       (implicit context: ActorContext) = {
    val interface = if (config getBoolean "full-address") ConfigUtil.ipv4
                    else config getString "bind-address"
    val port = config getInt "bind-port"
    // assign the localPort only if local-port-header is true
    val localPort = config getOptionalBoolean ("local-port-header") flatMap (useHeader => if (useHeader) Some(port) else None)
    val props = Props(classOf[ListenerActor], name, listenerRoutes(name), localPort)
    val listenerRef = context.actorOf(props, name)

    // register(new PredefinedActorBean(props, listenerRef, context.self), prefix + actorInfo + name )

    listenerRef ! notifySender // listener needs to send the notifySender an ack when it is ready.

    // create a new HttpServer using our handler tell it where to bind to

    implicit val self = context.self
    implicit val system = context.system

    // SSL use case
    if (config.getBoolean("secure")) {
      val settings = ServerSettings(system).copy(sslEncryption = true)

      val sslContextClassName = config.getString("ssl-context")
      implicit def sslContext: SSLContext =
        if (sslContextClassName == "default") SSLContext.getDefault
        else {
          try {
            val clazz = Class.forName(sslContextClassName)
            clazz.getMethod("getServerSslContext").invoke(clazz.newInstance()).asInstanceOf[SSLContext]
          } catch {
            case e : Throwable =>
              System.err.println(s"WARN: Failure obtaining SSLContext from $sslContextClassName. " +
                "Falling back to default.")
              SSLContext.getDefault
          }
        }

      val needClientAuth = config.getBoolean("need-client-auth")

      implicit val serverEngineProvider = ServerSSLEngineProvider { engine =>
        engine.setNeedClientAuth(needClientAuth)
        engine
      }

      IO(Http) ! Http.Bind(listenerRef, interface, port, settings = Option(settings))

    } else IO(Http) ! Http.Bind(listenerRef, interface, port) // Non-SSL

    context.watch(listenerRef)
  }

  // In very rare cases, we block. Shutdown is one where we want to make sure it is stopped.
  private[unicomplex] def stopListener(name: String, httpListener: ActorRef)(implicit context: ActorContext) = {
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
        Stop//Escalate
    }

  def actorRefFactory = context

  val matchContext = separateOnSlashes(webContext)

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
        log.error(s"Error instantiating route from ${clazz.getName}: $e", e)
        context.parent ! Initialized(Failure(e))
        context.stop(self)
        null
    }
  
  implicit val rejectionHandler:RejectionHandler = routeDef.rejectionHandler.getOrElse(PartialFunction.empty[List[Rejection], Route])
  implicit val exceptionHandler:ExceptionHandler = routeDef.exceptionHandler.getOrElse(PartialFunction.empty[Throwable, Route])

  def receive = {
    case request =>
      runRoute(pathPrefix(matchContext) { routeDef.route }).apply(request)
  }
}

private[unicomplex] class ListenerActor(name: String, routeMap: Agent[Map[String, ActorWrapper]], localPort: Option[Int] = None) extends Actor
with ActorLogging {

  val pendingRequests = mutable.WeakHashMap.empty[ActorRef, ActorRef]
  val localPortHeader = localPort.map(LocalPortHeader(_))

  def contextActor(request: HttpRequest): Option[(HttpRequest, ActorRef)] = {
    // internal method for patch listener's related headers
    def patchHeaders(request: HttpRequest, webCtx: Option[String] = None) = webCtx.map(WebContextHeader(_)) ++ localPortHeader match {
      case headers if headers.nonEmpty => request.mapHeaders(headers ++: _)
      case _ => request
    }

    val path = {
      val p = request.uri.path.toString()
      if (p startsWith "/") p substring 1 else p
    }

    val rMap = routeMap()
    val matches = rMap filter { case (webContext, _) =>
      path.startsWith(webContext) && (path.length == webContext.length || path.charAt(webContext.length) == '/')
    }
    
    val item = if (matches.isEmpty) rMap.get("") map("" -> _)
               else Some(matches.maxBy(_._1.length)) // Return the longest match or the default one, just the actor portion.

    item flatMap  {
      case (webCtx, ProxiedActor(actor)) => Some(patchHeaders(request, Some(webCtx)), actor)
      case (_, SimpleActor(actor)) => Some(patchHeaders(request), actor)
      case _ => None
    }
  }


  def receive = {
    // Notify the real sender for completion, but in lue of the parent
    case ref: ActorRef =>
      ref.tell(Ack, context.parent)
      context.become(wsReceive)
  }

  def wsReceive: Receive = {

    case _: Http.Connected => sender() ! Http.Register(self)

    case req: HttpRequest =>
      contextActor(req) match {
        case Some((req, actor)) => actor forward req
        case _ => sender() ! HttpResponse(NotFound, "The requested resource could not be found.")
      }

    case reqStart: ChunkedRequestStart =>
      contextActor(reqStart.request) match {
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

    case timedOut@Timedout(req: HttpRequest) =>
      contextActor(req) match {
        case Some((req, actor)) => actor forward Timedout(req)
        case _ => log.warning(s"Received Timedout message for unknown context ${req.uri.path.toString()} .")
      }

    case timedOut@Timedout(reqStart: ChunkedRequestStart) =>
      contextActor(reqStart.request) match {
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

object WebContext {

  private[unicomplex] val localContext = new ThreadLocal[Option[String]] {
    override def initialValue(): Option[String] = None
  }

  def createWithContext[T](webContext: String)(fn: => T): T = {
    localContext.set(Some(webContext))
    val r = fn
    localContext.set(None)
    r

  }
}



trait WebContext {
  protected final val webContext: String = WebContext.localContext.get.get
}
/**
 * Other media types beyond what Spray supports.
 */
object MediaTypeExt {
  val `text/event-stream` = MediaTypes.register(
    MediaType.custom("text", "event-stream", compressible = true))
}