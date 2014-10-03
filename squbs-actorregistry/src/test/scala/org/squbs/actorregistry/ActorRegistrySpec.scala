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
package org.squbs.actorregistry

import java.lang.management.ManagementFactory
import javax.management.ObjectName

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout

import com.typesafe.config.ConfigFactory
import org.scalatest._
import org.scalatest.concurrent.AsyncAssertions
import org.squbs.actorregistry.testcube._

import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.JMX._
import org.squbs.unicomplex.{Unicomplex, JMX, UnicomplexBoot}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object ActorRegistrySpec {

  val dummyJarsDir = "squbs-actorregistry/src/test/resources/classpaths"

  val classPaths = Array(
   "ActorRegistryCube",
   "TestCube"
  ) map (dummyJarsDir + "/" + _)

  import scala.collection.JavaConversions._

  val mapConfig = ConfigFactory.parseMap(
    Map(
      "squbs.actorsystem-name"    -> "ActorRegistrySpec",
      "squbs." + JMX.prefixConfig -> Boolean.box(true),
      "default-listener.bind-port" -> org.squbs.nextPort.toString
    )
  )

  val boot = UnicomplexBoot(mapConfig)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()


  def getActorRegistryBean(actorName: String, att: String) =
    try {
      ManagementFactory.getPlatformMBeanServer.getAttribute(
        getObjName(actorName), att)
    } catch {
      case e: Exception =>
        null
    }

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

  implicit val timeout: akka.util.Timeout = Timeout(1 seconds)
  implicit val ec = system.dispatcher


  override def afterAll() {
    Unicomplex(system).uniActor ! GracefulStop
  }

  "ActorRegistry" must {

    "1) check ActorRegistry" in {
      system.actorSelection("/user/ActorRegistryCube/ActorRegistry") ! Identify("test")
      receiveOne(timeout.duration) match {
        case ActorIdentity(result, Some(ref)) =>
          assert(true)
        case _ =>
          assert(false)
      }
    }

    "1.1) check ActorRegistryConfigBean " in {
      assert(ActorRegistrySpec.getActorRegistryConfigBean("Count") == 2)
      assert(ActorRegistrySpec.getActorRegistryConfigBean("Timeout") == 10)
    }

    "2) check TestActor" in {
      system.actorSelection("/user/TestCube/TestActor") ! Identify("test")
      receiveOne(timeout.duration) match {
        case ActorIdentity(result, Some(ref)) =>
          assert(true)
        case _ =>
          assert(false)
      }
    }

    "3) check ActorRegistryBean" in {
      val bean = ActorRegistrySpec.getActorRegistryBean("TestCube/TestActor", "ActorMessageTypeList")
      assert(bean.toString != null)
    }


    "4.1) ActorLookup ! TestRequest(...)" in {
      ActorLookup ! TestRequest("ActorLookup")
      receiveOne(timeout.duration) match {
        case TestResponse(msg) =>
          assert(msg === "ActorLookup")
        case x =>
          assert(false)
      }
    }

    "4.2) ActorLookup ? TestRequest(...)" in {
      val f = ActorLookup ? TestRequest("ActorLookup")
      Try(Await.result(f, timeout.duration)) match {
        case x =>
          assert(x==Success(TestResponse("ActorLookup")))

      }
    }

    "5) ActorLookup() ! TestRequest(...)" in {
      ActorLookup() ! TestRequest("ActorLookup")
      receiveOne(timeout.duration) match {
        case TestResponse(msg) =>
          assert(msg === "ActorLookup")
        case x =>
          assert(false)
      }
    }

    "5.1) ActorLookup().resolveOne" in {
      val vFuture = ActorLookup().resolveOne(FiniteDuration(100, MILLISECONDS))
      Try(Await.result(vFuture, timeout.duration)) match {
        case x =>
          assert(x==Failure(org.squbs.actorregistry.ActorNotFound(ActorLookup(None,None,None))))
      }
    }

    "5.1) new ActorLookup(requestClass=Some(Class[TestRequest])).resolveOne" in {
      val l= new ActorLookup(requestClass=Some(classOf[TestRequest]))
      val vFuture = l.resolveOne(FiniteDuration(100, MILLISECONDS))
      Try(Await.result(vFuture, timeout.duration)) match {
        case Success(actor: ActorRef) =>
          assert(actor.path.name == "TestActor")
        case _ =>
          assert(false)
      }
    }


    "6.0) ActorLookup[TestResponse].resolveOne" in {
      val vFuture = ActorLookup[TestResponse].resolveOne
      Try(Await.result(vFuture, timeout.duration)) match {
        case Success(actor: ActorRef) =>
          assert(actor.path.name == "TestActor")
        case _ =>
          assert(false)
      }

    }

    "6.1) ActorLookup[TestResponse] ! TestRequest(...)" in {
      ActorLookup[TestResponse] ! TestRequest("ActorLookup[TestResponse]")
      receiveOne(timeout.duration) match {
        case TestResponse(msg) =>
          assert(msg === "ActorLookup[TestResponse]")
        case x =>
          assert(false)
      }
    }

    "6.2) ActorLookup[TestResponse] ? TestRequest(...)" in {
      val f = ActorLookup[TestResponse] ? TestRequest("ActorLookup[TestResponse]")
      Try(Await.result(f, timeout.duration)) match {
        case x =>
          assert(x == Success(TestResponse("ActorLookup[TestResponse]")))
      }
    }

    "7) ActorLookup('TestActor') ! TestRequest(...)" in {
      ActorLookup("TestActor") ! TestRequest("ActorLookup('TestActor')")
      receiveOne(timeout.duration) match {
        case TestResponse(msg) =>
          assert(msg === "ActorLookup('TestActor')")
        case x =>
          assert(false)
      }
    }

    "8) ActorLookup[TestResponse]('TestActor') ! TestRequest(...)" in {
      ActorLookup[TestResponse]("TestActor") ! TestRequest("ActorLookup[TestResponse]('TestActor')")
      receiveOne(timeout.duration) match {
        case TestResponse(msg) =>
          assert(msg === "ActorLookup[TestResponse]('TestActor')")
        case x =>
          assert(false)
      }
    }

    "9) ActorLookup[TestResponse] ! Identify" in {
      ActorLookup[TestResponse] ! Identify(3)
      receiveOne(timeout.duration) match {
        case ActorIdentity(3,Some(x:ActorRef))=>
          assert(x.path.name === "TestActor")
        case x =>
          assert(false)
      }
    }

    "10) ActorLookup[TestResponse] ! TestRequest" in {
      ActorLookup[TestResponse] ! TestRequest
      receiveOne(timeout.duration) match {
        case x =>
          assert(x === TestResponse)
      }
    }

    "11) ActorLookup[TestResponse] ! TestRequest" in {
      ActorLookup[TestResponse] ! TestRequest
      receiveOne(timeout.duration) match {
        case x =>
          assert(x === TestResponse)
      }
    }

    "12) ActorLookup[String] ! NotExist " in {
      ActorLookup[String] ! "NotExist"
      receiveOne(timeout.duration) match {
        case msg : ActorNotFound =>
            assert(true)
        case _ =>
            assert(false)
      }
    }

    "13) ActorLookup ! PoisonPill" in {
      val before = ActorRegistrySpec.getActorRegistryBean("TestCube/TestActor1", "ActorMessageTypeList")
      assert(before != null)
      ActorLookup("TestActor1") ! PoisonPill
      receiveOne(timeout.duration) match {
        case msg =>
            val after = ActorRegistrySpec.getActorRegistryBean("TestCube/TestActor1", "ActorMessageTypeList")
            assert(after == null)
      }
    }

    "14) kill ActorRegistry" in {
      system.actorSelection("/user/ActorRegistryCube/ActorRegistry") ! PoisonPill
      receiveOne(timeout.duration) match {
        case msg =>
            val after = ManagementFactory.getPlatformMBeanServer.queryNames(ActorRegistrySpec.getObjName("*"), null)
            assert(after.size == 0)
      }
    }
  }
}


