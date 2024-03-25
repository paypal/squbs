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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex._

import scala.language.postfixOps

object PortGetterSpec {

  val dummyJarsDir = getClass.getClassLoader.getResource("").getPath

  val classPaths = Array(
    "PortGetterSpec"
  ) map (dummyJarsDir + "/" + _)

  def config(actorSystemName: String) = ConfigFactory.parseString(
    s"""
       |squbs {
       |  actorsystem-name = ${actorSystemName}
       |  ${JMX.prefixConfig} = true
       |}
       |
       |default-listener.bind-port = 0
       |
       |my-listener {
       |  type = squbs.listener
       |  bind-address = "0.0.0.0"
       |  full-address = false
       |  bind-port = 0
       |  secure = false
       |  need-client-auth = false
       |  ssl-context = default
       |}
    """.stripMargin
  )

  def boot(actorSystemName: String) = UnicomplexBoot(config(actorSystemName))
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()

}

class PortGetterSpec extends TestKit(PortGetterSpec.boot("portGetterSpec").actorSystem) with ImplicitSender
  with AnyFlatSpecLike with Matchers with BeforeAndAfterAll with PortGetter {

  override def afterAll(): Unit = {
    Unicomplex(system).uniActor ! GracefulStop
  }

  "PortGetter" should "retrieve the port" in {
    port should be > 0
    port shouldEqual port("default-listener")
  }
}

class PortGetterCustomListenerSpec extends TestKit(PortGetterSpec.boot("PortGetterCustomListenerSpec").actorSystem)
  with ImplicitSender with AnyFlatSpecLike with Matchers with BeforeAndAfterAll with PortGetter {

  override def afterAll(): Unit = {
    Unicomplex(system).uniActor ! GracefulStop
  }

  override def listener = "my-listener"

  "PortGetter" should "retrieve the port" in {
    port should be > 0
  }

  "PortGetter" should "return the specified listener's port" in {
    port should not equal port("default-listener")
    port shouldEqual port("my-listener")
  }
}
