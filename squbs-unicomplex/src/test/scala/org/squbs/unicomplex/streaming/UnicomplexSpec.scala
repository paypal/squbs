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

import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit
import javax.management.ObjectName

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpEntity, StatusCodes}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest._
import org.scalatest.concurrent.{Eventually, AsyncAssertions}
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex._
import org.squbs.unicomplex.UnicomplexBoot.StartupType
import org.squbs.unicomplex.dummyextensions.DummyExtension
import org.squbs.unicomplex.streaming.dummysvcactor.GetWebContext

import scala.concurrent.Await
import scala.language.postfixOps
import scala.util.Try

object UnicomplexSpec {

  val dummyJarsDir = getClass.getClassLoader.getResource("classpaths/streaming").getPath

  val classPaths = Array(
    "DummyCube",
    "DummyCubeSvc",
    "DummySvc",
    "DummySvcActor",
    "StashCube",
    "DummyExtensions.jar"
  ) map (dummyJarsDir + "/" + _)

  val (_, _, port) = temporaryServerHostnameAndPort()

  val config = ConfigFactory.parseString(
    s"""
       |squbs {
       |  actorsystem-name = streaming-UnicomplexSpec
       |  ${JMX.prefixConfig} = true
       |  experimental-mode-on = true
       |}
       |default-listener.bind-port = $port
    """.stripMargin
  )

  val boot = UnicomplexBoot(config)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()

}

class UnicomplexSpec extends TestKit(UnicomplexSpec.boot.actorSystem) with ImplicitSender
                             with WordSpecLike with Matchers with Inspectors with BeforeAndAfterAll
                             with AsyncAssertions with Eventually {

  import org.squbs.unicomplex.UnicomplexSpec._
  import system.dispatcher

  implicit val am = ActorMaterializer()

  implicit val timeout: akka.util.Timeout =
  Try(System.getProperty("test.timeout").toLong) map { millis =>
      akka.util.Timeout(millis, TimeUnit.MILLISECONDS)
    } getOrElse Timeouts.askTimeout

  val port = system.settings.config getInt "default-listener.bind-port"


  override def afterAll() {
    Unicomplex(system).uniActor ! GracefulStop
  }

  "UnicomplexBoot" must {

    "start all cube actors" in {
      val w = new Waiter

      system.actorSelection("/user/DummyCube").resolveOne().onComplete { result =>
        w {assert(result.isSuccess)}
        w.dismiss()
      }
      w.await()

      system.actorSelection("/user/DummyCubeSvc").resolveOne().onComplete { result =>
        w {assert(result.isSuccess)}
        w.dismiss()
      }
      w.await()

      system.actorSelection("/user/DummyCube/Appender").resolveOne().onComplete { result =>
        w {assert(result.isSuccess)}
        w.dismiss()
      }
      w.await()

      system.actorSelection("/user/DummyCube/Prepender").resolveOne().onComplete { result =>
        w {assert(result.isSuccess)}
        w.dismiss()
      }
      w.await()

      system.actorSelection("/user/DummyCubeSvc/PingPongPlayer").resolveOne().onComplete { result =>
        w {assert(result.isSuccess)}
        w.dismiss()
      }
      w.await()

      system.actorSelection("/user/StashCube").resolveOne().onComplete { result =>
        w {assert(result.isSuccess)}
        w.dismiss()
      }
      w.await()
    }

    "start all services" in {
      val services = boot.cubes flatMap { cube => cube.components.getOrElse(StartupType.SERVICES, Seq.empty) }
      assert(services.size == 6)

      Await.result(entityAsString(s"http://127.0.0.1:$port/dummysvc/msg/hello"), timeout.duration) should be ("^hello$")

      Await.result(entityAsString(s"http://127.0.0.1:$port/dummy2svc/v1/msg/hello"), timeout.duration) should be ("^hello$")
      // This implementation reverses the message
      Await.result(entityAsString(s"http://127.0.0.1:$port/dummy2svc/msg/hello"), timeout.duration) should be ("^olleh$")

      Await.result(entityAsString(s"http://127.0.0.1:$port/pingpongsvc/ping"), timeout.duration) should be ("Pong")

      Await.result(entityAsString(s"http://127.0.0.1:$port/pingpongsvc/pong"), timeout.duration) should be ("Ping")

      Await.result(entityAsString(s"http://127.0.0.1:$port/dummysvcactor/ping"), timeout.duration) should be ("pong")

      Await.result(post(s"http://127.0.0.1:$port/withstash/", HttpEntity("request message")),
        timeout.duration).status should be (StatusCodes.Accepted)

      Await.result(entityAsString(s"http://127.0.0.1:$port/withstash/"), timeout.duration) should be (Seq.empty[String].toString)

      Await.result(put(s"http://127.0.0.1:$port/withstash/"), timeout.duration).status should be (StatusCodes.Created)

      // Whether messages from unstashall or the message from this request will be the first message to StashCubeSvc
      // is not deterministic.  So, running it in eventually.
      eventually {
        Await.result(entityAsString(s"http://127.0.0.1:$port/withstash/"), timeout.duration) should be (Seq("request message").toString)
      }
    }

    "service actor with WebContext must have a WebContext" in {
      val w = new Waiter
      for {
        svcActor <- system.actorSelection("/user/DummySvcActor/dummysvcactor-DummySvcActor-handler").resolveOne()
        result   <- (svcActor ? GetWebContext).mapTo[String]
      } {
        w { result should be ("dummysvcactor") }
        w.dismiss()
      }
      w.await()
    }

    "check cube MXbean" in {
      import org.squbs.unicomplex.JMX._
      val mBeanServer = ManagementFactory.getPlatformMBeanServer
      val cubesObjName = new ObjectName(prefix(system) + cubesName)
      val attr = mBeanServer.getAttribute(cubesObjName, "Cubes")
      attr shouldBe a [Array[Any]]
      val attrs = attr.asInstanceOf[Array[_]]

      // 6 cubes registered above.
      attrs should have size 6
      all (attrs) shouldBe a [javax.management.openmbean.CompositeData]
    }

    "check cube state MXbean" in {
      import org.squbs.unicomplex.JMX._
      val cubeName = "DummyCube"
      val mBeanServer = ManagementFactory.getPlatformMBeanServer
      val cubesObjName = new ObjectName(prefix(system) + cubeStateName + cubeName)
      val name = mBeanServer.getAttribute(cubesObjName, "Name")
      name should be (cubeName)

      val cubeState = mBeanServer.getAttribute(cubesObjName, "CubeState")
      cubeState should be ("Active")

      val WellKnownActors = mBeanServer.getAttribute(cubesObjName, "WellKnownActors").asInstanceOf[String]
      WellKnownActors should include ("Actor[akka://streaming-UnicomplexSpec/user/DummyCube/Prepender#")
      WellKnownActors should include ("Actor[akka://streaming-UnicomplexSpec/user/DummyCube/Appender#")
    }

    "check listener MXbean" in {
      import org.squbs.unicomplex.JMX._
      val mBeanServer = ManagementFactory.getPlatformMBeanServer
      val listenersObjName = new ObjectName(prefix(system) + listenersName)
      val ls = mBeanServer.getAttribute(listenersObjName, "Listeners")
      ls shouldBe a [Array[Any]]
      val listeners = ls.asInstanceOf[Array[_]]
      all (listeners) shouldBe a [javax.management.openmbean.CompositeData]

      // 6 services registered on one listener
      listeners should have size 6

    }

    "check extension MBean" in {
      import org.squbs.unicomplex.JMX._
      val mBeanServer = ManagementFactory.getPlatformMBeanServer
      val extensionObjName = new ObjectName(prefix(system) + extensionsName)
      val xs = mBeanServer.getAttribute(extensionObjName, "Extensions")
      xs shouldBe a [Array[Any]]
      val extensions = xs.asInstanceOf[Array[_]]
      all (extensions) shouldBe a [javax.management.openmbean.CompositeData]
      extensions should have size 2
    }

    "preInit, init, preCubesInit   and postInit all extensions" in {
      boot.extensions.size should be (2)
      forAll (boot.extensions) { _.extLifecycle.get shouldBe a [DummyExtension]}
      boot.extensions(0).extLifecycle.get.asInstanceOf[DummyExtension].state should be ("AstartpreInitinitpreCubesInitpostInit")
      boot.extensions(1).extLifecycle.get.asInstanceOf[DummyExtension].state should be ("BstartpreInitinitpreCubesInitpostInit")
    }

    "ReportStatus" in {
      Unicomplex(system).uniActor ! ReportStatus
      expectMsgPF(){
        case StatusReport(state, _, _) =>
          state should be(Active)
      }
    }
  }
}
