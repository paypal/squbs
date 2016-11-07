/*
 *  Copyright 2015 PayPal
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
package org.squbs.unicomplex

import javax.management.ObjectName
import javax.management.openmbean.CompositeData

import akka.actor.{Actor, ActorSystem}
import akka.io.IO
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues._
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import org.squbs.lifecycle.GracefulStop
import spray.can.Http
import spray.http._
import spray.util.Utils

object CubeActorErrorStatesSpec{
  /*
  cube-name = org.squbs.unicomplex.test.RootRoute
  cube-version = "0.0.1"
  squbs-services = [
      {
          class-name = org.squbs.unicomplex.RootRoute
          web-context = ""
      }
  ]
   */
  val classPaths = Array(getClass.getClassLoader.getResource("classpaths/CubeActorErrorStates").getPath)

  val (_, port) = Utils.temporaryServerHostnameAndPort()

  val config = ConfigFactory.parseString(
    s"""
       |default-listener.bind-port = $port
       |squbs {
       |  actorsystem-name = cubeActorErrorStatesSpec
       |  ${JMX.prefixConfig} = true
       |}
    """.stripMargin
  )

  val boot = UnicomplexBoot(config)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()
}

class CubeActorErrorStatesSpec extends TestKit(CubeActorErrorStatesSpec.boot.actorSystem) with FlatSpecLike
with Matchers with ImplicitSender with BeforeAndAfterAll {

  val port = system.settings.config getInt "default-listener.bind-port"

  override def afterAll() {
    Unicomplex(system).uniActor ! GracefulStop
  }

  "Route" should "handle request with empty web-context" in {
    IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/test2?msg=1"))
    Thread.sleep(100)
    IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/test1?msg=1"))
    Thread.sleep(100)
    IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/test1?msg=2"))
    Thread.sleep(1000) // wait the agent get refreshed
    import org.squbs.unicomplex.JMX._
    val errorStates = get(new ObjectName(prefix(system) + cubeStateName + "CubeActorErrorStates"), "ActorErrorStates")
      .asInstanceOf[Array[CompositeData]]
//    val state = errorStates.get("akka://squbs/user/CubeActorErrorStates/$b")
//    val state = errorStates.values()
    errorStates.size should be(2)
    val state1 = errorStates.find(_.get("actorPath") == "/user/CubeActorErrorStates/test1-CubeActorTest-handlertarget")
    state1.value.get("errorCount") should be(2)
    state1.value.get("latestException").asInstanceOf[String] should include ("test1:2")
    val state2 = errorStates.find(_.get("actorPath") == "/user/CubeActorErrorStates/test2-CubeActorTest-handlertarget")
    state2.value.get("errorCount") should be(1)
    state2.value.get("latestException").asInstanceOf[String] should include ("test2:1")
  }
}

class CubeActorTest extends Actor {
  override def receive: Receive = {
    case r:HttpRequest =>
      val msg = r.uri.query.get("msg").getOrElse("")
      throw new RuntimeException(s"${r.uri.path}:$msg")
  }
}

