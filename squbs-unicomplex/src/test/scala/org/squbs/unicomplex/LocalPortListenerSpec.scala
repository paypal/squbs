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
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.pattern._
import org.apache.pekko.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.Timeouts._

import scala.concurrent.Await

object LocalPortListenerSpecActorSystem {

  // Case 1 default-listener: Explicitly generate a port for this test. The local port header takes the port form the config.
  // Case 2 fourth-listener: Implicitly ask Unicomplex to create a port.
  val (_, _, port1) = temporaryServerHostnameAndPort()

  val config = ConfigFactory.parseString(
      s"""
        squbs {
          actorsystem-name = LocalPortListenerSpec
          prefix-jmx-name = true
        }
        default-listener {
          type = squbs.listener
          aliases = []
          bind-address = "0.0.0.0"
          full-address = false
          bind-port = $port1
          local-port-header = true
          secure = false
          client-authn = false
          ssl-context = default
        }
        second-listener {
          type = squbs.listener
          aliases = []
          bind-address = "0.0.0.0"
          full-address = false
          bind-port = 0
          secure = false
          client-authn = false
          ssl-context = default
        }
        third-listener {
          type = squbs.listener
          aliases = []
          bind-address = "0.0.0.0"
          full-address = false
          bind-port = 0
          local-port-header = false
          secure = false
          client-authn = false
          ssl-context = default
        }
        fourth-listener {
          type = squbs.listener
          aliases = []
          bind-address = "0.0.0.0"
          full-address = false
          bind-port = 0
          local-port-header = true
          secure = false
          client-authn = false
          ssl-context = default
        }
      """.stripMargin)

  val dummyJarsDir = getClass.getClassLoader.getResource("classpaths").getPath
  val classPaths = Array(
    "LocalPortListener"
  ) map (dummyJarsDir + "/" + _)

    val boot = UnicomplexBoot(config)
    		.createUsing { (name, config) => ActorSystem(name, config)}
  			.scanComponents(classPaths)
  			.initExtensions
  			.start()
}

class LocalPortListenerSpec extends TestKit(LocalPortListenerSpecActorSystem.boot.actorSystem)
    with AnyFlatSpecLike with BeforeAndAfterAll with Matchers {

  val portBindings = Await.result((Unicomplex(system).uniActor ? PortBindings).mapTo[Map[String, Int]], awaitMax)
  val port1 = portBindings("default-listener")
  val port2 = portBindings("second-listener")
  val port3 = portBindings("third-listener")
  val port4 = portBindings("fourth-listener")

  it should "patch local port on local-port-header = true when port manually assigned" in {
    Await.result(entityAsInt(s"http://127.0.0.1:$port1/localport"), awaitMax) should be (port1)
  }

  it should "patch local port on local-port-header = true when port automatically assigned" in {
    Await.result(entityAsInt(s"http://127.0.0.1:$port4/localport"), awaitMax) should be (port4)
  }

  it should "not patch local port header if local-port-header is false or absent" in {
    Await.result(entityAsInt(s"http://127.0.0.1:$port2/localport"), awaitMax) should be (-1)
    Await.result(entityAsInt(s"http://127.0.0.1:$port3/localport"), awaitMax) should be (-1)
  }

  override protected def afterAll(): Unit = {
    Unicomplex(system).uniActor ! GracefulStop
  }
}

class LocalPortListenerService extends RouteDefinition {

  override def route: Route = get {
    headerValueByType[LocalPortHeader]()(header => complete(header.value()))
  } ~ get {
    complete((-1).toString)
  }
}
