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
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest._
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex._
import org.squbs.unicomplex.Timeouts._

import scala.concurrent.Await
import scala.language.postfixOps

object UnicomplexTestModeOnSpec {

  val dummyJarsDir = getClass.getClassLoader.getResource("classpaths/streaming").getPath

  val classPaths = Array(
    "DummySvc"
  ) map (dummyJarsDir + "/" + _)

  val config = ConfigFactory.parseString(
    s"""
       |squbs {
       |  actorsystem-name = streaming-UnicomplexTestModeSpec
       |  ${JMX.prefixConfig} = true
       |  experimental-mode-on = true
       |}
       |
       |default-listener.bind-port = 0
    """.stripMargin
  )

  val boot = UnicomplexBoot(config)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()

}

class UnicomplexTestModeOnSpec extends TestKit(UnicomplexTestModeOnSpec.boot.actorSystem) with ImplicitSender
  with FlatSpecLike with Matchers with BeforeAndAfterAll  {

  import akka.pattern.ask

  val portBindings = Await.result((Unicomplex(system).uniActor ? PortBindings).mapTo[Map[String, Int]], awaitMax)

  override def afterAll() {
    Unicomplex(system).uniActor ! GracefulStop
  }

  "Unicomplex" should "let the system pick the port" in {
    portBindings("default-listener") should not be(8080)
    portBindings("default-listener") should not be(13000) // bind-port specified in src/test/resources/reference.conf
  }
}
