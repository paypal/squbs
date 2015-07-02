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

import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit
import javax.management.ObjectName

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest._
import org.scalatest.concurrent.AsyncAssertions
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.dummyextensions.DummyExtension
import spray.util.Utils

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try

object BadUnicomplexBootSpec {

  val dummyJarsDir = getClass.getClassLoader.getResource("classpaths").getPath

  val classPaths = Array(
    "BadUnicomplexBoot",
    "BadCube",
    "BadCube1",
    "BadCube2",
    "NoMetaCube"
  ) map (dummyJarsDir + "/" + _)

  val (_, port) = Utils.temporaryServerHostnameAndPort()

  val config = ConfigFactory.parseString(
    s"""
       |squbs {
       |  actorsystem-name = BadUnicomplexBootSpec
       |  ${JMX.prefixConfig} = true
       |}
       |default-listener.bind-port = $port
    """.stripMargin
  ) withFallback ConfigFactory.parseString(
    """
      |
      |akka.actor.deployment {
      |  /BadUnicomplexBoot/Prepender {
      |    router = round-robin-pool
      |    nr-of-instances = 5
      |  }
      |}
      |
      |
    """.stripMargin
  )

  //for coverage
  UnicomplexBoot { (name, config) => ActorSystem(name, config) }

  val boot = UnicomplexBoot(config)
    .createUsing {
    (name, config) => ActorSystem(name, config)
  }
    .scanComponents(classPaths)
    .initExtensions.start()


}

class BadUnicomplexBootSpec extends TestKit(BadUnicomplexBootSpec.boot.actorSystem) with ImplicitSender
with WordSpecLike with Matchers with Inspectors with BeforeAndAfterAll
with AsyncAssertions {

  import org.squbs.unicomplex.BadUnicomplexBootSpec._

  implicit val timeout: akka.util.Timeout =
    Try(System.getProperty("test.timeout").toLong) map {
      millis =>
        akka.util.Timeout(millis, TimeUnit.MILLISECONDS)
    } getOrElse (10 seconds)

  implicit val executionContext = system.dispatcher

  override def afterAll() {
    Unicomplex(system).uniActor ! GracefulStop
  }

  "UnicomplexBoot" must {

    "start all cube actors" in {
      val w = new Waiter

      system.actorSelection("/user/BadUnicomplexBoot").resolveOne().onComplete(result => {
        w {
          assert(result.isSuccess)
        }
        w.dismiss()
      })
      w.await()

      system.actorSelection("/user/BadUnicomplexBoot/AppendActor").resolveOne().onComplete(result => {
        w {
          assert(result.isSuccess)
        }
        w.dismiss()
      })
      w.await()

      system.actorSelection("/user/BadUnicomplexBoot/Prepender").resolveOne().onComplete(result => {
        w {
          assert(result.isSuccess)
        }
        w.dismiss()
      })
      w.await()


    }

    "check cube MXbean" in {
      import JMX._
      val mbeanServer = ManagementFactory.getPlatformMBeanServer
      val cubesObjName = new ObjectName(prefix(system) + cubesName)
      val attr = mbeanServer.getAttribute(cubesObjName, "Cubes")
      attr shouldBe a [Array[Any]]
      all (attr.asInstanceOf[Array[Any]]) shouldBe a [javax.management.openmbean.CompositeData]
      // 5 cubes registered above.
      val cAttr = attr.asInstanceOf[Array[_]]
      forAll (cAttr) (_ shouldBe a [javax.management.openmbean.CompositeData])
      attr.asInstanceOf[Array[_]] should have size 1
    }

    "preInit, init and postInit all extensions" in {
      boot.extensions.size should be (2)
      //boot.extensions.forall(_.extLifecycle.get.isInstanceOf[DummyExtension]) should be(true)
      boot.extensions(0).extLifecycle.get.asInstanceOf[DummyExtension].state should be ("CstartpreInitpostInit")
      boot.extensions(1).extLifecycle should be (None)

    }

    "start again" in {
      the[IllegalStateException] thrownBy {
        boot.start()
      } should have message "Unicomplex already started!"


    }

    "stopJVMOnExit" in {
      boot.stopJVMOnExit shouldBe 'stopJVM
    }

    "externalConfigDir" in {
      boot.externalConfigDir should be("squbsconfig")
    }

    "Constants" in {
      UnicomplexBoot.extConfigDirKey should be("squbs.external-config-dir")
      UnicomplexBoot.extConfigNameKey should be("squbs.external-config-files")
      UnicomplexBoot.actorSystemNameKey should be("squbs.actorsystem-name")
    }
  }
}
