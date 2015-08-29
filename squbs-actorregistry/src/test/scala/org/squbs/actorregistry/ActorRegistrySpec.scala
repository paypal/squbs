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

package org.squbs.actorregistry

import java.lang.management.ManagementFactory
import javax.management.ObjectName

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest._
import org.scalatest.concurrent.AsyncAssertions
import org.squbs.actorregistry.testcube._
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.JMX._
import org.squbs.unicomplex.{JMX, Unicomplex, UnicomplexBoot}
import spray.util.Utils

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object ActorRegistrySpec {

  val dummyJarsDir = getClass.getClassLoader.getResource("classpaths").getPath

  val classPaths = Array(
   "ActorRegistryCube",
   "TestCube"
  ) map (dummyJarsDir + "/" + _)

  val (_, port) = Utils.temporaryServerHostnameAndPort()

  val config = ConfigFactory.parseString(
    s"""
       |squbs {
       |  actorsystem-name = ActorRegistrySpec
       |  ${JMX.prefixConfig} = true
       |}
       |default-listener.bind-port = $port
    """.stripMargin)

  val boot = UnicomplexBoot(config)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()


  def getActorRegistryBean(actorName: String, att: String) =
    Try { ManagementFactory.getPlatformMBeanServer.getAttribute(getObjName(actorName), att) } .toOption

  def getObjName(name: String) = new ObjectName(prefix(boot.actorSystem) + ActorRegistryBean.Pattern + name)

  def getActorRegistryConfigBean(att: String) =
    try {
      val o = new ObjectName(prefix(boot.actorSystem) + "org.squbs.unicomplex:type=ActorRegistry")
      ManagementFactory.getPlatformMBeanServer.getAttribute(o, att).asInstanceOf[Int]
    } catch {
      case e: Exception =>
        null
    }
}

class ActorRegistrySpec extends TestKit(ActorRegistrySpec.boot.actorSystem) with ImplicitSender
                             with WordSpecLike with Matchers with BeforeAndAfterAll
                             with AsyncAssertions {

  import org.squbs.testkit.Timeouts._
  implicit val ec = system.dispatcher


  override def afterAll() {
    Unicomplex(system).uniActor ! GracefulStop
  }

  "ActorRegistry" must {

    "1) check ActorRegistry" in {
      system.actorSelection("/user/ActorRegistryCube/ActorRegistry") ! Identify("test")
      receiveOne(awaitMax) should matchPattern { case ActorIdentity(_, Some(_)) => }
    }

    "1.1) check ActorRegistryConfigBean " in {
      ActorRegistrySpec.getActorRegistryConfigBean("Count") should be (2)
      ActorRegistrySpec.getActorRegistryConfigBean("Timeout") should be (1000)
    }

    "2) check TestActor" in {
      system.actorSelection("/user/TestCube/TestActor") ! Identify("test")
      receiveOne(awaitMax) should matchPattern { case ActorIdentity(_, Some(_)) => }
    }

    "3) check ActorRegistryBean" in {
      ActorRegistrySpec.getActorRegistryBean("TestCube/TestActor", "ActorMessageTypeList") should not be empty
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
        case Failure(org.squbs.actorregistry.ActorNotFound(ActorLookup(None,None,None))) =>
      }
    }

    "5.2) ActorLookup('TestActor').resolveOne" in {
      val vFuture = ActorLookup("TestActor").resolveOne(awaitMax)
      Try(Await.result(vFuture, awaitMax)) should matchPattern {
        case Success(actor: ActorRef) if actor.path.name === "TestActor" =>
      }
    }

    "5.3) new ActorLookup(requestClass=Some(Class[TestRequest])).resolveOne" in {
      val l= new ActorLookup(requestClass=Some(classOf[TestRequest]))
      val vFuture = l.resolveOne(FiniteDuration(100, MILLISECONDS))
      Try(Await.result(vFuture, awaitMax)) should matchPattern {
        case Success(actor: ActorRef) if actor.path.name === "TestActor" =>
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

    "12) ActorLookup[String] ! NotExist " in {
      ActorLookup[String] ! "NotExist"
      receiveOne(awaitMax) shouldBe an [ActorNotFound]
    }

    "13) ActorLookup('TestActor1') ! TestRequest1" in {
      val before = ActorRegistrySpec.getActorRegistryBean("TestCube/TestActor1", "ActorMessageTypeList")
      before should not be empty
      ActorLookup[String]("TestActor1") ! TestRequest1("13")
      receiveOne(awaitMax) shouldBe an [ActorNotFound]
    }

    "14) ActorLookup[String]('TestActor1') ! TestRequest1" in {
      val before = ActorRegistrySpec.getActorRegistryBean("TestCube/TestActor1", "ActorMessageTypeList")
      before should not be empty
      ActorLookup("TestActor1") ! TestRequest1("13")
      receiveOne(awaitMax) should matchPattern { case TestResponse("13") => }
    }

    "15) ActorLookup ! PoisonPill" in {
      val before = ActorRegistrySpec.getActorRegistryBean("TestCube/TestActor1", "ActorMessageTypeList")
      before should not be empty
      ActorLookup("TestActor1") ! PoisonPill

      receiveOne(awaitMax)
      ActorRegistrySpec.getActorRegistryBean("TestCube/TestActor1", "ActorMessageTypeList") shouldBe empty
    }

    "16) kill ActorRegistry" in {
      system.actorSelection("/user/ActorRegistryCube/ActorRegistry") ! PoisonPill
      receiveOne(awaitMax)
      ManagementFactory.getPlatformMBeanServer.queryNames(ActorRegistrySpec.getObjName("*"), null) shouldBe empty
    }
  }
}


