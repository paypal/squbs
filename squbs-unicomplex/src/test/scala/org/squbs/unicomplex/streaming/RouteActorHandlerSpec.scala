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
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.Timeouts._
import org.squbs.unicomplex.{Unicomplex, UnicomplexBoot, JMX}

import scala.concurrent.Await

object RouteActorHandlerSpec {

  val classPaths = Array(getClass.getClassLoader.getResource("classpaths/streaming/RouteActorHandler").getPath)

  val (_, _, port) = temporaryServerHostnameAndPort()

  val config = ConfigFactory.parseString(
    s"""
       |default-listener.bind-port = $port
       |squbs {
       |  actorsystem-name = streaming-routeActorHandlerSpec
       |  ${JMX.prefixConfig} = true
       |  experimental-mode-on = true
       |}
    """.stripMargin
  )

  val boot = UnicomplexBoot(config)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()
}

class RouteActorHandlerSpec extends TestKit(
  RouteActorHandlerSpec.boot.actorSystem) with FlatSpecLike with BeforeAndAfterAll with Matchers {

  implicit val am = ActorMaterializer()

  val port = system.settings.config getInt "default-listener.bind-port"

  override def afterAll() {
    Unicomplex(system).uniActor ! GracefulStop
  }

  "Rejection handler" should "be applied to the route actor" in {
    Await.result(entityAsString(s"http://127.0.0.1:$port/ctx/reject"), awaitMax) should be("rejected")
  }

  "Exception handler" should "be applied to the route actor" in {
    Await.result(entityAsString(s"http://127.0.0.1:$port/ctx/exception"), awaitMax) should be("exception")
  }
}

class Service extends RouteDefinition {

  override def rejectionHandler: Option[RejectionHandler] = Some(RejectionHandler.newBuilder().handle {
    case ServiceRejection => complete("rejected")
  }.result())

  override def exceptionHandler: Option[ExceptionHandler] = Some(ExceptionHandler {
    case _: ServiceException => complete("exception")
  })

  override def route: Route = path("reject") {
    reject(ServiceRejection)
  } ~ path("exception") {
    ctx =>
      throw new ServiceException
  }

  object ServiceRejection extends Rejection

  class ServiceException extends Exception
}