/*
 * Copyright 2015 PayPal
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

package org.squbs.testkit.streaming

import akka.actor.{Actor, ActorSystem}
import akka.http.scaladsl.server.Route
import akka.testkit.TestActorRef
import akka.http.scaladsl.server.directives.PathDirectives._
import org.squbs.unicomplex.WebContext
import org.squbs.unicomplex.streaming.RouteDefinition

import scala.reflect.ClassTag

/**
 * The TestRoute is used for creating a Route to be used with the Akka Http TestKit, from a RouteDefinition.
 * While a RouteDefinition is easy to define, it is actually not easy to instantiate.
 */
object TestRoute {

  /**
   * Creates the Route to be used in Akka Http TestKit.
   * @param system The ActorSystem. This will be supplied implicitly if using the ScalaTestRouteTest trait from Akka Http.
   * @tparam T The RouteDefinition to be tested
   * @return The Route to be used for testing.
   */
  def apply[T <: RouteDefinition: ClassTag](implicit system: ActorSystem): Route = apply[T]("")

  /**
   * Creates the Route to be used in Akka Http TestKit.
   * @param webContext The web context to simulate
   * @param system The ActorSystem. This will be supplied implicitly if using the ScalaTestRouteTest trait from Akka Http.
   * @tparam T The RouteDefinition to be tested
   * @return The Route to be used for testing.
   */
  def apply[T <: RouteDefinition: ClassTag](webContext: String)(implicit system: ActorSystem): Route = {
    val clazz = implicitly[ClassTag[T]].runtimeClass
    implicit val actorContext = TestActorRef[TestRouteActor].underlyingActor.context
    val routeDef = WebContext.createWithContext(webContext) {
      RouteDefinition.startRoutes {
        clazz.asSubclass(classOf[RouteDefinition]).newInstance()
      }
    }
    if (webContext.length > 0) pathPrefix(separateOnSlashes(webContext)) {routeDef.route}
    else routeDef.route
  }

  private class TestRouteActor extends Actor {
    def receive = {
      case _ =>
    }
  }
}
