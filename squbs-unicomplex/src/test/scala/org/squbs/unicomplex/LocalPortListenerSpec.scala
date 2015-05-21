/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the AUTHORS file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.squbs.unicomplex

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import org.squbs.lifecycle.GracefulStop
import spray.client.pipelining._
import spray.routing.{Directives, Route}
import spray.util.Utils

import scala.concurrent.Await

object LocalPortListenerSpecActorSystem {
  val (_, port1) = Utils.temporaryServerHostnameAndPort()
  val (_, port2) = Utils.temporaryServerHostnameAndPort()
  val (_, port3) = Utils.temporaryServerHostnameAndPort()

  val config = ConfigFactory.parseString(
      s"""
        squbs {
          actorsystem-name = "LocalPortListenerSpec"
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

  val dummyJarsDir = getClass.getClassLoader.getResource("classpaths").getPath
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

  import system.dispatcher

  import scala.concurrent.duration._
  
  val (port1, port2, port3) = LocalPortListenerSpecActorSystem.getPort

  it should "patch local port well on local-port-header = true" in {
    val pipeline = sendReceive
    Await.result(pipeline(Get(s"http://127.0.0.1:$port1/localport")), 1 second).entity.asString.toInt should be (port1)
//    Await.result(pipeline(Get(s"http://127.0.0.1:$port2/localport")), 1 second).status.intValue should be (200)
  }

  it should "not patch local port header if local-port-header is false or absent" in {
    val pipeline = sendReceive
    Await.result(pipeline(Get(s"http://127.0.0.1:$port2/localport")), 1 second).entity.asString.toInt should be (0)
    Await.result(pipeline(Get(s"http://127.0.0.1:$port3/localport")), 1 second).entity.asString.toInt should be (0)
  }

  override protected def afterAll(): Unit = {
    Unicomplex(system).uniActor ! GracefulStop
  }
}

class LocalPortListenerService extends RouteDefinition with Directives {



  override def route: Route = get {
    headerValueByType[LocalPortHeader]()(header => complete(header.value))
  } ~ get {
    complete(0.toString)
  }
}
