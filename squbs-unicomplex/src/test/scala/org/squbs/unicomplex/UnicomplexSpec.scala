/*
 * Copyright 2017 PayPal
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

package org.squbs.unicomplex

import java.lang.management.ManagementFactory
import javax.management.ObjectName
import javax.management.openmbean.CompositeData
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.HttpEntity.Chunked
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import org.apache.pekko.pattern.ask
import org.apache.pekko.stream.ActorMaterializer
import org.apache.pekko.stream.scaladsl.{Keep, Sink, Source}
import org.apache.pekko.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Inspectors}
import org.scalatest.concurrent.{Eventually, Waiters}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.Timeouts._
import org.squbs.unicomplex.UnicomplexBoot.StartupType
import org.squbs.unicomplex.dummyextensions.DummyExtension
import org.squbs.unicomplex.dummysvcactor.GetWebContext

import scala.concurrent.Await
import scala.language.postfixOps

object UnicomplexSpec {

  val dummyJarsDir = getClass.getClassLoader.getResource("classpaths").getPath

  val classPaths = Array(
    "DummyCube",
    "DummyCubeSvc",
    "DummySvc",
    "DummySvcActor",
    "DummyFlowSvc",
    "StashCube",
    "DummyExtensions"
  ) map (dummyJarsDir + "/" + _)

  val config = ConfigFactory.parseString(
    s"""
       |squbs {
       |  actorsystem-name = UnicomplexSpec
       |  ${JMX.prefixConfig} = true
       |}
       |default-listener.bind-port = 0
       |pekko.http.server.remote-address-header = on
    """.stripMargin
  )

  val boot = UnicomplexBoot(config)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()

}

class UnicomplexSpec extends TestKit(UnicomplexSpec.boot.actorSystem) with ImplicitSender
                             with AnyWordSpecLike with Matchers with Inspectors with BeforeAndAfterAll
                             with Waiters with Eventually {

  import UnicomplexSpec._
  import system.dispatcher

  val portBindings = Await.result((Unicomplex(system).uniActor ? PortBindings).mapTo[Map[String, Int]], awaitMax)
  val port = portBindings("default-listener")


  override def afterAll(): Unit = {
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
      assert(services.size == 7)


      Await.result(entityAsString(s"http://127.0.0.1:$port/dummysvc/msg/hello"), awaitMax) should be ("^hello$")

      Await.result(entityAsString(s"http://127.0.0.1:$port/dummysvc/who"), awaitMax) should fullyMatch regex """\d+(\.\d+){3}:\d+"""

      Await.result(entityAsString(s"http://127.0.0.1:$port/dummy2svc/v1/msg/hello"), awaitMax) should be ("^hello$")
      // This implementation reverses the message
      Await.result(entityAsString(s"http://127.0.0.1:$port/dummy2svc/msg/hello"), awaitMax) should be ("^olleh$")

      Await.result(entityAsString(s"http://127.0.0.1:$port/pingpongsvc/ping"), awaitMax) should be ("Pong")

      Await.result(entityAsString(s"http://127.0.0.1:$port/pingpongsvc/pong"), awaitMax) should be ("Ping")

      Await.result(entityAsString(s"http://127.0.0.1:$port/dummysvcactor/ping"), awaitMax) should be ("pong")

      Await.result(entityAsString(s"http://127.0.0.1:$port/dummyflowsvc/ping"), awaitMax) should be ("pong")

      val requestChunks = Source.single("Hi this is a test")
        .mapConcat { s => s.split(' ').toList }
        .map (HttpEntity.ChunkStreamPart(_))

      val errResp = Await.result(get(s"http://127.0.0.1:$port/dummyflowsvc/throwit"), awaitMax)
      errResp.status shouldBe StatusCodes.InternalServerError
      val respEntity = Await.result(errResp.entity.toStrict(awaitMax), awaitMax)
      respEntity.data.utf8String shouldBe empty

      val response = Await.result(
          post(s"http://127.0.0.1:$port/dummyflowsvc/chunks", Chunked(ContentTypes.`text/plain(UTF-8)`, requestChunks)),
          awaitMax)

      response.entity shouldBe 'chunked
      val responseStringF = response.entity.dataBytes.map(_.utf8String).toMat(Sink.fold("") { _ + _ })(Keep.right).run()
      val responseString = Await.result(responseStringF, awaitMax)
      responseString should be ("Received 5 chunks and 13 bytes.\r\nThis is the last chunk!")

      Await.result(post(s"http://127.0.0.1:$port/withstash/", HttpEntity("request message")),
        awaitMax).status should be (StatusCodes.Accepted)

      Await.result(entityAsString(s"http://127.0.0.1:$port/withstash/"), awaitMax) should be (Seq.empty[String].toString)

      Await.result(put(s"http://127.0.0.1:$port/withstash/"), awaitMax).status should be (StatusCodes.Created)

      // Whether messages from unstashall or the message from this request will be the first message to StashCubeSvc
      // is not deterministic.  So, running it in eventually.
      eventually {
        Await.result(entityAsString(s"http://127.0.0.1:$port/withstash/"), awaitMax) should be (Seq("request message").toString)
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
      attr shouldBe a [Array[_]]
      val attrs = attr.asInstanceOf[Array[_]]

      // 6 cubes registered above.
      attrs should have size 7
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
      WellKnownActors should include ("Actor[pekko://UnicomplexSpec/user/DummyCube/Prepender#")
      WellKnownActors should include ("Actor[pekko://UnicomplexSpec/user/DummyCube/Appender#")
    }

    "check listener MXbean" in {
      import org.squbs.unicomplex.JMX._
      val mBeanServer = ManagementFactory.getPlatformMBeanServer
      val listenersObjName = new ObjectName(prefix(system) + listenersName)
      val ls = mBeanServer.getAttribute(listenersObjName, "Listeners")
      ls shouldBe a [Array[_]]
      val listeners = ls.asInstanceOf[Array[_]]
      all (listeners) shouldBe a [javax.management.openmbean.CompositeData]

      // 6 services registered on one listener
      listeners should have size 7

    }

    "check extension MBean" in {
      import org.squbs.unicomplex.JMX._
      val mBeanServer = ManagementFactory.getPlatformMBeanServer
      val extensionObjName = new ObjectName(prefix(system) + extensionsName)
      val xs = mBeanServer.getAttribute(extensionObjName, "Extensions")
      xs shouldBe a [Array[_]]
      val extensions = xs.asInstanceOf[Array[_]]
      all (extensions) shouldBe a [javax.management.openmbean.CompositeData]
      extensions should have size 2
      val dummyExtensionA = extensions(0).asInstanceOf[CompositeData]
      val dummyExtensionB = extensions(1).asInstanceOf[CompositeData]
      dummyExtensionA.get("cube") should be ("DummyExtensions")
      dummyExtensionA.get("sequence") should be (30)
      dummyExtensionA.get("error") should be ("")
      dummyExtensionA.get("phase") should be ("")
      dummyExtensionB.get("cube") should be ("DummyExtensions")
      dummyExtensionB.get("sequence") should be (40)
      dummyExtensionB.get("error") should be ("")
      dummyExtensionB.get("phase") should be ("")
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
