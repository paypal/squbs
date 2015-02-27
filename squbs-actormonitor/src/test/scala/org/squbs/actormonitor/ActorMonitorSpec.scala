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
package org.squbs.actormonitor

import java.lang.management.ManagementFactory
import javax.management.ObjectName

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout

import com.typesafe.config.ConfigFactory
import org.scalatest._
import org.scalatest.concurrent.AsyncAssertions



import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.JMX._
import org.squbs.unicomplex.{Unicomplex, JMX, UnicomplexBoot}

import scala.concurrent.duration._


object ActorMonitorSpec {

  val dummyJarsDir = getClass.getClassLoader.getResource("classpaths").getPath

  val classPaths = Array(
   "ActorMonitorCube",
   "TestCube"
  ) map (dummyJarsDir + "/" + _)

  import scala.collection.JavaConversions._

  val mapConfig = ConfigFactory.parseMap(
    Map(
      "squbs.actorsystem-name"    -> "ActorMonitorSpec",
      "squbs." + JMX.prefixConfig -> Boolean.box(true),
      "default-listener.bind-port" -> org.squbs.nextPort.toString
    )
  )

  val boot = UnicomplexBoot(mapConfig)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()



 def getActorMonitorBean(actorName: String, att: String) =
    try {
      ManagementFactory.getPlatformMBeanServer.getAttribute(getObjName(actorName), att).asInstanceOf[String]
    } catch {
      case e: Exception =>
	      e.printStackTrace()
      //  println("exception => " + e.getMessage)
       null
    }

  def getActorMonitorConfigBean(att: String) =
    try {
      val o = new ObjectName(prefix(boot.actorSystem) + "org.squbs.unicomplex:type=ActorMonitor")
      ManagementFactory.getPlatformMBeanServer.getAttribute(o, att).asInstanceOf[Int]
    } catch {
      case e: Exception =>
        null
    }

  def getObjName(name: String) = new ObjectName(prefix(boot.actorSystem) + ActorMonitorBean.Pattern + name)

}

class ActorMonitorSpec extends TestKit(ActorMonitorSpec.boot.actorSystem) with ImplicitSender
                             with WordSpecLike with Matchers with BeforeAndAfterAll
                             with AsyncAssertions {

  implicit val timeout: akka.util.Timeout = Timeout(1 seconds)
  implicit val ec = system.dispatcher


  override def afterAll() {
    Unicomplex(system).uniActor ! GracefulStop
  }

  "ActorMonitor" must {

    "1.1) getActor of TestCube/TestActor" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor", "Actor")
      assert(bean.startsWith("Actor[akka://ActorMonitorSpec/user/TestCube/TestActor#"))
    }


    "2.1) getClassName of TestCube/TestActor" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor", "ClassName")
      assert(bean == "org.squbs.actormonitor.testcube.TestActor")
    }

    "2.2) getRouteConfig of TestCube/TestActor" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor", "RouteConfig")
      assert(bean == "NoRouter")
    }

    "2.3) getParent of TestCube/TestActor" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor", "Parent")
      assert(bean.startsWith("Actor[akka://ActorMonitorSpec/user/TestCube#"))
    }

    "2.4) getChildren of TestCube/TestActor" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor", "Children")
      assert(bean == "")
    }

    "2.5) getDispatcher of TestCube/TestActor" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor", "Dispatcher")
      assert(bean == "akka.actor.default-dispatcher")
    }

    "2.6) getMailBoxSize of TestCube/TestActor" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor", "MailBoxSize")
      assert(bean == "0")
    }

    "3.0) getActor of TestCube" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube", "Actor")
      assert(bean.startsWith("Actor[akka://ActorMonitorSpec/user/TestCube#"))
    }

    "3.1) check ActorBean ClassName of TestCube" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube", "ClassName")
      assert(bean == "org.squbs.unicomplex.CubeSupervisor")
    }

    "3.2) getRouteConfig of TestCube" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube", "RouteConfig")
      assert(bean == "NoRouter")
    }

    "3.3) getParent of TestCube" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube", "Parent")
      assert(bean == "Actor[akka://ActorMonitorSpec/user]")
    }

    "3.4) getChildren of TestCube" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube", "Children")
      assert(bean.contains("Actor[akka://ActorMonitorSpec/user/TestCube/TestActor#") && bean.contains("Actor[akka://ActorMonitorSpec/user/TestCube/TestActorWithRoute#"))
    }

    "3.5) getDispatcher of TestCube" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube", "Dispatcher")
      assert(bean == "akka.actor.default-dispatcher")
    }

    "3.6) getMailBoxSize of TestCube" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube", "MailBoxSize")
      assert(bean == "N/A")
    }

    "4.0) getActor of TestCube/TestActorWithRoute" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute", "Actor")
      assert(bean.startsWith("Actor[akka://ActorMonitorSpec/user/TestCube/TestActorWithRoute#"))
    }

    "4.1) getClassName of TestCube/TestActorWithRoute" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute", "ClassName")
      assert(bean == "akka.routing.RouterActor")
    }

    "4.2) getRouteConfig of TestCube/TestActorWithRoute" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute", "RouteConfig")
      assert(bean == "RoundRobinPool(1,Some(DefaultResizer(1,10,1,0.2,0.3,0.1,10)),OneForOneStrategy(-1,Duration.Inf,true),akka.actor.default-dispatcher,false)")
    }

    "4.3) getParent of TestCube/TestActorWithRoute" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute", "Parent")
      assert(bean.startsWith("Actor[akka://ActorMonitorSpec/user/TestCube#"))
    }

    "4.4) getChildren of TestCube/TestActorWithRoute" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute", "Children")
      assert(bean.contains("Actor[akka://ActorMonitorSpec/user/TestCube/TestActorWithRoute/$a#"))
    }

    "4.5) getDispatcher of TestCube/TestActorWithRoute" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor", "Dispatcher")
      assert(bean == "akka.actor.default-dispatcher")
    }

    "4.6) getMailBoxSize of TestCube/TestActorWithRoute" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor", "MailBoxSize")
      assert(bean == "0")
    }

    "5.0) getActor of TestCube/TestActorWithRoute/$a" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute/$a", "Actor")
      assert(bean.startsWith("Actor[akka://ActorMonitorSpec/user/TestCube/TestActorWithRoute/$a#"))
    }

    "5.1) getClassName of TestCube/TestActorWithRoute/$a" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute/$a", "ClassName")
      assert(bean == "org.squbs.actormonitor.testcube.TestActorWithRoute")
    }

    "5.2) getRouteConfig of TestCube/TestActorWithRoute/$a" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute/$a", "RouteConfig")
      assert(bean == "NoRouter")
    }

    "5.3) getParent of TestCube/TestActorWithRoute/$a" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute/$a", "Parent")
      assert(bean.startsWith("Actor[akka://ActorMonitorSpec/user/TestCube/TestActorWithRoute#"))
    }

    "5.4) getChildren of TestCube/TestActorWithRoute/$a" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute/$a", "Children")
      assert(bean == "")
    }

    "5.5) getDispatcher of TestCube/TestActorWithRoute/$a" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute/$a", "Dispatcher")
      assert(bean == "blocking-dispatcher")
    }

    "5.6) getMailBoxSize of TestCube/TestActorWithRoute/$a" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute/$a", "MailBoxSize")
      assert(bean == "0")
    }

    "6.1) getBean after actor has been stop" in {
      val before = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor1", "Actor")
      import ActorMonitorSpec._
      val originalNum = getActorMonitorConfigBean("Count")
      assert(before != null)
      system.actorSelection("/user/TestCube/TestActor1") ! PoisonPill
      receiveOne(2 seconds) match {
        case msg =>
          val after = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor1", "Actor")
          assert(after == null)
          assert(getActorMonitorConfigBean("Count") == 11)
      }
    }

    "7.0) ActorMonitorConfigBean" in {
      import ActorMonitorSpec._
      assert(getActorMonitorConfigBean("MaxCount") == 500)
      assert(getActorMonitorConfigBean("MaxChildrenDisplay") == 20)
    }

  }

}



