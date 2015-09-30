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
package org.squbs.unicomplex

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.io.IO
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import org.squbs.lifecycle.GracefulStop
import spray.can.Http
import spray.http._
import spray.routing.Directives._
import spray.util.Utils

import scala.util.Try

object RouteDefinitionSpec {

  val dummyJarsDir = getClass.getClassLoader.getResource("classpaths").getPath

  val classPaths = Array(
    "RouteDefinitionSpec"
  ) map (dummyJarsDir + "/" + _)

  val (_, port) = Utils.temporaryServerHostnameAndPort()

  val config = ConfigFactory.parseString(
    s"""
       |squbs {
       |  actorsystem-name = routeDef
       |  ${JMX.prefixConfig} = true
       |}
       |default-listener.bind-port = $port
    """.stripMargin
  )

  val boot = UnicomplexBoot(config)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()

}

class TestRouteDefinition extends RouteDefinition {
  var count = 0
  def route =
    path ("first") {
      get {
        count += 1 // executed on build time
        complete{count.toString}
      }
    } ~ path("second") {
      get {
        count += 1 // executed on build time
        complete{count.toString}
      }
    } ~ path("third") {
      get {
        complete {
          count += 1 // executed on invocation time
          count.toString
        }
      }
    }

}


class RouteDefinitionSpec extends TestKit(
  RouteDefinitionSpec.boot.actorSystem) with FlatSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll {

  implicit val timeout: akka.util.Timeout =
    Try(System.getProperty("test.timeout").toLong) map { millis =>
      akka.util.Timeout(millis, TimeUnit.MILLISECONDS)
    } getOrElse Timeouts.askTimeout

  val port = system.settings.config getInt "default-listener.bind-port"

  override def afterAll() {
    Unicomplex(system).uniActor ! GracefulStop
  }

  "The test actor" should "return correct count value" in {
    // the count should be 2 due to two statements in first and second build time
    IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/routedef/first"))
    within(timeout.duration) {
      val response = expectMsgType[HttpResponse]
      response.status should be(StatusCodes.OK)
      response.entity.asString should be("2")
    }

    IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/routedef/second"))
    within(timeout.duration) {
      val response = expectMsgType[HttpResponse]
      response.status should be(StatusCodes.OK)
      response.entity.asString should be("2")
    }

    // should be increase 1 on hitting third
    IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/routedef/third"))
    within(timeout.duration) {
      val response = expectMsgType[HttpResponse]
      response.status should be(StatusCodes.OK)
      response.entity.asString should be("3")
    }

    IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/routedef/third"))
    within(timeout.duration) {
      val response = expectMsgType[HttpResponse]
      response.status should be(StatusCodes.OK)
      response.entity.asString should be("4")
    }

    IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/routedef/second"))
    within(timeout.duration) {
      val response = expectMsgType[HttpResponse]
      response.status should be(StatusCodes.OK)
      response.entity.asString should be("4")
    }
  }
}
