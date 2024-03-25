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

import java.util.{Optional, function => jf}
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.{Actor, ActorContext, ActorLogging}
import org.apache.pekko.http.javadsl.model.{HttpRequest, HttpResponse}
import org.apache.pekko.http.javadsl.server._
import org.apache.pekko.http.javadsl.server.directives.{PathDirectives, RouteAdapter}
import org.apache.pekko.http.scaladsl.{model => sm, server => ss}
import org.apache.pekko.stream.javadsl.Flow
import org.apache.pekko.stream.{ActorMaterializer, Materializer, scaladsl => sd}
import org.squbs.pipeline.RequestContext

import scala.util.{Failure, Success, Try}

/**
  * Java API. The Abstract FlowDefinition is inherited to declare the Flow in the Java API.
  */
abstract class AbstractFlowDefinition extends WebContext {
  protected final val context: ActorContext = WithActorContext.localContext.get.get

  def flow: Flow[HttpRequest, HttpResponse, NotUsed]
}

/**
  * The JavaFlowActor only hosts the AbstractFlowDefinition and hands out the Flow. It gives an
  * ActorContext to the FlowDefinition but does no other functionality of an actor.
  * @param webContext The web context held by this FlowActor
  * @param clazz The FlowDefinition to be instantiated.
  */
private[unicomplex] class JavaFlowActor(webContext: String, clazz: Class[AbstractFlowDefinition])
  extends Actor with ActorLogging with FlowSupplier {

  val flowDefTry: Try[AbstractFlowDefinition] = Try {
    WithActorContext {
      WithWebContext(webContext) {
        clazz.newInstance
      }
    }
  }

  val flowTry: Try[Materializer => sd.Flow[RequestContext, RequestContext, NotUsed]] = flowDefTry match {

    case Success(flowDef) =>
      context.parent ! Initialized(Success(None))
      val reqFlow = flowDef.flow.asScala.asInstanceOf[sd.Flow[sm.HttpRequest, sm.HttpResponse, NotUsed]]
      Success((materializer: Materializer) => {
        implicit val mat = materializer
        RequestContextFlow(reqFlow)
      })

    case Failure(e) =>
      log.error(e, s"Error instantiating flow from {}: {}", clazz.getName, e)
      context.parent ! Initialized(Failure(e))
      Failure(e)
  }
}


/**
  * Java API. The Abstract RouteDefinition is inherited to declare the Route in the Java API.
  */
abstract class AbstractRouteDefinition extends AllDirectives with WebContext {
  protected final val context: ActorContext = WithActorContext.localContext.get.get

  @throws(classOf[Exception])
  def route: Route

  def rejectionHandler: Optional[RejectionHandler] = Optional.empty()

  def exceptionHandler: Optional[ExceptionHandler] = Optional.empty()

}

private[unicomplex] class JavaRouteActor(webContext: String, clazz: Class[AbstractRouteDefinition])
  extends Actor with ActorLogging with FlowSupplier {

  val routeDefTry: Try[AbstractRouteDefinition] = Try {
    WithActorContext {
      WithWebContext(webContext) {
        clazz.newInstance
      }
    }
  }

  val routeTry: Try[Route] = routeDefTry match {

    case Success(routeDef) =>
      context.parent ! Initialized(Success(None))
      Success(BuildRoute(routeDef, webContext))

    case Failure(e) =>
      log.error(e, s"Error instantiating route from {}: {}", clazz.getName, e)
      context.parent ! Initialized(Failure(e))
      Failure(e)
  }

  val flowTry: Try[Materializer => sd.Flow[RequestContext, RequestContext, NotUsed]] =
    routeTry.map {
      case r: RouteAdapter =>
        materializer: Materializer =>
          implicit val mat = materializer
          RequestContextFlow(r.delegate)
      case r => // There should be NO java Route that is not a RouteAdapter. This code is to catch such cases.
        // $COVERAGE-OFF$
        materializer: Materializer =>
          implicit val mat = materializer
          val requestFlow = r.flow(context.system, materializer).asScala
            .asInstanceOf[sd.Flow[sm.HttpRequest, sm.HttpResponse, NotUsed]]
          RequestContextFlow(requestFlow)
        // $COVERAGE-ON$
    }
}

private[squbs] object BuildRoute extends PathDirectives {

  /**
    * Object used for building the final Java route.
    * Implementation note: We do not use closure syntax inside this function as we want the code to work with both
    * Scala 2.11 and Scala 2.12. Scala closures will only be compatible with Java 8 closures by Scala 2.12.
    * @param routeDef The user-provided route definition
    * @param webContext The web context of this route, or empty string if none
    * @return The final wrapped route
    */
  def apply(routeDef: AbstractRouteDefinition, webContext: String):
      Route = {

    val routeSupplier = new jf.Supplier[Route] {
      override def get = routeDef.route
    }

    val routeWithContext =
      if (webContext.nonEmpty) {
        pathPrefix(PathMatchers.separateOnSlashes(webContext), routeSupplier)
      } else {
        routeDef.route
      }


    val routeWithContextSupplier = new jf.Supplier[Route] {
      override def get = routeWithContext
    }

    val routeWithEH = routeDef.exceptionHandler.map[Route](new jf.Function[ExceptionHandler, Route] {
      override def apply(t: ExceptionHandler): Route =
        handleExceptions(t, routeWithContextSupplier)
    }).orElse(routeWithContext)

    val routeWithEHSupplier = new jf.Supplier[Route] {
      override def get = routeWithEH
    }

    routeDef.rejectionHandler.map[Route](new jf.Function[RejectionHandler, Route] {
      override def apply(r: RejectionHandler): Route =
        handleRejections(r, routeWithEHSupplier)
    }).orElse(routeWithEH)
  }
}
