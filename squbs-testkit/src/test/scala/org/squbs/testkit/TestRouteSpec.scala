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

import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.unicomplex.{RouteDefinition, WebContext}

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

class MyTestRoute2 extends RouteDefinition {

  val route =
    path("foo" / "bar" / Segment.?) { segment =>
      complete {
        segment match {
          case Some(s) => s"Hello, got $s"
          case None => "Hello, got nothing"
        }
      }
    } ~
    path("foo" / "bar") {
      complete {
        "Not even a trailing slash!"
      }
    } ~
    path("foo" / "baz" / IntNumber.?) { num =>
      complete {
        num match {
          case Some(n) => s"Hello, got half of ${n * 2}"
          case None => "Hello, got no int"
        }
      }
    } ~
    path ("foo" / "baz") {
      complete {
        "No trailing slash either"
      }
    }
}

class TestRouteSpec extends AnyFlatSpecLike with Matchers with ScalatestRouteTest {

  it should "respond to string and int segments" in {
    val route = TestRoute[MyTestRoute2]
    Get("/foo/bar/xyz") ~> route ~> check {
      responseAs[String] should be ("Hello, got xyz")
    }
    Get("/foo/bar/") ~> route ~> check {
      responseAs[String] should be ("Hello, got nothing")
    }
    Get("/foo/bar") ~> route ~> check {
      responseAs[String] should be ("Not even a trailing slash!")
    }
    Get("/foo/baz/5") ~> route ~> check {
      responseAs[String] should be ("Hello, got half of 10")
    }
    Get("/foo/baz/") ~>  route ~> check {
      responseAs[String] should be ("Hello, got no int")
    }
    Get("/foo/baz") ~> route ~> check {
      responseAs[String] should be ("No trailing slash either")
    }
  }

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
