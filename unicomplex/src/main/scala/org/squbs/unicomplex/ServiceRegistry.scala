package org.squbs.unicomplex


import javax.net.ssl.SSLContext
import scala.util.Try
import akka.io.IO
import akka.actor._
import akka.agent.Agent
import spray.can.Http
import spray.can.server.ServerSettings
import spray.http.{MediaType, MediaTypes}
import spray.io.ServerSSLEngineProvider
import spray.routing._
import Directives._
import com.typesafe.config.Config
import scala.concurrent.Await
import akka.util.Timeout
import scala.concurrent.duration._
import scala.annotation.tailrec

case class Register(symName: String, alias: String, version: String, routeDef: RouteDefinition)
case class Unregister(key: String)

class ServiceRegistry(system: ActorSystem) {

  implicit val executionContext = system.dispatcher
  
  type Registry = Map[String, Register] // Registers context against the Register record

  private[unicomplex] val registrar = Agent[Map[String, ActorRef]](Map.empty)
  private[unicomplex] val serviceActorContext = Agent[Map[String, ActorContext]](Map.empty)
  private[unicomplex] val registry= Agent[Map[String, Registry]](Map.empty)

  /**
   * Starts the web service. This should be called from the Unicomplex actor
   * upon seeing the first service registration.
   */
  private[unicomplex] def startWebService(name: String, config: Config, notifySender: ActorRef)
                                         (implicit context: ActorContext) = {

    val route = Agent[Route](null) // Route for registrar and service pair
    val registrarRef = context.actorOf(Props(classOf[Registrar], name, route), name + "-registrar")
    registrar send { _ + (name -> registrarRef) }
    registry send { _ + (name -> Map.empty) }
    val serviceRef = context.actorOf(Props(classOf[WebSvcActor], name, route), name + "-service")
    serviceRef ! notifySender // serviceRef needs to send the notifySender an ack when it is ready.

    // create a new HttpServer using our handler tell it where to bind to
    import ConfigUtil._
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

      IO(Http) ! Http.Bind(serviceRef, interface, port, settings = Option(settings))

    } else if (bindService) IO(Http) ! Http.Bind(serviceRef, interface, port) // Non-SSL

    context.watch(registrarRef)
    context.watch(serviceRef)
  }

  // In very rare cases, we block. Shutdown is one where we want to make sure it is stopped.
  private[unicomplex] def stopWebService(name: String, httpListener: ActorRef)(implicit context: ActorContext) = {
    implicit val self = context.self
    implicit val system = context.system
    val lRegistrar = registrar()
    lRegistrar get name foreach { r =>
      context.unwatch(r)
      r ! PoisonPill
    }
    val empty = (lRegistrar - name).isEmpty
    registrar send { _ - name }
    httpListener ! Http.Unbind
    
    if (empty) IO(Http) ! Http.CloseAll
  }
}

/**
 * The Registrar receives Register and Unregister messages.
 */
private[unicomplex] class Registrar(listenerName: String, route: Agent[Route]) extends Actor with ActorLogging {

  val serviceRegistry = Unicomplex.serviceRegistry
  import serviceRegistry._

  class ContextsBean extends ContextsMXBean {

    override def getContexts: java.util.List[ContextInfo] = {
      import collection.JavaConversions._
      registry()(listenerName).map { case (ctx, Register(symName, alias, version, routeDef)) =>
        ContextInfo(ctx, routeDef.getClass.getName, symName, version)
      } .toSeq
    }
  }

  override def preStart() {
    import JMX._
    register(new ContextsBean, prefix + contextsName + listenerName)
  }

  override def postStop()  {
    import JMX._
    unregister(prefix + contextsName + listenerName)
  }

  //private var registry = Map.empty[String, Register]

  // CalculateRoute MUST return a function and not a value
  private def calculateRoute(tmpRegistry: Registry) = {
    Try(tmpRegistry.map {
      case (webContext, Register(_, _, _, routeDef)) => pathPrefix(webContext) {
        routeDef.route
      }
    }.reduceLeft(_ ~ _)).getOrElse(path(Slash) {
      get {
        complete {
          "Default Route"
        }
      }
    })
  }

  def receive = {
    case r @ Register(symName, alias, version, routeDef) =>
      val localRegistry = registry()(listenerName)
      if (localRegistry contains routeDef.webContext)
        log.warning(s"""Web context "${routeDef.webContext}" already registered. Overriding!""")
      val tmpRegistry = localRegistry + (routeDef.webContext -> r)

      // This line is the problem. Don't pre-calculate.
      route send calculateRoute(tmpRegistry)
      registry send { _ + (listenerName -> tmpRegistry) }
      log.info(s"""Web context "${routeDef.webContext}" (${routeDef.getClass.getName}) registered.""")

    case Unregister(webContext) =>
      val tmpRegistry = registry()(listenerName) - webContext
      route send calculateRoute(tmpRegistry)
      registry send { _ + (listenerName -> tmpRegistry) }
      log.info(s"Web service route $webContext unregistered.")

    case RoutesStarted => // Got all the service registrations for now.
      Unicomplex() ! RoutesStarted // Just send the message onto Unicomplex after processing all registrations.
  }
}

/**
 * The main service actor.
 */
private[unicomplex] class WebSvcActor(listenerName: String, route: Agent[Route])
    extends Actor with HttpService with ActorLogging {

  val serviceRegistry = Unicomplex.serviceRegistry
  import serviceRegistry._

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // All RouteDefinitions should use this context.
  serviceActorContext send { _ + (listenerName -> context) }
  log.debug(s"Sent ActorContext for ${context.self.path} to Agent.")

  def receive = {
    // Notify the real sender for completion, but in lue of the parent
    case ref: ActorRef =>
      ref.tell(Ack, context.parent)
      context.become(wsReceive)
  }

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def wsReceive: Receive = runRoute(route().apply(_))
}


object RouteDefinition {

  private[unicomplex] val localContext = new ThreadLocal[Option[ActorContext]] {
    override def initialValue(): Option[ActorContext] = None
  }

  @tailrec
  private def getContext(system: ActorSystem, listenerName: String, retries: Int): Option[ActorContext] = {
    val contextAgent = Unicomplex(system).serviceRegistry.serviceActorContext
    contextAgent().get(listenerName) match {
      case c: Some[ActorContext] => c
      case None =>
        if (retries == 0)
          throw new IllegalStateException(s"Timed out waiting for service actor context for listener $listenerName.")
        implicit val timeout = Timeout(1.seconds)
        Await.ready(contextAgent.future(), timeout.duration)
        getContext(system, listenerName, retries - 1)
    }
  }

  def startRoutes[T](system: ActorSystem, listenerName: String)(fn: ()=>T): T = {
    localContext.set(getContext(system, listenerName, 10))
    val r = fn()
    localContext.set(None)
    r
  }
}

trait RouteDefinition {
  protected implicit final val context: ActorContext = RouteDefinition.localContext.get.get
  implicit final lazy val self = context.self

  val webContext: String
  def route: Route
}

/**
 * Other media types beyond what Spray supports.
 */
object MediaTypeExt {
  
  val `text/event-stream` = MediaTypes.register(
      MediaType.custom("text", "event-stream", compressible = true))

}
