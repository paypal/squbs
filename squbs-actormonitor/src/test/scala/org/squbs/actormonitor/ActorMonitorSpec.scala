/*
 *  Copyright 2017 PayPal
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
import org.apache.pekko.actor._
import org.apache.pekko.pattern.ask
import org.apache.pekko.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Waiters
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.JMX._
import org.squbs.unicomplex.{JMX, Unicomplex, UnicomplexBoot}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Try


object ActorMonitorSpec extends LazyLogging {

  val dummyJarsDir = getClass.getClassLoader.getResource("classpaths").getPath

  val classPaths = Array(
   "ActorMonitorCube",
   "TestCube"
  ) map (dummyJarsDir + "/" + _)


  val config = ConfigFactory.parseString(
    s"""
       |squbs {
       |  actorsystem-name = ActorMonitorSpec
       |  ${JMX.prefixConfig} = true
       |}
       |default-listener.bind-port = 0
    """.stripMargin)

  val boot = UnicomplexBoot(config)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()

  def getActorMonitorBean(actorName: String, att: String) =
    Try {
      ManagementFactory.getPlatformMBeanServer.getAttribute(getObjName(actorName), att).asInstanceOf[String]
    } .toOption

  def getActorMonitorConfigBean(att: String) =
    Try {
      val o = new ObjectName(prefix(boot.actorSystem) + "org.squbs.unicomplex:type=ActorMonitor")
      ManagementFactory.getPlatformMBeanServer.getAttribute(o, att).asInstanceOf[Int]
    } .toOption

  def getObjName(name: String) = new ObjectName(prefix(boot.actorSystem) + ActorMonitorBean.Pattern + name)

}

class ActorMonitorSpec extends TestKit(ActorMonitorSpec.boot.actorSystem) with ImplicitSender
                             with AnyWordSpecLike with Matchers with BeforeAndAfterAll
                             with Waiters with LazyLogging {

  import org.squbs.testkit.Timeouts._
  import system.dispatcher

  override def beforeAll(): Unit = {
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
  }


  override def afterAll(): Unit = {
    Unicomplex(system).uniActor ! GracefulStop
  }

  "ActorMonitor" must {

    "0.0) Register all necessary base actors and have an up-to-date count" in {
      awaitAssert({
        import ActorMonitorSpec.{getActorMonitorBean, getActorMonitorConfigBean}
        getActorMonitorBean("system", "Actor") should be (Some("Actor[pekko://ActorMonitorSpec/system]"))
        getActorMonitorBean("user", "Actor") should be (Some("Actor[pekko://ActorMonitorSpec/user]"))
        getActorMonitorBean("system/deadLetterListener", "Actor")
          .getOrElse("") should startWith ("Actor[pekko://ActorMonitorSpec/system/deadLetterListener#")
        getActorMonitorBean("user/unicomplex", "Actor")
          .getOrElse("") should startWith ("Actor[pekko://ActorMonitorSpec/user/unicomplex#")
        getActorMonitorBean("user/ActorMonitorCube", "Actor")
          .getOrElse("") should startWith ("Actor[pekko://ActorMonitorSpec/user/ActorMonitorCube#")
        getActorMonitorBean("user/squbs-actormonitor", "Actor")
          .getOrElse("") should startWith ("Actor[pekko://ActorMonitorSpec/user/squbs-actormonitor#")
        /*
        Note: The following actor beans are just checked in the other tests below, no need to repeat:
        1. user/TestCube
        2. user/TestCube/TestActor
        3. user/TestCube/TestActorWithRoute
        4. user/TestCube/TestActorWithRoute/$a
        5. user/TestCube/TestActor1
        We just check the count to make sure we have adequate beans registered.
         */

        val cfgBeanCount = getActorMonitorConfigBean("Count").getOrElse(-100)
        if (cfgBeanCount < 11) {
          system.actorSelection("/user/squbs-actormonitor") ! "refresh"
          logger.warn("Did not register all relevant actors just yet. Refreshing...")
        }
        cfgBeanCount should be >= 11
      }, max = awaitMax, interval = 2.seconds)
    }

    "1.0) getMailBoxSize of unicomplex" in {
      ActorMonitorSpec.getActorMonitorBean("user/unicomplex", "MailBoxSize") should be (Some("0"))
    }

    "1.1) getActor of TestCube/TestActor" in {
      awaitAssert(
        ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor", "Actor")
          .getOrElse("") should startWith ("Actor[pekko://ActorMonitorSpec/user/TestCube/TestActor#"),
      max = awaitMax)
    }

    "2.1) getClassName of TestCube/TestActor" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor", "ClassName")
      bean should be (Some("org.squbs.actormonitor.testcube.TestActor"))
    }

    "2.2) getRouteConfig of TestCube/TestActor" in {
      ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor", "RouteConfig") should be (Some("NoRouter"))
    }

    "2.3) getParent of TestCube/TestActor" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor", "Parent")
      bean shouldBe defined
      bean getOrElse "" should startWith ("Actor[pekko://ActorMonitorSpec/user/TestCube#")
    }

    "2.4) getChildren of TestCube/TestActor" in {
      ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor", "Children") should be (Some(""))
    }

    "2.5) getDispatcher of TestCube/TestActor" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor", "Dispatcher")
      bean should be (Some("pekko.actor.default-dispatcher"))
    }

    "2.6) getMailBoxSize of TestCube/TestActor" in {
      ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor", "MailBoxSize") should be (Some("0"))
    }

    "3.0) getActor of TestCube" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube", "Actor")
      bean shouldBe defined
      bean getOrElse "" should startWith ("Actor[pekko://ActorMonitorSpec/user/TestCube#")
    }

    "3.1) check ActorBean ClassName of TestCube" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube", "ClassName")
      bean should be (Some("org.squbs.unicomplex.CubeSupervisor"))
    }

    "3.2) getRouteConfig of TestCube" in {
      ActorMonitorSpec.getActorMonitorBean("user/TestCube", "RouteConfig") should be (Some("NoRouter"))
    }

    "3.3) getParent of TestCube" in {
      ActorMonitorSpec.
        getActorMonitorBean("user/TestCube", "Parent") should be (Some("Actor[pekko://ActorMonitorSpec/user]"))
    }

    "3.4) getChildren of TestCube" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube", "Children")
      bean getOrElse "" should include ("Actor[pekko://ActorMonitorSpec/user/TestCube/TestActor#")
      bean getOrElse "" should include ("Actor[pekko://ActorMonitorSpec/user/TestCube/TestActorWithRoute#")
    }

    "3.5) getDispatcher of TestCube" in {
      ActorMonitorSpec.
        getActorMonitorBean("user/TestCube", "Dispatcher") should be (Some("pekko.actor.default-dispatcher"))
    }

    "3.6) getMailBoxSize of TestCube" in {
      ActorMonitorSpec.getActorMonitorBean("user/TestCube", "MailBoxSize") should be (Some("0"))
    }

    "4.0) getActor of TestCube/TestActorWithRoute" in {
      awaitAssert (
        ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute", "Actor")
          .getOrElse("") should startWith ("Actor[pekko://ActorMonitorSpec/user/TestCube/TestActorWithRoute#"),
        max = awaitMax)
    }

    "4.1) getClassName of TestCube/TestActorWithRoute" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute", "ClassName")
      bean should be (Some("org.apache.pekko.routing.RouterActor"))
    }

    "4.2) getRouteConfig of TestCube/TestActorWithRoute" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute", "RouteConfig")
      bean should be (Option("RoundRobinPool(1,Some(DefaultResizer(1,10,1,0.2,0.3,0.1,10))," +
        "OneForOneStrategy(-1,Duration.Inf,true),pekko.actor.default-dispatcher,false)"))
    }

    "4.3) getParent of TestCube/TestActorWithRoute" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute", "Parent")
      bean getOrElse "" should startWith ("Actor[pekko://ActorMonitorSpec/user/TestCube#")
    }

    "4.4) getChildren of TestCube/TestActorWithRoute" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute", "Children")
      bean getOrElse "" should include ("Actor[pekko://ActorMonitorSpec/user/TestCube/TestActorWithRoute/$a#")
    }

    "4.5) getDispatcher of TestCube/TestActorWithRoute" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor", "Dispatcher")
      bean should be (Some("pekko.actor.default-dispatcher"))
    }

    "4.6) getMailBoxSize of TestCube/TestActorWithRoute" in {
      ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor", "MailBoxSize") should be (Some("0"))
    }

    "5.0) getActor of TestCube/TestActorWithRoute/$a" in {
      awaitAssert(
        ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute/$a", "Actor")
          .getOrElse("") should startWith ("Actor[pekko://ActorMonitorSpec/user/TestCube/TestActorWithRoute/$a#"),
        max = awaitMax)
    }

    "5.1) getClassName of TestCube/TestActorWithRoute/$a" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute/$a", "ClassName")
      bean should be (Some("org.squbs.actormonitor.testcube.TestActorWithRoute"))
    }

    "5.2) getRouteConfig of TestCube/TestActorWithRoute/$a" in {
      ActorMonitorSpec.
        getActorMonitorBean("user/TestCube/TestActorWithRoute/$a", "RouteConfig") should be (Some("NoRouter"))
    }

    "5.3) getParent of TestCube/TestActorWithRoute/$a" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute/$a", "Parent") getOrElse ""
      bean should startWith ("Actor[pekko://ActorMonitorSpec/user/TestCube/TestActorWithRoute#")
    }

    "5.4) getChildren of TestCube/TestActorWithRoute/$a" in {
      ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute/$a", "Children") should be (Some(""))
    }

    "5.5) getDispatcher of TestCube/TestActorWithRoute/$a" in {
      val bean = ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute/$a", "Dispatcher")
      bean should be (Some("blocking-dispatcher"))
    }

    "5.6) getMailBoxSize of TestCube/TestActorWithRoute/$a" in {
      ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActorWithRoute/$a", "MailBoxSize") should be (Some("0"))
    }

    "6.1) getBean after actor has been stop" in {
      awaitAssert(ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor1", "Actor") shouldBe defined,
                  max = awaitMax)


      import ActorMonitorSpec._
      val originalNum = getActorMonitorConfigBean("Count").getOrElse(-100)
      originalNum should be > 0

      system.actorSelection("/user/TestCube/TestActor1") ! PoisonPill
      awaitAssert({
        ActorMonitorSpec.getActorMonitorBean("user/TestCube/TestActor1", "Actor") shouldBe empty
        getActorMonitorConfigBean("Count") should contain (originalNum - 1)
      }, max = awaitMax)
    }

    "7.0) ActorMonitorConfigBean" in {
      import ActorMonitorSpec._
      getActorMonitorConfigBean("MaxCount") should be (Some(500))
      getActorMonitorConfigBean("MaxChildrenDisplay") should be (Some(20))
    }
  }
}




