/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the CONTRIBUTING file distributed with this work for
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

import java.io.File
import java.util.UUID

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import org.squbs._
import org.squbs.lifecycle.GracefulStop
import spray.client.pipelining._
import spray.http.StatusCodes
import spray.routing.{Directives, Route}

import scala.concurrent.Await

class MultiListenerSpec extends TestKit(ActorSystem("MultiListenerSpec"))
    with FlatSpecLike with BeforeAndAfterAll with Matchers {

  import system.dispatcher
  import scala.concurrent.duration._

  val port1 = nextPort()
  val port2 = nextPort()
  var boot: UnicomplexBoot = null

  it should "run up two listeners on different ports" in {
    val pipeline = sendReceive
    Await.result(pipeline(Get(s"http://127.0.0.1:$port1/multi")), 1 second).status.intValue should be (200)
    Await.result(pipeline(Get(s"http://127.0.0.1:$port2/multi")), 1 second).status.intValue should be (200)
  }

  it should "only have started the application once" in {
    MultiListenerService.count should be(1)
  }

  override protected def beforeAll(): Unit = {
    val config = ConfigFactory.parseString(
      s"""
        squbs {
          actorsystem-name = "${UUID.randomUUID()}"
          ${JMX.prefixConfig} = true
        }
        default-listener {
          type = squbs.listener
          aliases = []
          bind-address = "0.0.0.0"
          full-address = false
          bind-port = $port1
          bind-service = true
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
          bind-service = true
          secure = false
          client-authn = false
          ssl-context = default
        }
      """.stripMargin)
    boot = UnicomplexBoot(config)
      .createUsing { (name, config) => ActorSystem(name, config)}
      .scanComponents(Seq(new File("squbs-unicomplex/src/test/resources/classpaths/MultiListeners").getAbsolutePath))
      .initExtensions
      .start()
  }

  override protected def afterAll(): Unit = {
    Unicomplex(boot.actorSystem).uniActor ! GracefulStop
  }
}

class MultiListenerService extends RouteDefinition with Directives {
  MultiListenerService.inc()


  override def route: Route = get {
    complete(StatusCodes.OK)
  }
}

object MultiListenerService {
  private var counter = 0

  def count = counter

  def inc(): Unit = counter = counter + 1
}