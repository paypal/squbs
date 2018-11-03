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
package org.squbs.testkit

import java.io.File

import akka.actor.{Props, Actor}
import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{FlatSpecLike, Matchers}
import org.squbs.unicomplex.{JMX, RouteDefinition, UnicomplexBoot}
import spray.client.pipelining._
import spray.http.StatusCodes
import spray.routing.Directives
import spray.util.Utils._

import scala.concurrent.Await

class CustomTestKitSpec extends CustomTestKit(CustomTestKitSpec.boot)
with ImplicitSender with FlatSpecLike with Matchers with Eventually {

  override implicit val patienceConfig = new PatienceConfig(timeout = Span(3, Seconds))

  import system.dispatcher
  import scala.concurrent.duration._

  it should "return OK" in {
    val pipeline = sendReceive
    val result = Await.result(pipeline(Get(s"http://127.0.0.1:${CustomTestKitSpec.port}/test")), 20 second)
    result.entity.asString should include ("success")
  }

  it should "return a Pong on a Ping" in {
    system.actorOf(Props[TestActor]) ! TestPing
    receiveOne(10 seconds) should be (TestPong)
  }
}

object CustomTestKitSpec {

  import scala.collection.JavaConversions._

  val (_, port) = temporaryServerHostnameAndPort()

  val testConfig = ConfigFactory.parseMap(
    Map(
      "squbs.actorsystem-name" -> "myTest",
      "squbs.external-config-dir" -> "actorCalLogTestConfig",
      "default-listener.bind-port" -> Int.box(port),
      "squbs." + JMX.prefixConfig -> Boolean.box(true)
    )
  )

  lazy val boot = UnicomplexBoot(testConfig)
    .scanComponents(Seq(new File("src/test/resources/CustomTestKitTest").getAbsolutePath))
    .start()
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
