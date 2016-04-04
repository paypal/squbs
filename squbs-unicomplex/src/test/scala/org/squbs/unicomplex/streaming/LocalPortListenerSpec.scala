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
import akka.http.scaladsl.server.{Route, Directives}
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.Timeouts._
import org.squbs.unicomplex.{Unicomplex, UnicomplexBoot}

import scala.concurrent.Await

object LocalPortListenerSpecActorSystem {
  val (_, _, port1) = temporaryServerHostnameAndPort()
  val (_, _, port2) = temporaryServerHostnameAndPort()
  val (_, _, port3) = temporaryServerHostnameAndPort()

  val config = ConfigFactory.parseString(
      s"""
        squbs {
          actorsystem-name = streaming-LocalPortListenerSpec
          prefix-jmx-name = true
          experimental-mode-on = true
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
          bind-port =  $port2
          secure = false
          client-authn = false
          ssl-context = default
        }
        third-listener {
          type = squbs.listener
          aliases = []
          bind-address = "0.0.0.0"
          full-address = false
          bind-port =  $port3
          local-port-header = false
          secure = false
          client-authn = false
          ssl-context = default
          }
      """.stripMargin)

  val dummyJarsDir = getClass.getClassLoader.getResource("classpaths/streaming").getPath
  val classPaths = Array(
    "LocalPortListener"
  ) map (dummyJarsDir + "/" + _)

    val boot = UnicomplexBoot(config)
    		.createUsing { (name, config) => ActorSystem(name, config)}
  			.scanComponents(classPaths)
  			.initExtensions
  			.start()
  	
  	def getPort = (port1, port2, port3)
}

class LocalPortListenerSpec extends TestKit(LocalPortListenerSpecActorSystem.boot.actorSystem)
    with FlatSpecLike with BeforeAndAfterAll with Matchers {

  implicit val am = ActorMaterializer()
  
  val (port1, port2, port3) = LocalPortListenerSpecActorSystem.getPort

  it should "patch local port well on local-port-header = true" in {
    Await.result(entityAsInt(s"http://127.0.0.1:$port1/localport"), awaitMax) should be (port1)
  }

  it should "not patch local port header if local-port-header is false or absent" in {
    Await.result(entityAsInt(s"http://127.0.0.1:$port2/localport"), awaitMax) should be (0)
    Await.result(entityAsInt(s"http://127.0.0.1:$port3/localport"), awaitMax) should be (0)
  }

  override protected def afterAll(): Unit = {
    Unicomplex(system).uniActor ! GracefulStop
  }
}

class LocalPortListenerService extends RouteDefinition {

  override def route: Route = get {
    headerValueByType[LocalPortHeader]()(header => complete(header.value))
  } ~ get {
    complete(0.toString)
  }
}
