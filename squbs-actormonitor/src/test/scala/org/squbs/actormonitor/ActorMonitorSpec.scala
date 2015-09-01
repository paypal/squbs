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
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.scalatest._
import org.scalatest.concurrent.AsyncAssertions
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.JMX._
import org.squbs.unicomplex.{JMX, Unicomplex, UnicomplexBoot}
import spray.util.Utils

import scala.concurrent.{Await, Future}


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
                             with AsyncAssertions with LazyLogging {

  import org.squbs.testkit.Timeouts._
  implicit val ec = system.dispatcher

  override def beforeAll() {
    // Make sure all actors are indeed alive.
    val idFuture1 = (system.actorSelection("/user/TestCube/TestActor") ? Identify(None)).mapTo[ActorIdentity]
    val idFuture2 = (system.actorSelection("/user/TestCube/TestActorWithRoute") ? Identify(None)).mapTo[ActorIdentity]
    val idFuture3 = (system.actorSelection("/user/TestCube/TestActorWithRoute/$a") ? Identify(None)).mapTo[ActorIdentity]
    val idFuture4 = (system.actorSelection("/user/TestCube/TestActor1") ? Identify(None)).mapTo[ActorIdentity]
    val futures = Future.sequence(Seq(idFuture1, idFuture2, idFuture3, idFuture4))
    val idList = Await.result(futures, awaitMax)
    idList foreach {
      case ActorIdentity(_, Some(actor)) => logger.info(s"beforeAll identity: $actor")
      case other => logger.warn(s"beforeAll invalid identity: $other")
    }

    import ActorMonitorSpec._
    val cfgBeanCount = getActorMonitorConfigBean("Count").toString.toInt
    cfgBeanCount should be > 11
  }


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
          startsWith("Actor[akka://ActorMonitorSpec/user/TestCube/TestActor#"), max = awaitMax)
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
          .startsWith("Actor[akka://ActorMonitorSpec/user/TestCube/TestActorWithRoute#"), max = awaitMax)
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
        max = awaitMax)
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
      awaitAssert(ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor1", "Actor") != null, max = awaitMax)


      import ActorMonitorSpec._
      val originalNum = getActorMonitorConfigBean("Count").toString.toInt

      system.actorSelection("/user/TestCube/TestActor1") ! PoisonPill
      receiveOne(awaitMax) match {
        case msg =>
          awaitAssert(ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor1", "Actor") == null, max= awaitMax)
          awaitAssert(getActorMonitorConfigBean("Count") == originalNum - 1, max = awaitMax )
      }
    }

    "7.0) ActorMonitorConfigBean" in {
      import ActorMonitorSpec._
      getActorMonitorConfigBean("MaxCount") should be (500)
      getActorMonitorConfigBean("MaxChildrenDisplay") should be (20)
    }
  }
}




