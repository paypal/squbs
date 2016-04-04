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
package org.squbs.unicomplex.streaming

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.Timeouts._
import org.squbs.unicomplex.{Unicomplex, UnicomplexBoot, JMX}

import scala.concurrent.Await

object RouteDefinitionSpec {

  val dummyJarsDir = getClass.getClassLoader.getResource("classpaths/streaming").getPath

  val classPaths = Array(
    "RouteDefinitionSpec"
  ) map (dummyJarsDir + "/" + _)

  val (_, _, port) = temporaryServerHostnameAndPort()

  val config = ConfigFactory.parseString(
    s"""
       |squbs {
       |  actorsystem-name = streaming-routeDef
       |  ${JMX.prefixConfig} = true
       |  experimental-mode-on = true
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
  def route: Route =
    path ("first") {
      get {
        count += 1
        complete{count.toString}
      }
    } ~ path("second") {
      get {
        count += 1
        complete{count.toString}
      }
    } ~ path("third") {
      get {
        complete {
          count += 1
          count.toString
        }
      }
    }

}

class RouteDefinitionSpec extends TestKit(
  RouteDefinitionSpec.boot.actorSystem) with FlatSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll {

  implicit val am = ActorMaterializer()

  val port = system.settings.config getInt "default-listener.bind-port"

  override def afterAll() {
    Unicomplex(system).uniActor ! GracefulStop
  }

  "The test actor" should "return correct count value" in {
    // The behaviour is different than Spray.  Not caching anymore.
    Await.result(entityAsString(s"http://127.0.0.1:$port/routedef/first"), awaitMax) should be ("1")
    Await.result(entityAsString(s"http://127.0.0.1:$port/routedef/first"), awaitMax) should be ("2")
    Await.result(entityAsString(s"http://127.0.0.1:$port/routedef/second"), awaitMax) should be ("3")
    Await.result(entityAsString(s"http://127.0.0.1:$port/routedef/third"), awaitMax) should be ("4")
    Await.result(entityAsString(s"http://127.0.0.1:$port/routedef/third"), awaitMax) should be ("5")
  }
}
