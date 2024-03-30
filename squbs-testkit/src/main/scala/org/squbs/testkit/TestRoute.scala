/*
 * Copyright 2017 PayPal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.squbs.testkit

import org.apache.pekko.actor.{Actor, ActorSystem}
import org.apache.pekko.http.scaladsl.server.directives.PathDirectives._
import org.apache.pekko.http.scaladsl.server.{ExceptionHandler, RejectionHandler, Route}
import org.apache.pekko.http.scaladsl.settings.RoutingSettings
import org.apache.pekko.testkit.TestActorRef
import org.squbs.unicomplex.{RouteDefinition, WithActorContext, WithWebContext}

import scala.reflect.ClassTag

/**
 * The TestRoute is used for creating a Route to be used with the Pekko Http TestKit, from a RouteDefinition.
 * While a RouteDefinition is easy to define, it is actually not easy to instantiate.
 */
object TestRoute {

  /**
   * Creates the Route to be used in Pekko Http TestKit.
   * @param system The ActorSystem. This will be supplied implicitly if using the ScalaTestRouteTest trait from Pekko Http.
   * @tparam T The RouteDefinition to be tested
   * @return The Route to be used for testing.
   */
  def apply[T <: RouteDefinition: ClassTag](implicit system: ActorSystem): Route = apply[T]("")

  /**
   * Creates the Route to be used in Pekko Http TestKit.
   * @param webContext The web context to simulate
   * @param system The ActorSystem. This will be supplied implicitly if using the ScalaTestRouteTest trait from Pekko Http.
   * @tparam T The RouteDefinition to be tested
   * @return The Route to be used for testing.
   */
  def apply[T <: RouteDefinition: ClassTag](webContext: String)(implicit system: ActorSystem): Route = {
    val clazz = implicitly[ClassTag[T]].runtimeClass
    implicit val actorContext = TestActorRef[TestRouteActor].underlyingActor.context
    val routeDef =
      WithWebContext(webContext) {
        WithActorContext {
          clazz.asSubclass(classOf[RouteDefinition]).newInstance()
        }
      }

    implicit val routingSettings = RoutingSettings(system.settings.config)
    implicit val rejectionHandler:RejectionHandler = routeDef.rejectionHandler.getOrElse(RejectionHandler.default)
    implicit val exceptionHandler:ExceptionHandler = routeDef.exceptionHandler.orNull

    if (webContext.nonEmpty) pathPrefix(separateOnSlashes(webContext)) { Route.seal(routeDef.route) }
    else { Route.seal(routeDef.route) }
  }

  private[testkit] class TestRouteActor extends Actor {
    def receive = {
      case _ =>
    }
  }
}
