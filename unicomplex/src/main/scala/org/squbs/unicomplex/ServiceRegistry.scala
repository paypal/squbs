package org.squbs.unicomplex


import scala.util.Try
import akka.io.IO
import akka.actor._
import akka.agent.Agent
import spray.can.Http
import spray.http.{MediaType, MediaTypes}
import spray.routing._
import Directives._
import java.net.InetAddress

case class Register(symName: String, alias: String, version: String, routeDef: RouteDefinition)
case class Unregister(key: String)

class ServiceRegistry(system: ActorSystem) {

  implicit val executionContext = system.dispatcher

  private[unicomplex] val route = Agent[Route](null)
  private[unicomplex] val registrar = Agent[ActorRef](null)
  private[unicomplex] val serviceActorContext = Agent[ActorContext](null)
  private[unicomplex] val registry= Agent[Map[String, Register]](Map.empty)

  /**
   * Starts the web service. This should be called from the Unicomplex actor
   * upon seeing the first service registration.
   */
  private[unicomplex] def startWebService(notifySender: ActorRef)(implicit context: ActorContext) = {

    val registrarRef = context.actorOf(Props[Registrar], "service-registrar")
    registrar send registrarRef
    val serviceRef = context.actorOf(Props[WebSvcActor], "web-service")
    serviceRef ! notifySender // serviceRef needs to send the notifySender an ack when it is ready.

    // create a new HttpServer using our handler tell it where to bind to
    val interface = if(Unicomplex.config getBoolean "full-address") InetAddress.getLocalHost.getCanonicalHostName
      else Unicomplex.config getString "bind-address"
    val port = Unicomplex.config getInt "bind-port"
    implicit val self = context.self
    implicit val system = context.system
    IO(Http) ! Http.Bind(serviceRef, interface, port)
    context.watch(registrarRef)
    context.watch(serviceRef)
  }

  // In very rare cases, we block. Shutdown is one where we want to make it is stopped.
  private[unicomplex] def stopWebService (implicit context: ActorContext) = {
    implicit val self = context.self
    implicit val system = context.system
    context.unwatch(registrar())
    registrar() ! PoisonPill
    IO(Http) ! Http.CloseAll
  }
}

/**
 * The Registrar receives Register and Unregister messages.
 */
private[unicomplex] class Registrar extends Actor with ActorLogging {

  val serviceRegistry = Unicomplex.serviceRegistry
  import serviceRegistry._

  class ContextsBean extends ContextsMXBean {

    override def getContexts: java.util.List[ContextInfo] = {
      import collection.JavaConversions._
      registry().map { case (ctx, Register(symName, alias, version, routeDef)) =>
        ContextInfo(ctx, routeDef.getClass.getName, symName, version)
      } .toSeq
    }
  }

  override def preStart() {
    import JMX._
    register(new ContextsBean, prefix + contextsName)
  }

  override def postStop()  {
    import JMX._
    unregister(prefix + contextsName)
  }

  //private var registry = Map.empty[String, Register]

  // CalculateRoute MUST return a function and not a value
  private def calculateRoute(tmpRegistry: Map[String, Register]) = {
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
      val localRegistry = registry()
      if (localRegistry contains routeDef.webContext)
        log.warning(s"""Web context "${routeDef.webContext}" already registered. Overriding!""")
      val tmpRegistry = localRegistry + (routeDef.webContext -> r)

      // This line is the problem. Don't pre-calculate.
      route send calculateRoute(tmpRegistry)
      registry send tmpRegistry
      log.info(s"""Web context "${routeDef.webContext}" (${routeDef.getClass.getName}) registered.""")

    case Unregister(webContext) =>
      val tmpRegistry = registry() - webContext
      route send calculateRoute(tmpRegistry)
      registry send tmpRegistry
      log.info(s"Web service route $webContext unregistered.")

    case WebServicesStarted => // Got all the service registrations for now.
      Unicomplex() ! WebServicesStarted // Just send the message onto Unicomplex after processing all registrations.
  }
}

/**
 * The main service actor.
 */
private[unicomplex] class WebSvcActor extends Actor with HttpService {

  val serviceRegistry = Unicomplex.serviceRegistry
  import serviceRegistry._

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // All RouteDefinitions should use this context.
  serviceActorContext send context

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

  def startRoutes[T](system: ActorSystem)(fn: ()=>T): T = {
    localContext.set(Option(Unicomplex(system).serviceRegistry.serviceActorContext()))
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
