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

import javax.management.ObjectName
import javax.management.openmbean.CompositeData

import akka.actor.{Actor, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{Uri, HttpRequest}
import akka.stream.ActorMaterializer
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.{Unicomplex, UnicomplexBoot, JMX}

object CubeActorErrorStatesSpec{

  val classPaths = Array(getClass.getClassLoader.getResource("classpaths/streaming/CubeActorErrorStates").getPath)

  val (_, _, port) = temporaryServerHostnameAndPort()

  val config = ConfigFactory.parseString(
    s"""
       |default-listener.bind-port = $port
       |squbs {
       |  actorsystem-name = streaming-cubeActorErrorStatesSpec
       |  ${JMX.prefixConfig} = true
       |  experimental-mode-on = true
       |}
    """.stripMargin
  )

  val boot = UnicomplexBoot(config)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()
}

class CubeActorErrorStatesSpec extends TestKit(
  CubeActorErrorStatesSpec.boot.actorSystem) with FlatSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll {

  val port = system.settings.config getInt "default-listener.bind-port"
  implicit val am = ActorMaterializer()

  override def afterAll() {
    Unicomplex(system).uniActor ! GracefulStop
  }

  "Route" should "handle request with empty web-context" in {
    Http().singleRequest(HttpRequest(uri = Uri(s"http://127.0.0.1:$port/test2?msg=1")))
    Thread.sleep(100)
    Http().singleRequest(HttpRequest(uri = Uri(s"http://127.0.0.1:$port/test1?msg=1")))
    Thread.sleep(100)
    Http().singleRequest(HttpRequest(uri = Uri(s"http://127.0.0.1:$port/test1?msg=2")))
    Thread.sleep(1000) // wait the agent get refreshed
    import org.squbs.unicomplex.JMX._
    val errorStates = get(new ObjectName(prefix(system) + cubeStateName + "CubeActorErrorStates"), "ActorErrorStates").asInstanceOf[Array[CompositeData]]
//    val state = errorStates.get("akka://squbs/user/CubeActorErrorStates/$b")
//    val state = errorStates.values()
    errorStates.size should be(2)
    val state1 = errorStates.find(_.get("actorPath").equals("/user/CubeActorErrorStates/test1-CubeActorTest-handlertarget")).get
    state1.get("errorCount") should be(2)
    state1.get("latestException").asInstanceOf[String] should include ("test1:2")
    val state2 = errorStates.find(_.get("actorPath").equals("/user/CubeActorErrorStates/test2-CubeActorTest-handlertarget")).get
    state2.get("errorCount") should be(1)
    state2.get("latestException").asInstanceOf[String] should include ("test2:1")
  }
}

class CubeActorTest extends Actor {
  override def receive: Receive = {
    case r: HttpRequest =>
      val msg = r.uri.query().get("msg").getOrElse("")
      throw new RuntimeException(s"${r.uri.path}:$msg")
  }
}

