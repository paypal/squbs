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
package org.squbs.actorregistry

import org.apache.pekko.actor.{ActorIdentity, ActorRef, ActorSystem, Identify, PoisonPill}
import org.apache.pekko.testkit.{ImplicitSender, TestKit}

import java.lang.management.ManagementFactory
import javax.management.ObjectName
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.squbs.actorregistry.testcube._
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.JMX._
import org.squbs.unicomplex.{JMX, Unicomplex, UnicomplexBoot}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class TestBootstrap(configContent: String, cubeEntries: String*) {

  val dummyJarsDir = getClass.getClassLoader.getResource("classpaths").getPath

  val classPaths = cubeEntries map (dummyJarsDir + "/" + _)

  val config = ConfigFactory.parseString(configContent)

  val boot = UnicomplexBoot(config)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()


  def getActorRegistryBean(actorName: String, att: String) =
    Try { ManagementFactory.getPlatformMBeanServer.getAttribute(getObjName(actorName), att) } .toOption

  def getObjName(name: String) = new ObjectName(prefix(boot.actorSystem) + ActorRegistryBean.Pattern + name)

  def getActorRegistryConfigBean(att: String) =
    Try {
      val o = new ObjectName(prefix(boot.actorSystem) + "org.squbs.unicomplex:type=ActorRegistry")
      ManagementFactory.getPlatformMBeanServer.getAttribute(o, att).asInstanceOf[Int]
    } .toOption
}

abstract class ActorRegistrySpec(testBootstrap: TestBootstrap) extends TestKit(testBootstrap.boot.actorSystem)
    with ImplicitSender with AnyWordSpecLike with Matchers with BeforeAndAfterAll {

  import org.squbs.testkit.Timeouts._


  override def afterAll(): Unit = {
    Unicomplex(system).uniActor ! GracefulStop
  }

  "ActorRegistry" must {

    "1) check ActorRegistry" in {
      system.actorSelection("/user/ActorRegistryCube/ActorRegistry") ! Identify("test")
      receiveOne(awaitMax) should matchPattern { case ActorIdentity(_, Some(_)) => }
    }

    "1.1) check ActorRegistryConfigBean " in {
      testBootstrap.getActorRegistryConfigBean("Count") should be (Some(2))
      testBootstrap.getActorRegistryConfigBean("Timeout") should be (Some(1000))
    }

    "2) check TestActor" in {
      system.actorSelection("/user/TestCube/TestActor") ! Identify("test")
      receiveOne(awaitMax) should matchPattern { case ActorIdentity(_, Some(_)) => }
    }

    "3) check ActorRegistryBean" in {
      testBootstrap.getActorRegistryBean("TestCube/TestActor", "ActorMessageTypeList") should not be empty
    }


    "4.1) ActorLookup ! TestRequest(...)" in {
      ActorLookup ! TestRequest("ActorLookup")
      receiveOne(awaitMax) should matchPattern { case TestResponse("ActorLookup") => }
    }

    "4.2) ActorLookup ? TestRequest(...)" in {
      val f = ActorLookup ? TestRequest("ActorLookup")
      Try(Await.result(f, awaitMax)) should matchPattern { case Success(TestResponse("ActorLookup")) => }
    }

    "5) ActorLookup() ! TestRequest(...)" in {
      ActorLookup() ! TestRequest("ActorLookup")
      receiveOne(awaitMax) should matchPattern { case TestResponse("ActorLookup") => }
    }

    "5.1) ActorLookup().resolveOne" in {
      val vFuture = ActorLookup().resolveOne(FiniteDuration(100, MILLISECONDS))
      Try(Await.result(vFuture, awaitMax)) should matchPattern {
        case Failure(org.squbs.actorregistry.ActorNotFound(ActorLookup(_, None, None, false))) =>
      }
    }

    "5.2) ActorLookup('TestActor').resolveOne" in {
      val vFuture = ActorLookup("TestActor").resolveOne(awaitMax)
      Try(Await.result(vFuture, awaitMax)) should matchPattern {
        case Success(actor: ActorRef) if actor.path.name === "TestActor" =>
      }
    }

    "5.3) ActorLookup(requestClass=classOf[TestRequest]).resolveOne" in {
      val l = ActorLookup(requestClass = classOf[TestRequest])
      val vFuture = l.resolveOne(FiniteDuration(100, MILLISECONDS))
      Try(Await.result(vFuture, awaitMax)) should matchPattern {
        case Success(actor: ActorRef) if actor.path.name === "TestActor" =>
      }
    }

    "5.4) ActorLookup(requestClass=Option(classOf[TestRequest]),Option('TestActor')).resolveOne" in {
      val l = ActorLookup(requestClass = Option(classOf[TestRequest]), actorName = Option("TestActor"))
      val vFuture = l.resolveOne(FiniteDuration(100, MILLISECONDS))
      Try(Await.result(vFuture, awaitMax)) should matchPattern {
        case Success(actor: ActorRef) if actor.path.name === "TestActor" =>
      }
    }

    "5.5) ActorLookup('InvalidActor').resolveOne returns Failure(ActorNotFound)" in {
      val vFuture = ActorLookup("InvalidActor").resolveOne(awaitMax)
      Try(Await.result(vFuture, awaitMax)) should matchPattern {
        case Failure(nf: ActorNotFound) =>
      }
    }

    "6.0) ActorLookup[TestResponse].resolveOne" in {
      val vFuture = ActorLookup[TestResponse].resolveOne
      Try(Await.result(vFuture, awaitMax)) should matchPattern {
        case Success(actor: ActorRef) if actor.path.name === "TestActor" =>
      }
    }

    "6.1) ActorLookup[TestResponse] ! TestRequest(...)" in {
      ActorLookup[TestResponse] ! TestRequest("ActorLookup[TestResponse]")
      receiveOne(awaitMax) should matchPattern { case TestResponse("ActorLookup[TestResponse]") => }
    }

    "6.2) ActorLookup[TestResponse] ? TestRequest(...)" in {
      val f = ActorLookup[TestResponse] ? TestRequest("ActorLookup[TestResponse]")
      Try(Await.result(f, awaitMax)) should matchPattern {
        case Success(TestResponse("ActorLookup[TestResponse]")) =>
      }
     }

    "7) ActorLookup('TestActor') ! TestRequest(...)" in {
      ActorLookup("TestActor") ! TestRequest("ActorLookup('TestActor')")
      receiveOne(awaitMax) should matchPattern { case TestResponse("ActorLookup('TestActor')") => }
    }

    "8) ActorLookup[TestResponse]('TestActor') ! TestRequest(...)" in {
      ActorLookup[TestResponse]("TestActor") ! TestRequest("ActorLookup[TestResponse]('TestActor')")
      receiveOne(awaitMax) should matchPattern {
        case TestResponse("ActorLookup[TestResponse]('TestActor')") =>
      }
    }

    "9) ActorLookup[TestResponse] ! Identify" in {
      ActorLookup[TestResponse] ! Identify(3)
      receiveOne(awaitMax) should matchPattern {
        case ActorIdentity(3, Some(actor: ActorRef)) if actor.path.name === "TestActor" =>
      }
    }

    "10) ActorLookup[TestResponse] ! TestRequest" in {
      ActorLookup[TestResponse] ! TestRequest
      receiveOne(awaitMax) should be (TestResponse)
    }

    "11) ActorLookup[TestResponse] ! TestRequest" in {
      ActorLookup[TestResponse] ! TestRequest
      receiveOne(awaitMax) should be (TestResponse)
    }

    "12.0) ActorLookup[String] ! NotExist " in {
      ActorLookup[String] ! "NotExist"
      receiveOne(awaitMax) shouldBe an [ActorNotFound]
    }

    "12.1) ActorLookup[Long] ! NotExist " in {
      ActorLookup[String] ! "NotExist"
      receiveOne(awaitMax) shouldBe an [ActorNotFound]
    }

    "13) ActorLookup('TestActor1') ! TestRequest1" in {
      val before = testBootstrap.getActorRegistryBean("TestCube/TestActor1", "ActorMessageTypeList")
      before should not be empty
      ActorLookup[String]("TestActor1") ! TestRequest1("13")
      receiveOne(awaitMax) shouldBe an [ActorNotFound]
    }

    "14) ActorLookup[String]('TestActor1') ! TestRequest1" in {
      val before = testBootstrap.getActorRegistryBean("TestCube/TestActor1", "ActorMessageTypeList")
      before should not be empty
      ActorLookup("TestActor1") ! TestRequest1("13")
      receiveOne(awaitMax) should matchPattern { case TestResponse("13") => }
    }

    "15) ActorLookup ! PoisonPill" in {
      val before = testBootstrap.getActorRegistryBean("TestCube/TestActor1", "ActorMessageTypeList")
      before should not be empty
      ActorLookup("TestActor1") ! PoisonPill
      awaitAssert(
        testBootstrap.getActorRegistryBean("TestCube/TestActor1", "ActorMessageTypeList") shouldBe empty,
        max = awaitMax)
    }

    "16) kill ActorRegistry" in {
      system.actorSelection("/user/ActorRegistryCube/ActorRegistry") ! PoisonPill
      awaitAssert(
        ManagementFactory.getPlatformMBeanServer.queryNames(testBootstrap.getObjName("*"), null) shouldBe empty,
        max = awaitMax)
    }
  }
}

object PlainBootstrap extends TestBootstrap(
  s"""
     |squbs {
     |  actorsystem-name = ActorRegistrySpec
     |  ${JMX.prefixConfig} = true
     |}
     |default-listener.bind-port = 0
    """.stripMargin,
  "ActorRegistryCube", "TestCube"
)

class PlainActorRegistrySpec extends ActorRegistrySpec(PlainBootstrap)

object RouterBootstrap extends TestBootstrap(
  s"""
     |squbs {
     |  actorsystem-name = RouterActorRegistrySpec
     |  ${JMX.prefixConfig} = true
     |}
     |default-listener.bind-port = 0
     |
     |pekko.actor.deployment {
     |
     |  # Router configuration
     |  /TestCube/TestActor {
     |    router = round-robin-pool
     |    nr-of-instances = 3
     |  }
     |}
    """.stripMargin,
  "ActorRegistryCube", "TestCubeWithRouter"
)

class RouterActorRegistrySpec extends ActorRegistrySpec(RouterBootstrap)

