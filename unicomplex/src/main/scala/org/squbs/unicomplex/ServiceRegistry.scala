package org.squbs.unicomplex

import concurrent.duration._

import akka.io.IO
import akka.event.Logging
import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Props}
import akka.agent.Agent
import spray.can.Http
import spray.http.MediaTypes
import spray.routing._
import Directives._
import MediaTypes._
import spray.routing.{HttpService, RequestContext}

import Unicomplex._

case class Register(routeDef: RouteDefinition)
case class Unregister(key: String)

object ServiceRegistry {

  private[unicomplex] val route = Agent[Route](null)
  private[unicomplex] val registrar = Agent[ActorRef](null)
  private[unicomplex] val updateDue = Agent[Boolean](true)
  private[unicomplex] val serviceActorContext = Agent[ActorContext](null)
  
  /**
   * The Registrar receives Register and Unregister messages.
   */
  private[unicomplex] class Registrar extends Actor with ActorLogging {

    private var registry = Map.empty[String, RouteDefinition]
    
    // CalculateRoute MUST return a function and not a value
    private def calculateRoute(tmpRegistry: Map[String, RouteDefinition]) = tmpRegistry
       .map{ case (webContext, routeDef) => pathPrefix(webContext) { routeDef.route } }
       .reduceLeft(_ ~ _)
    
    def receive = {
      case Register(routeDef) =>
        if (registry contains routeDef.webContext)
          log.warning(s"""Web context "${routeDef.webContext}" already registered. Overriding!""")
        val tmpRegistry = registry + (routeDef.webContext -> routeDef)
        
        // This line is the problem. Don't pre-calculate.
        route send calculateRoute(tmpRegistry)
        updateDue send true
        registry = tmpRegistry
        log.info(s"""Web context "${routeDef.webContext}" (${routeDef.getClass().getName()}) registered.""")
      case Unregister(webContext) =>
        val tmpRegistry = registry - webContext
        route send calculateRoute(tmpRegistry)
        updateDue send true
        registry = tmpRegistry 
        log.info(s"Web service route ${webContext} unregistered.")
    }
  }
  
  /**
   * The main service actor.
   */
  private[unicomplex] class WebSvcActor extends Actor with HttpService {

    // the HttpService trait defines only one abstract member, which
    // connects the services environment to the enclosing actor or test
    def actorRefFactory = context
    
    // All RouteDefinitions should use this context.
    serviceActorContext send context
  
    // this actor only runs our route, but you could add
    // other things here, like request stream processing
    // or timeout handling    
    def receive = runRoute(dynamic {route()})
//    def receive = { case _ =>
//      val reload = updateDue()
//      dynamicIf(reload) { runRoute(route()) }
//    }
  }

  /**
   * Starts the web service. This should be called from the Unicomplex actor
   * upon seeing the first service registration.
   */
  private[unicomplex] def startWebService(implicit context: ActorContext) = {
          
      val registrarRef = context.actorOf(Props[Registrar], "service-registrar")
      registrar send registrarRef
      val serviceRef = context.actorOf(Props[WebSvcActor], "web-service")
      // create a new HttpServer using our handler tell it where to bind to
      IO(Http) ! Http.Bind(serviceRef, interface = "localhost", port = 8080)
      context.watch(registrarRef)
      context.watch(serviceRef)
  }
}

trait RouteDefinition {
  protected implicit final def context: ActorContext = ServiceRegistry.serviceActorContext()
  val webContext: String
  def route: Route
}

/**
 * Other media types beyond what Spray supports.
 */
object MediaTypeExt {
  
  val `text/event-stream` = MediaTypes.register(
      new CustomMediaType("text", "event-stream", compressible = true))

}
