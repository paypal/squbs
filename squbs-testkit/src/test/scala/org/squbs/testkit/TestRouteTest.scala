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

package org.squbs.testkit

import org.scalatest.{Matchers, FlatSpecLike}
import org.squbs.unicomplex.{WebContext, RouteDefinition}
import spray.routing.Directives._
import spray.testkit.ScalatestRouteTest

class MyRoute extends RouteDefinition {

  val route =
    path("ping") {
      get {
        complete {
          "pong"
        }
      }
    }
}

class MyRouteWithContext extends RouteDefinition with WebContext {

  val route =
    path("ping") {
      get {
        complete {
          s"pong from $webContext"
        }
      }
    }
}

class TestRouteTest extends FlatSpecLike with Matchers with ScalatestRouteTest {

  it should "return pong on a ping" in {
    val route = TestRoute[MyRoute]
    Get("/ping") ~> route ~> check {
      responseAs[String] should be ("pong")
    }
  }

  it should "return pong from nothing on a ping" in {
    val route = TestRoute[MyRouteWithContext]
    Get("/ping") ~> route ~> check {
      responseAs[String] should be ("pong from ")
    }
  }

  it should "return pong from context on a ping" in {
    val route = TestRoute[MyRouteWithContext]("test")
    Get("/test/ping") ~> route ~> check {
      responseAs[String] should be ("pong from test")
    }
  }
}
