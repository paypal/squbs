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
package org.squbs.testkit.japi

import org.apache.pekko.http.javadsl.model.HttpRequest
import org.apache.pekko.http.javadsl.server.Route
import org.apache.pekko.http.javadsl.testkit.{RouteTest, TestRoute, TestRouteResult}
import org.apache.pekko.testkit.TestActorRef
import org.squbs.testkit.TestRoute.TestRouteActor
import org.squbs.unicomplex.{AbstractRouteDefinition, BuildRoute, WithActorContext, WithWebContext}

trait RouteDefinitionTest { this: RouteTest =>
  def testRoute[T <: AbstractRouteDefinition](clazz: Class[T]): TestRoute = testRoute("", clazz)

  def testRoute[T <: AbstractRouteDefinition](webContext: String, clazz: Class[T]): TestRoute = {
    implicit val actorContext = TestActorRef[TestRouteActor].underlyingActor.context
    val routeDef =
      WithWebContext(webContext) {
        WithActorContext {
          clazz.asSubclass(classOf[AbstractRouteDefinition]).newInstance()
        }
      }

    new TestRoute {
      val underlying: Route = BuildRoute(routeDef, webContext)

      def run(request: HttpRequest): TestRouteResult = runRoute(underlying, request)

      def runWithRejections(request: HttpRequest): TestRouteResult = runRouteUnSealed(underlying, request)

      def runClientServer(request: HttpRequest): TestRouteResult = runRoute(underlying, request)
    }
  }
}