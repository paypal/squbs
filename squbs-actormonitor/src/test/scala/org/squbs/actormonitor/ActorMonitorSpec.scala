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

package org.squbs.actormonitor

import java.lang.management.ManagementFactory
import javax.management.ObjectName

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.scalatest._
import org.scalatest.concurrent.AsyncAssertions
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.JMX._
import org.squbs.unicomplex.{JMX, Unicomplex, UnicomplexBoot}
import spray.util.Utils

import scala.concurrent.duration._


object ActorMonitorSpec extends LazyLogging {

  val dummyJarsDir = getClass.getClassLoader.getResource("classpaths").getPath

  val classPaths = Array(
   "ActorMonitorCube",
   "TestCube"
  ) map (dummyJarsDir + "/" + _)

  val (_, port) = Utils.temporaryServerHostnameAndPort()

  val config = ConfigFactory.parseString(
    s"""
       |squbs {
       |  actorsystem-name = ActorMonitorSpec
       |  ${JMX.prefixConfig} = true
       |}
       |default-listener.bind-port = $port
    """.stripMargin)

  val boot = UnicomplexBoot(config)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()


  var originalNum = getActorMonitorConfigBean("Count").toString.toInt
  Thread.sleep(2000)
  while(originalNum < 12){
    Thread.sleep(2000)
    originalNum = getActorMonitorConfigBean("Count").toString.toInt
    Thread.sleep(2000)
  }


 def getActorMonitorBean(actorName: String, att: String) =
    try {
      ManagementFactory.getPlatformMBeanServer.getAttribute(getObjName(actorName), att).asInstanceOf[String]
    } catch {
      case e: Exception =>
        logger.error(e.getMessage, e)
        null
    }

  def getActorMonitorConfigBean(att: String) =
    try {
      val o = new ObjectName(prefix(boot.actorSystem) + "org.squbs.unicomplex:type=ActorMonitor")
      ManagementFactory.getPlatformMBeanServer.getAttribute(o, att).asInstanceOf[Int]
    } catch {
      case e: Exception =>
        logger.error(e.getMessage, e)
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

    "1.0) getMailBoxSize of unicomplex" in {
      ActorMonitorSpec.getActorMonitorBean("user/unicomplex", "MailBoxSize") should be ("0")
    }

    "1.1) getActor of TestCube/TestActor" in {
      awaitAssert (
        ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor", "Actor").
          startsWith("Actor[akka://ActorMonitorSpec/user/TestCube/TestActor#"), max= 5 seconds)
    }

    "2.1) getClassName of TestCube/TestActor" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor", "ClassName")
      bean should be ("org.squbs.actormonitor.testcube.TestActor")
    }

    "2.2) getRouteConfig of TestCube/TestActor" in {
      ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor", "RouteConfig") should be ("NoRouter")
    }

    "2.3) getParent of TestCube/TestActor" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor", "Parent")
      bean should startWith ("Actor[akka://ActorMonitorSpec/user/TestCube#")
    }

    "2.4) getChildren of TestCube/TestActor" in {
      ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor", "Children") shouldBe empty
    }

    "2.5) getDispatcher of TestCube/TestActor" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor", "Dispatcher")
      bean should be ("akka.actor.default-dispatcher")
    }

    "2.6) getMailBoxSize of TestCube/TestActor" in {
      ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor", "MailBoxSize") should be ("0")
    }

   "3.0) getActor of TestCube" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube", "Actor")
      bean should startWith ("Actor[akka://ActorMonitorSpec/user/TestCube#")
    }

    "3.1) check ActorBean ClassName of TestCube" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube", "ClassName")
      bean should be ("org.squbs.unicomplex.CubeSupervisor")
    }

    "3.2) getRouteConfig of TestCube" in {
      ActorMonitorSpec.getActorMonitorBean("user/TestCube", "RouteConfig") should be ("NoRouter")
    }

    "3.3) getParent of TestCube" in {
      ActorMonitorSpec.getActorMonitorBean("user/TestCube", "Parent") should be ("Actor[akka://ActorMonitorSpec/user]")
    }

    "3.4) getChildren of TestCube" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube", "Children")
      bean should include ("Actor[akka://ActorMonitorSpec/user/TestCube/TestActor#")
      bean should include ("Actor[akka://ActorMonitorSpec/user/TestCube/TestActorWithRoute#")
    }

    "3.5) getDispatcher of TestCube" in {
      ActorMonitorSpec.getActorMonitorBean("user/TestCube", "Dispatcher") should be ("akka.actor.default-dispatcher")
    }

    "3.6) getMailBoxSize of TestCube" in {
      ActorMonitorSpec.getActorMonitorBean("user/TestCube", "MailBoxSize") should be ("0")
    }

    "4.0) getActor of TestCube/TestActorWithRoute" in {
      awaitAssert (
        ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute", "Actor")
          .startsWith("Actor[akka://ActorMonitorSpec/user/TestCube/TestActorWithRoute#"), max = 6 seconds)
    }

    "4.1) getClassName of TestCube/TestActorWithRoute" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute", "ClassName")
      bean should be ("akka.routing.RouterActor")
    }

    "4.2) getRouteConfig of TestCube/TestActorWithRoute" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute", "RouteConfig")
      bean should be ("RoundRobinPool(1,Some(DefaultResizer(1,10,1,0.2,0.3,0.1,10))," +
        "OneForOneStrategy(-1,Duration.Inf,true),akka.actor.default-dispatcher,false)")
    }

    "4.3) getParent of TestCube/TestActorWithRoute" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute", "Parent")
      bean should startWith ("Actor[akka://ActorMonitorSpec/user/TestCube#")
    }

    "4.4) getChildren of TestCube/TestActorWithRoute" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute", "Children")
      bean should include ("Actor[akka://ActorMonitorSpec/user/TestCube/TestActorWithRoute/$a#")
    }

    "4.5) getDispatcher of TestCube/TestActorWithRoute" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor", "Dispatcher")
      bean should be ("akka.actor.default-dispatcher")
    }

    "4.6) getMailBoxSize of TestCube/TestActorWithRoute" in {
      ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor", "MailBoxSize") should be ("0")
    }

    "5.0) getActor of TestCube/TestActorWithRoute/$a" in {
      awaitAssert(
        ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute/$a", "Actor").
          startsWith("Actor[akka://ActorMonitorSpec/user/TestCube/TestActorWithRoute/$a#"),
        max = 2 seconds)
    }

    "5.1) getClassName of TestCube/TestActorWithRoute/$a" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute/$a", "ClassName")
      bean should be ("org.squbs.actormonitor.testcube.TestActorWithRoute")
    }

    "5.2) getRouteConfig of TestCube/TestActorWithRoute/$a" in {
      ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute/$a", "RouteConfig") should be ("NoRouter")
    }

    "5.3) getParent of TestCube/TestActorWithRoute/$a" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute/$a", "Parent")
      bean should startWith ("Actor[akka://ActorMonitorSpec/user/TestCube/TestActorWithRoute#")
    }

    "5.4) getChildren of TestCube/TestActorWithRoute/$a" in {
      ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute/$a", "Children") should be ("")
    }

    "5.5) getDispatcher of TestCube/TestActorWithRoute/$a" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute/$a", "Dispatcher")
      bean should be ("blocking-dispatcher")
    }

    "5.6) getMailBoxSize of TestCube/TestActorWithRoute/$a" in {
      ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute/$a", "MailBoxSize") should be ("0")
    }

    "6.1) getBean after actor has been stop" in {
      awaitAssert(ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor1", "Actor") != null, max = 2 seconds)


      import ActorMonitorSpec._
      val originalNum = getActorMonitorConfigBean("Count").toString.toInt

      system.actorSelection("/user/TestCube/TestActor1") ! PoisonPill
      receiveOne(2 seconds) match {
        case msg =>
          awaitAssert(ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor1", "Actor") == null, max= 2 second)
          awaitAssert(getActorMonitorConfigBean("Count") == originalNum - 1, max = 2 second )
      }
    }

    "7.0) ActorMonitorConfigBean" in {
      import ActorMonitorSpec._
      getActorMonitorConfigBean("MaxCount") should be (500)
      getActorMonitorConfigBean("MaxChildrenDisplay") should be (20)
    }
  }
}




