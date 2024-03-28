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
package org.squbs.actorregistry

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.JMX.prefix
import org.squbs.unicomplex._

import java.lang.management.ManagementFactory
import javax.management.ObjectName

object NoWellKnownActorsSpec {

  val classPaths = Array(getClass.getClassLoader.getResource("classpaths/NoWellKnownActorsCube").getPath)

  val config = ConfigFactory.parseString(
    s"""
       |squbs {
       |  actorsystem-name = ActorRegistryNoWellKnownActorsSpec
       |  ${JMX.prefixConfig} = true
       |}
    """.stripMargin
  )

  val boot = UnicomplexBoot(config)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()
}

class NoWellKnownActorsSpec extends TestKit(NoWellKnownActorsSpec.boot.actorSystem)
  with ImplicitSender with AnyFunSpecLike with Matchers with BeforeAndAfterAll {

  import NoWellKnownActorsSpec._

  override def afterAll(): Unit = {
    Unicomplex(system).uniActor ! GracefulStop
  }

  describe("ActorRegistry") {
    it ("should initialize even if there is no well-known actor in the classpath") {
      awaitAssert {
        boot.started shouldBe true
        Unicomplex(system).uniActor ! SystemState
        expectMsg(Active)
      }
    }

    it ("should show well-known actor count as zero") {
      val o = new ObjectName(prefix(boot.actorSystem) + "org.squbs.unicomplex:type=ActorRegistry")
      val count = ManagementFactory.getPlatformMBeanServer.getAttribute(o, "Count").asInstanceOf[Int]
      count should be (0)
    }
  }
}