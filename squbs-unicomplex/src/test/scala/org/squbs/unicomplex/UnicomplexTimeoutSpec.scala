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

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest._
import org.scalatest.concurrent.AsyncAssertions
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.Timeouts._

import scala.concurrent.Await

object UnicomplexTimeoutSpec {

  val dummyJarsDir = getClass.getClassLoader.getResource("classpaths").getPath

  val classPaths = Array(
    "DummySvcActor"
  ) map (dummyJarsDir + "/" + _)

  val aConfig = ConfigFactory.parseString(
    s"""
       |squbs {
       |  actorsystem-name = UnicomplexTimeoutSpec
       |  ${JMX.prefixConfig} = true
       |}
       |default-listener {
       |  bind-port = 0
       |}
       |akka.http.server {
       |  request-timeout = 3s
       |}
     """.stripMargin)

  val boot = UnicomplexBoot(aConfig)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()

}

class UnicomplexTimeoutSpec extends TestKit(UnicomplexTimeoutSpec.boot.actorSystem) with ImplicitSender
    with WordSpecLike with Matchers with BeforeAndAfterAll with AsyncAssertions {

  implicit val am = ActorMaterializer()
  import akka.pattern.ask
  val port = Await.result((Unicomplex(system).uniActor ? PortBindings).mapTo[Map[String, Int]], awaitMax)("default-listener")

  override def afterAll() {
    Unicomplex(system).uniActor ! GracefulStop
  }

  "Unicomplex" must {

    "Cause a timeout event" in {
      system.settings.config getString "akka.http.server.request-timeout" should be ("3s")
      val response = Await.result(get(s"http://127.0.0.1:$port/dummysvcactor/timeout"), awaitMax)
      // TODO This test is useless to me..  Need to explore how we can intervene with timeouts..  Do we need to ?
      // There may be scenarios, where we may want to do some work when a timeout happens..  So, having a hook
      // would be useful..
      response.status should be (StatusCodes.ServiceUnavailable)
    }
  }
}
