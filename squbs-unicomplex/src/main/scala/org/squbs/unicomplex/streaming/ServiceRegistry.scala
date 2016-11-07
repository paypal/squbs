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

package org.squbs.unicomplex.streaming

import akka.NotUsed
import akka.actor.Actor._
import akka.actor.Status.{Failure => ActorFailure}
import akka.actor.SupervisorStrategy.Escalate
import akka.actor._
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.PathDirectives
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.pattern.pipe
import akka.stream.TLSClientAuth.{Need, Want}
import akka.stream.scaladsl.Flow
import akka.stream.{ActorMaterializer, BindFailedException}
import com.typesafe.config.Config
import org.squbs.pipeline.streaming.{PipelineExtension, PipelineSetting}
import org.squbs.unicomplex._
import org.squbs.unicomplex.streaming.StatsSupport.StatsHolder

import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
  * Akka HTTP based [[ServiceRegistryBase]] implementation.
  */
class ServiceRegistry(val log: LoggingAdapter) extends ServiceRegistryBase[Path] {

  case class ServerBindingInfo(serverBinding: Option[ServerBinding], exception: Option[Throwable] = None)

  private var serverBindings = Map.empty[String, ServerBindingInfo] // Service actor and HttpListener actor

  var listenerRoutesVar = Map.empty[String, Seq[(Path, FlowWrapper, PipelineSetting)]]

  override protected def listenerRoutes: Map[String, Seq[(Path, FlowWrapper, PipelineSetting)]] = listenerRoutesVar

  override protected def listenerRoutes_=[B](newListenerRoutes: Map[String, Seq[(B, FlowWrapper, PipelineSetting)]]): Unit =
    listenerRoutesVar = newListenerRoutes.asInstanceOf[Map[String, Seq[(Path, FlowWrapper, PipelineSetting)]]]

  override private[unicomplex] def startListener(name: String, config: Config, notifySender: ActorRef)
                                                (implicit context: ActorContext): Receive = {

    val BindConfig(interface, port, localPort, sslContext, needClientAuth) = Try { bindConfig(config) } match {
      case Success(bc) => bc
      case Failure(ex) =>
        serverBindings = serverBindings + (name -> ServerBindingInfo(None, Some(ex)))
        notifySender ! Failure(ex)
        throw ex
    }

    implicit val am = ActorMaterializer()
    import context.system

    val handler = try { FlowHandler(listenerRoutes(name), localPort) } catch { case e: Throwable =>
      serverBindings = serverBindings + (name -> ServerBindingInfo(None, Some(e)))
      log.error(s"Failed to build streaming flow handler.  System may not function properly.")
      notifySender ! Failure(e)
      throw e
    }

    val uniSelf = context.self
    import context.dispatcher

    val statsHolder = new StatsHolder
    val requestFlow = Flow[HttpRequest]
      .via(statsHolder.watchRequests())
      .via(handler.flow)
      .via(statsHolder.watchResponses())

    val bindingF = sslContext match {
      case Some(sslCtx) =>
        val httpsCtx = ConnectionContext.https(sslCtx, clientAuth = Some { if(needClientAuth) Need else Want })
        Http().bindAndHandle(requestFlow, interface, port, connectionContext = httpsCtx )

      case None => Http().bindAndHandle(requestFlow, interface, port)
    }
    bindingF pipeTo uniSelf

    {
      case sb: ServerBinding =>
        import org.squbs.unicomplex.JMX._
        JMX.register(new ServerStats(name, statsHolder), prefix + serverStats + name)
        serverBindings = serverBindings + (name -> ServerBindingInfo(Some(sb)))
        notifySender ! Ack
        uniSelf ! HttpBindSuccess
      case ActorFailure(ex) if ex.isInstanceOf[BindFailedException] =>
        serverBindings = serverBindings + (name -> ServerBindingInfo(None, Some(ex)))
        log.error(s"Failed to bind listener $name. Cleaning up. System may not function properly.")
        notifySender ! ex
        uniSelf ! HttpBindFailed
    }
  }

  override private[unicomplex] def registerContext(listeners: Iterable[String], webContext: String, servant: FlowWrapper,
                                                   ps: PipelineSetting)(implicit context: ActorContext) {

    // Calling this here just to see if it would throw an exception.
    // We do not want it to be thrown at materialization time, instead face it during startup.
    PipelineExtension(context.system).getFlow(ps)

    listeners foreach { listener =>
      val currentListenerRoutes = listenerRoutes(listener)
      val mergedListenerRoutes = merge(currentListenerRoutes, webContext, servant, ps, {
        log.warning(s"Web context $webContext already registered on $listener. Override existing registration.")
      })

      listenerRoutes = listenerRoutes + (listener -> mergedListenerRoutes)
    }
  }

  override private[unicomplex] def isListenersBound = serverBindings.size == listenerRoutes.size

  case class Unbound(sb: ServerBinding)

  override private[unicomplex] def shutdownState: Receive = {
    case Unbound(sb) =>
      serverBindings = serverBindings.filterNot {
        case (_, ServerBindingInfo(Some(`sb`), None)) => true
        case _ => false
      }
  }

  override private[unicomplex] def listenerTerminated(listenerActor: ActorRef): Unit =
    log.warning(s"Unexpected serviceRegistry.listenerTerminated(${listenerActor.toString()}) in streaming use case.")

  override private[unicomplex] def stopAll()(implicit context: ActorContext): Unit = {

    import context.{dispatcher, system}
    val uniSelf = context.self

//    Http().shutdownAllConnectionPools() andThen { case _ =>
      serverBindings foreach {
        case (name, ServerBindingInfo(Some(sb), None)) =>
          listenerRoutes(name) foreach {
            case (_, sw, _) => sw.actor ! PoisonPill
          }
          listenerRoutes = listenerRoutes - name
          sb.unbind() andThen { case _ => uniSelf ! Unbound(sb) }
          if (listenerRoutes.isEmpty) {
            Http().shutdownAllConnectionPools()
            // TODO Unregister "Listeners" JMX Bean.
          }
        // TODO Unregister "ServerStats,Listener=name" JMX Bean
        case _ =>
      }
//    }
  }

  override private[unicomplex] def isAnyFailedToInitialize: Boolean = serverBindings.values exists (_.exception.nonEmpty)

  override private[unicomplex] def isShutdownComplete: Boolean = serverBindings.isEmpty

  override protected def pathCompanion(s: String): Path = Path(s)

  override protected def pathLength(p: Path) = p.length

  override protected def listenerStateMXBean(): ListenerStateMXBean = {
    new ListenerStateMXBean {
      import scala.collection.JavaConversions._
      override def getListenerStates: java.util.List[ListenerState] = {
        serverBindings.map { case (name, ServerBindingInfo(sb, exception)) =>
          ListenerState(name, sb.map(_ => "Success").getOrElse("Failed"), exception.getOrElse("").toString)
        } .toSeq
      }
    }
  }

  override private[unicomplex] def portBindings: Map[String, Int] = {
    serverBindings map { case (name, ServerBindingInfo(Some(sb), None)) =>
      name -> sb.localAddress.getPort
    }
  }
}

private[unicomplex] sealed trait FlowSupplier { this: Actor with ActorLogging =>

  import scala.concurrent.duration._

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case e: Exception =>
        log.error(s"Received ${e.getClass.getName} with message ${e.getMessage} from ${sender().path}")
        Escalate
    }

  val flowTry: Try[Flow[HttpRequest, HttpResponse, NotUsed]]

  final def receive: Receive = {
    case FlowRequest =>
      sender() ! flowTry
      if (flowTry.isFailure) context.stop(self)
  }
}

private[unicomplex] class RouteActor(webContext: String, clazz: Class[RouteDefinition])
  extends Actor with ActorLogging with FlowSupplier {

  def actorRefFactory = context

  val routeDefTry = Try {
    RouteDefinition.startRoutes {
      WebContext.createWithContext[RouteDefinition](webContext) {
        clazz.newInstance
      }
    }
  }

  implicit val am = ActorMaterializer()

  val flowTry: Try[Flow[HttpRequest, HttpResponse, NotUsed]] = routeDefTry match {

    case Success(routeDef) =>
      context.parent ! Initialized(Success(None))

      implicit val rejectionHandler:RejectionHandler = routeDef.rejectionHandler.getOrElse(RejectionHandler.default)
      implicit val exceptionHandler:ExceptionHandler = routeDef.exceptionHandler.getOrElse(PartialFunction.empty[Throwable, Route])

      if (webContext.nonEmpty) {
        Success(PathDirectives.pathPrefix(PathMatchers.separateOnSlashes(webContext)) {routeDef.route})
      } else {
        // don't append pathPrefix if webContext is empty, won't be null due to the top check
        Success(routeDef.route)
      }

    case Failure(e) =>
      log.error(e, s"Error instantiating route from {}: {}", clazz.getName, e)
      context.parent ! Initialized(Failure(e))
      Failure(e)
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

trait RouteDefinition extends Directives {
  protected implicit final val context: ActorContext = RouteDefinition.localContext.get.get
  implicit final lazy val self = context.self

  def route: Route

  def rejectionHandler: Option[RejectionHandler] = None

  def exceptionHandler: Option[ExceptionHandler] = None
}

class RejectRoute extends RouteDefinition {

  val route: Route = reject
}

private[unicomplex] case object FlowRequest
private[unicomplex] case class FlowNotAvailable(flowClass: String)


/**
  * The FlowActor only hosts the FlowDefinition and hands out the Flow. It gives an
  * ActorContext to the FlowDefinition but does no other functionality of an actor.
  * @param webContext The web context held by this FlowActor
  * @param clazz The FlowDefinition to be instantiated.
  */
private[unicomplex] class FlowActor(webContext: String, clazz: Class[FlowDefinition])
  extends Actor with ActorLogging with FlowSupplier {

  val flowDefTry = Try {
    FlowDefinition.startFlow {
      WebContext.createWithContext[FlowDefinition](webContext) {
        clazz.newInstance
      }
    }
  }

  val flowTry: Try[Flow[HttpRequest, HttpResponse, NotUsed]] = flowDefTry match {

    case Success(flowDef) =>
      context.parent ! Initialized(Success(None))
      Success(flowDef.flow)

    case Failure(e) =>
      log.error(e, s"Error instantiating flow from {}: {}", clazz.getName, e)
      context.parent ! Initialized(Failure(e))
      Failure(e)
  }
}

object FlowDefinition {
  private[unicomplex] val context = new ThreadLocal[Option[ActorContext]] {
    override def initialValue(): Option[ActorContext] = None
  }

  def startFlow[T](fn: => T)(implicit refFactory: ActorContext): T = {
    context.set(Some(refFactory))
    val f = fn
    context.set(None)
    f
  }
}

trait FlowDefinition {
  protected implicit final val context: ActorContext = FlowDefinition.context.get.get

  def flow: Flow[HttpRequest, HttpResponse, NotUsed]
}
