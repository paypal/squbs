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
import org.apache.pekko.http.scaladsl.server._
import org.apache.pekko.pattern._
import org.apache.pekko.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.Timeouts._

import scala.concurrent.Await

object RouteActorHandlerSpec {

  val classPaths = Array(getClass.getClassLoader.getResource("classpaths/RouteActorHandler").getPath)

  val config = ConfigFactory.parseString(
    s"""
       |default-listener.bind-port = 0
       |squbs {
       |  actorsystem-name = RouteActorHandlerSpec
       |  ${JMX.prefixConfig} = true
       |}
    """.stripMargin
  )

  val boot = UnicomplexBoot(config)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()
}

class RouteActorHandlerSpec extends TestKit(
  RouteActorHandlerSpec.boot.actorSystem) with AnyFlatSpecLike with BeforeAndAfterAll with Matchers {

  val portBindings = Await.result((Unicomplex(system).uniActor ? PortBindings).mapTo[Map[String, Int]], awaitMax)
  val port = portBindings("default-listener")

  override def afterAll(): Unit = {
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
