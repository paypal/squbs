/*
 *  Copyright 2017 PayPal
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

package org.squbs.testkit

import org.apache.pekko.actor.{Actor, Props}
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives
import com.typesafe.config.ConfigFactory
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.testkit.Timeouts._
import org.squbs.unicomplex.{JMX, RouteDefinition, UnicomplexBoot}

import scala.concurrent.Await

class CustomTestKitSpec extends CustomTestKit(CustomTestKitSpec.boot) with AnyFlatSpecLike with Matchers {

  it should "return OK with the port from PortGetter" in {
    val result = Await.result(entityAsString(s"http://127.0.0.1:$port/test"), awaitMax)
    result should include ("success")
  }

  it should "return a Pong on a Ping" in {
    system.actorOf(Props[TestActor]()) ! TestPing
    receiveOne(awaitMax) should be (TestPong)
  }
}

object CustomTestKitSpec {

  val testConfig = ConfigFactory.parseString(
    s"""
       |squbs.actorsystem-name = CustomTestKitSpec
       |squbs.external-config-dir = actorCalLogTestConfig
       |default-listener.bind-port = 0
       |squbs.${JMX.prefixConfig} = true
     """.stripMargin
  )

  lazy val boot = UnicomplexBoot(testConfig)
    .scanResources(getClass.getClassLoader.getResource("").getPath + "/CustomTestKitTest/META-INF/squbs-meta.conf")
    .start()
}

class CustomTestKitDefaultSpec extends CustomTestKit with AnyFlatSpecLike with Matchers {

  it should "start default-listener" in {
    noException should be thrownBy port
    port shouldEqual port("default-listener")
  }

  it should "set actor system name to package-class-integer" in {
    system.name should fullyMatch regex "org-squbs-testkit-CustomTestKitDefaultSpec-\\d+"
  }

  it should "use the default configuration" in {
    system.settings.config.getInt("default-listener.bind-port") should be(0)
    system.settings.config.getBoolean(s"squbs.${JMX.prefixConfig}") should be(true)
  }
}

class CustomTestKitDefaultWithActorSystemNameSpec extends CustomTestKit("CustomTestKitDefaultWithActorSystemNameSpec")
with AnyFlatSpecLike with Matchers {

  it should "start default-listener" in {
    noException should be thrownBy port
    port shouldEqual port("default-listener")
  }

  it should "set actor system name to the value passed in constructor" in {
    system.name should equal ("CustomTestKitDefaultWithActorSystemNameSpec")
  }

  it should "use the default configuration" in {
    system.settings.config.getInt("default-listener.bind-port") should be(0)
    system.settings.config.getBoolean(s"squbs.${JMX.prefixConfig}") should be(true)
  }
}

object CustomTestKitConfigSpec {
  val config = ConfigFactory.parseString {
      """
        |squbs {
        |  actorsystem-name = CustomTestKitConfigSpecInConfig
        |}
      """.stripMargin
  }
}

class CustomTestKitConfigSpec extends CustomTestKit(CustomTestKitConfigSpec.config)
with AnyFlatSpecLike with Matchers {

  it should "set actor system name to the value defined in config" in {
    system.name should equal ("CustomTestKitConfigSpecInConfig")
  }

  it should "start default-listener" in {
    noException should be thrownBy port
    port shouldEqual port("default-listener")
  }

  it should "give priority to custom config" in {
    system.settings.config.getString("squbs.actorsystem-name") should equal ("CustomTestKitConfigSpecInConfig")
  }

  it should "merge the custom config and default config" in {
    system.settings.config.getBoolean(s"squbs.${JMX.prefixConfig}") should be(true)
  }
}

object CustomTestKitResourcesSpec {

  val resources = Seq(getClass.getClassLoader.getResource("").getPath + "/CustomTestKitTest/META-INF/squbs-meta.conf")
}

class CustomTestKitResourcesSpec extends CustomTestKit(CustomTestKitResourcesSpec.resources,
                                                       false)
with AnyFlatSpecLike with Matchers {

  it should "set actor system name to package-class-integer" in {
    system.name should fullyMatch regex "org-squbs-testkit-CustomTestKitResourcesSpec-\\d+"
  }

  it should "start default-listener" in {
    noException should be thrownBy port
    port shouldEqual port("default-listener")
  }
}

class CustomTestKitResourcesWithActorSystemNameSpec
  extends CustomTestKit("CustomTestKitResourcesWithActorSystemNameSpec",
    CustomTestKitResourcesSpec.resources, withClassPath = false)
with AnyFlatSpecLike with Matchers {

  it should "set actor system name to the value passed in constructor" in {
    system.name should equal ("CustomTestKitResourcesWithActorSystemNameSpec")
  }

  it should "start default-listener" in {
    noException should be thrownBy port
    port shouldEqual port("default-listener")
  }
}

class CustomTestKitEmptyResourcesSpec extends CustomTestKit(Seq.empty, false)
with AnyFlatSpecLike with Matchers {

  it should "not bind to any port when resources is empty" in {
    an [NoSuchElementException] should be thrownBy port
  }
}

class CustomTestKitWithClassPathSpec extends CustomTestKit(Seq.empty, true)
with AnyFlatSpecLike with Matchers {

  it should "start default-listener" in {
    noException should be thrownBy port
    port shouldEqual port("default-listener")
  }
}

class CustomTestKitWithResourceDetectSpec extends CustomTestKit(false)
  with AnyFlatSpecLike with Matchers {

  it should "start default-listener" in {
    noException should be thrownBy port
    port shouldEqual port("default-listener")
  }
}

object CustomTestKitConfigAndResourcesSpec {

  val resources = Seq(getClass.getClassLoader.getResource("").getPath + "/CustomTestKitTest/META-INF/squbs-meta.conf")

  val config = ConfigFactory.parseString {
    """
      |squbs {
      |  actorsystem-name = CustomTestKitConfigAndResourcesSpecInConfig
      |}
    """.stripMargin
  }
}

class CustomTestKitConfigAndResourcesSpec extends CustomTestKit(CustomTestKitConfigAndResourcesSpec.config,
                                                                CustomTestKitConfigAndResourcesSpec.resources,
                                                                false)
with AnyFlatSpecLike with Matchers {

  it should "set actor system name to the value defined in config" in {
    system.name should equal ("CustomTestKitConfigAndResourcesSpecInConfig")
  }

  it should "start default-listener" in {
    noException should be thrownBy port
    port shouldEqual port("default-listener")
  }
}

class Service extends RouteDefinition with Directives {

  def route = get {
    complete(StatusCodes.OK, "success")
  }
}

class TestActor extends Actor {
  def receive = {
    case TestPing => sender() ! TestPong
  }
}
