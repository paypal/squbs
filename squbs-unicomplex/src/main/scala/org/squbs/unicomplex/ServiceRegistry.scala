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

import akka.actor._
import akka.agent.Agent
import akka.io.IO
import com.typesafe.config.Config
import spray.can.Http
import spray.can.server.ServerSettings
import spray.http.StatusCodes.NotFound
import spray.http._
import spray.io.ServerSSLEngineProvider
import spray.routing._

import scala.collection.mutable
import scala.util.{Failure, Success}

case class RegisterContext(listeners: Seq[String], webContext: String, actor: ActorRef)

class ServiceRegistry {

  var listenerRoutes = Map.empty[String, Agent[Map[String, ActorRef]]]

  class ListenerBean extends ListenerMXBean {

    override def getListeners: java.util.List[ListenerInfo] = {
      import scala.collection.JavaConversions._
      listenerRoutes.flatMap { case (listenerName, agent) =>
        agent() map { case (webContext, actor) =>
            ListenerInfo(listenerName, webContext, actor.path.toStringWithoutAddress)
        }
      }.toSeq
    }
  }

  private[unicomplex] def prepListeners(listenerNames: Iterable[String])(implicit context: ActorContext) {
    import context.dispatcher
    listenerRoutes = listenerNames.map { listener =>
      listener -> Agent[Map[String, ActorRef]](Map.empty)
    }.toMap

    import org.squbs.unicomplex.JMX._
    register(new ListenerBean, prefix + listenersName)
  }

  private[unicomplex] def registerContext(listeners: Iterable[String], webContext: String, actor: ActorRef) {
    listeners foreach { listener =>
      val agent = listenerRoutes(listener)
      agent.send { _ + (webContext -> actor) }
    }
  }

  /**
   * Starts the web service. This should be called from the Unicomplex actor
   * upon seeing the first service registration.
   */
  private[unicomplex] def startListener(name: String, config: Config, notifySender: ActorRef)
                                         (implicit context: ActorContext) = {

    val listenerRef = context.actorOf(Props(classOf[ListenerActor], name, listenerRoutes(name)), name)
    listenerRef ! notifySender // listener needs to send the notifySender an ack when it is ready.

    // create a new HttpServer using our handler tell it where to bind to
    import org.squbs.unicomplex.ConfigUtil._
    val interface = if(config getBoolean "full-address") ConfigUtil.ipv4
      else config getString "bind-address"
    val port = config getInt "bind-port"
    val bindService = config getOptionalBoolean "bind-service" getOrElse true
    implicit val self = context.self
    implicit val system = context.system

    // SSL use case
    if (bindService && config.getBoolean("secure")) {
      val settings = ServerSettings(system).copy(sslEncryption = true)

      val sslContextClassName = config.getString("ssl-context")
      implicit def sslContext =
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

    } else if (bindService) IO(Http) ! Http.Bind(listenerRef, interface, port) // Non-SSL

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
        log.error(s"Error instantiating route from ${clazz.getName}: $e", e)
        context.parent ! Initialized(Failure(e))
        context.stop(self)
        null
    }

  def receive = {
    case request =>
      runRoute(pathPrefix(webContext) { routeDef.route }).apply(request)
  }
}

private[unicomplex] class ListenerActor(name: String, routeMap: Agent[Map[String, ActorRef]]) extends Actor
    with ActorLogging {

  val pendingRequests = mutable.WeakHashMap.empty[ActorRef, ActorRef]

  def contextActor(request: HttpRequest) = {
    val path = request.uri.path.toString()
    val webContext =
    if (path startsWith "/") {
      val ctxEnd = path.indexOf('/', 1)
      if (ctxEnd >= 1) path.substring(1, ctxEnd)
      else path.substring(1)
    } else {
      val ctxEnd = path.indexOf('/')
      if (ctxEnd >= 0) path.substring(0, ctxEnd)
      else path
    }
    routeMap().get(webContext) orElse routeMap().get("")
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
        case Some(actor) => actor forward req
        case None => sender() ! HttpResponse(NotFound, "The requested resource could not be found.")
      }

    case reqStart: ChunkedRequestStart =>
      contextActor(reqStart.request) match {
        case Some(actor) =>
          actor forward reqStart
          pendingRequests += sender() -> actor
          context.watch(sender())
        case None => sender() ! HttpResponse(NotFound, "The requested resource could not be found.")
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
