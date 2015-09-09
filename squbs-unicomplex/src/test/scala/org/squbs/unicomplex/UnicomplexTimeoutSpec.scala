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
import org.scalatest._
import org.scalatest.concurrent.AsyncAssertions
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.dummysvcactor.RegisterTimeoutHandler
import spray.can.Http
import spray.http._
import spray.util.Utils

import scala.util.Try

object UnicomplexTimeoutSpec {

  val dummyJarsDir = getClass.getClassLoader.getResource("classpaths").getPath

  val classPaths = Array(
    "DummySvcActor"
  ) map (dummyJarsDir + "/" + _)

  val (_, port) = Utils.temporaryServerHostnameAndPort()

  val aConfig = ConfigFactory.parseString(
    s"""
       |squbs {
       |  actorsystem-name = unicomplexTimeoutSpec
       |  ${JMX.prefixConfig} = true
       |}
       |default-listener {
       |  bind-port = $port
       |}
       |spray.can.server {
       |  request-timeout = 5s
       |}
     """.stripMargin)

  val boot = UnicomplexBoot(aConfig)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()

}

class UnicomplexTimeoutSpec extends TestKit(UnicomplexTimeoutSpec.boot.actorSystem) with ImplicitSender
    with WordSpecLike with Matchers with BeforeAndAfterAll with AsyncAssertions {

  implicit val timeout: akka.util.Timeout =
    Try(System.getProperty("test.timeout").toLong) map { millis =>
      akka.util.Timeout(millis, TimeUnit.MILLISECONDS)
    } getOrElse Timeouts.askTimeout

  val port = system.settings.config getInt "default-listener.bind-port"

  implicit val executionContext = system.dispatcher

  override def afterAll() {
    Unicomplex(system).uniActor ! GracefulStop
  }

  "Unicomplex" must {

    "Cause a timeout event" in {
      system.settings.config getString "spray.can.server.request-timeout" should be ("5s")
      system.actorSelection("/user/DummySvcActor/dummysvcactor-DummySvcActor-handler") ! RegisterTimeoutHandler
      val path = "/dummysvcactor/timeout"
      IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port$path"))
      within(timeout.duration) {
        val timedOut = expectMsgType[Timedout]
        timedOut.request should matchPattern { case req: HttpRequest if req.uri.path.toString === path => }
      }
    }
  }
}
