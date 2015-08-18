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
import akka.io.IO
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest._
import org.scalatest.concurrent.AsyncAssertions
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.UnicomplexBoot.StartupType
import org.squbs.unicomplex.dummyextensions.DummyExtension
import org.squbs.unicomplex.dummysvcactor.GetWebContext
import spray.can.Http
import spray.http._
import spray.util.Utils

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try

object UnicomplexSpec {

  val dummyJarsDir = getClass.getClassLoader.getResource("classpaths").getPath

  val classPaths = Array(
    "DummyCube",
    "DummyCubeSvc",
    "DummySvc",
    "DummySvcActor",
    "StashCube",
    "DummyExtensions.jar"
  ) map (dummyJarsDir + "/" + _)

  val (_, port) = Utils.temporaryServerHostnameAndPort()

  val config = ConfigFactory.parseString(
    s"""
       |squbs {
       |  actorsystem-name = unicomplexSpec
       |  ${JMX.prefixConfig} = true
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
                             with AsyncAssertions {

  import org.squbs.unicomplex.UnicomplexSpec._

  implicit val timeout: akka.util.Timeout =
    Try(System.getProperty("test.timeout").toLong) map { millis =>
      akka.util.Timeout(millis, TimeUnit.MILLISECONDS)
    } getOrElse (10 seconds)

  val port = system.settings.config getInt "default-listener.bind-port"

  implicit val executionContext = system.dispatcher

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
      IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/dummysvc/msg/hello"))
      within(timeout.duration) {
        val response = expectMsgType[HttpResponse]
        response.status should be(StatusCodes.OK)
        response.entity.asString should be("^hello$")
      }

      IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/dummy2svc/v1/msg/hello"))
      within(timeout.duration) {
        val response = expectMsgType[HttpResponse]
        response.status should be(StatusCodes.OK)
        response.entity.asString should be("^hello$")
      }

      IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/dummy2svc/msg/hello"))
      within(timeout.duration) {
        val response = expectMsgType[HttpResponse]
        response.status should be(StatusCodes.OK)
        response.entity.asString should be("^olleh$") // This implementation reverses the message
      }

      IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/pingpongsvc/ping"))
      within(timeout.duration) {
        val response = expectMsgType[HttpResponse]
        response.status should be(StatusCodes.OK)
        response.entity.asString should be("Pong")
      }

      IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/pingpongsvc/pong"))
      within(timeout.duration) {
        val response = expectMsgType[HttpResponse]
        response.status should be(StatusCodes.OK)
        response.entity.asString should be("Ping")
      }

      IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/dummysvcactor/ping"))
      within(timeout.duration) {
        val response = expectMsgType[HttpResponse]
        response.status should be(StatusCodes.OK)
        response.entity.asString should be("pong")
      }

      IO(Http) ! HttpRequest(HttpMethods.POST, Uri(s"http://127.0.0.1:$port/withstash/"), entity = HttpEntity("request message"))
      within(timeout.duration) {
        val resp0 = expectMsgType[HttpResponse]
        resp0.status shouldBe StatusCodes.Accepted
      }

      IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/withstash/"))
			within(timeout.duration) {
        val resp1 = expectMsgType[HttpResponse]
        resp1.status shouldBe StatusCodes.OK
        resp1.entity.asString shouldBe Seq.empty[String].toString
      }

      IO(Http) ! HttpRequest(HttpMethods.PUT, Uri(s"http://127.0.0.1:$port/withstash/"))
      within(timeout.duration) {
        val resp0 = expectMsgType[HttpResponse]
        resp0.status shouldBe StatusCodes.Created
      }

      IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/withstash/"))
			within(timeout.duration) {
				val resp2 = expectMsgType[HttpResponse]
				resp2.status shouldBe StatusCodes.OK
				resp2.entity.asString shouldBe Seq("request message").toString // This line fails inconsistently.
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
      println(WellKnownActors)
      WellKnownActors should include ("Actor[akka://unicomplexSpec/user/DummyCube/Prepender#")
      WellKnownActors should include ("Actor[akka://unicomplexSpec/user/DummyCube/Appender#")
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

    "preInit, init and postInit all extensions" in {
      boot.extensions.size should be (2)
      forAll (boot.extensions) { _.extLifecycle.get shouldBe a [DummyExtension]}
      boot.extensions(0).extLifecycle.get.asInstanceOf[DummyExtension].state should be ("AstartpreInitinitpostInit")
      boot.extensions(1).extLifecycle.get.asInstanceOf[DummyExtension].state should be ("BstartpreInitinitpostInit")
    }

    "ReportStatus" in {
      Unicomplex(system).uniActor ! ReportStatus
      expectMsgPF(){
        case (state, b, exts) =>
          state should be(Active)
      }
    }
  }
}
