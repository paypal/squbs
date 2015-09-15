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

import akka.actor.ActorSystem
import akka.io.IO
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.Timeouts.awaitMax
import spray.can.Http
import spray.http._
import spray.routing.Directives._
import spray.routing.Route
import spray.util.Utils

object RootCtxRouteSpec{
  /*
  cube-name = org.squbs.unicomplex.test.RootRoute
  cube-version = "0.0.1"
  squbs-services = [
      {
          class-name = org.squbs.unicomplex.RootRoute
          web-context = ""
      }
  ]
   */
  val classPaths = Array(getClass.getClassLoader.getResource("classpaths/RootCtxRoute").getPath)

  val (_, port) = Utils.temporaryServerHostnameAndPort()

  val config = ConfigFactory.parseString(
    s"""
       |default-listener.bind-port = $port
       |squbs {
       |  actorsystem-name = rootCtxRouteSpec
       |  ${JMX.prefixConfig} = true
       |}
    """.stripMargin
  )

  val boot = UnicomplexBoot(config)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()
}

class RootCtxRouteSpec extends TestKit(
  RootCtxRouteSpec.boot.actorSystem) with FlatSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll {

  val port = system.settings.config getInt "default-listener.bind-port"

  implicit val executionContext = system.dispatcher

  override def afterAll() {
    Unicomplex(system).uniActor ! GracefulStop
  }

  "Route" should "handle request with empty web-context" in {
    IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/ping"))
    within(awaitMax) {
      val response = expectMsgType[HttpResponse]
      response.status should be(StatusCodes.OK)
      response.entity.asString should be("pong")
    }
  }
}

class RootRoute extends RouteDefinition {
  override def route: Route = path("ping") {
    complete{"pong"}
  }
}
