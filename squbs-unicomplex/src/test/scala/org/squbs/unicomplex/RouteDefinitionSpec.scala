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
package org.squbs.unicomplex

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.{ErrorInfo, IllegalRequestException, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.pattern._
import org.apache.pekko.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.Timeouts._

import scala.concurrent.Await
import scala.concurrent.duration._

object RouteDefinitionSpec {

  val dummyJarsDir = getClass.getClassLoader.getResource("classpaths").getPath

  val classPaths = Array(
    "RouteDefinitionSpec"
  ) map (dummyJarsDir + "/" + _)

  val config = ConfigFactory.parseString(
    s"""
       |squbs {
       |  actorsystem-name = RouteDefinitionSpec
       |  ${JMX.prefixConfig} = true
       |}
       |default-listener.bind-port = 0
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
    } ~ path("illegalrequest") {
      get {
        throw new IllegalRequestException(new ErrorInfo("Test illegal request", "This is a test"), StatusCodes.Locked)
      }
    } ~ path("exception") {
      get {
        throw new IllegalStateException("Default ExceptionHandler Test")
      }
    }
}

class RouteDefinitionSpec extends TestKit(
  RouteDefinitionSpec.boot.actorSystem) with AnyFlatSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll
  with ScalaFutures {

  implicit val pc = PatienceConfig(1.minute)

  val portBindings = Await.result((Unicomplex(system).uniActor ? PortBindings).mapTo[Map[String, Int]], awaitMax)
  val port = portBindings("default-listener")

  override def afterAll(): Unit = {
    Unicomplex(system).uniActor ! GracefulStop
  }

  "The test route" should "return correct count value" in {
    // The behaviour is different than Spray.  Not caching anymore.
    entityAsString(s"http://127.0.0.1:$port/routedef/first").futureValue shouldBe "1"
    entityAsString(s"http://127.0.0.1:$port/routedef/first").futureValue shouldBe "2"
    entityAsString(s"http://127.0.0.1:$port/routedef/second").futureValue shouldBe "3"
    entityAsString(s"http://127.0.0.1:$port/routedef/third").futureValue shouldBe "4"
    entityAsString(s"http://127.0.0.1:$port/routedef/third").futureValue shouldBe "5"
  }

  "The test route" should "return specified exception and error message" in {
    val response = get(s"http://127.0.0.1:$port/routedef/illegalrequest").futureValue
    response.status shouldBe StatusCodes.Locked
    extractEntityAsString(response).futureValue shouldBe "Test illegal request"
  }

  "The test route" should "return InternalServerError and messages on server exception" in {
    val response = get(s"http://127.0.0.1:$port/routedef/exception").futureValue
    response.status shouldBe StatusCodes.InternalServerError
    extractEntityAsString(response).futureValue shouldBe StatusCodes.InternalServerError.defaultMessage
  }
}
