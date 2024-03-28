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

package org.squbs.unicomplex

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, SequentialNestedSuiteExecution}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.Timeouts._
import org.squbs.unicomplex.dummyfailedextensions.{DummyFailedExtensionA, DummyFailedExtensionB}

import scala.jdk.CollectionConverters._


object SystemStatusTest {

	val dummyJarsDir = getClass.getClassLoader.getResource("classpaths").getPath

	val classPaths = Array(
		"InitBlockCube",
		"InitCubeA",
		"InitCubeB",
		"InitFailCube",
    "DummyFailedExtensions") map (dummyJarsDir + "/" + _)

	val mapConfig = ConfigFactory.parseMap(
		Map(
			"squbs.actorsystem-name" -> "SystemStatusTest",
			"squbs." + JMX.prefixConfig -> Boolean.box(true)).asJava)

  val boot = UnicomplexBoot(mapConfig)
    .createUsing {
    (name, config) => ActorSystem(name, config)
  }
    .scanComponents(classPaths)
    .initExtensions.start(startupTimeout)

}

class SystemStatusTest extends TestKit(SystemStatusTest.boot.actorSystem) with ImplicitSender
	with AnyWordSpecLike with Matchers with BeforeAndAfterAll
	with SequentialNestedSuiteExecution {

	override def beforeAll(): Unit = {
    awaitAssert({
      Unicomplex(system).uniActor ! ReportStatus
      receiveOne(awaitMax) match {
        case StatusReport(state, _, _) =>
          Seq(Active, Stopped, Failed) should contain (state)
        case _ => fail()
      }
    }, max = awaitMax)
	}

	override def afterAll(): Unit = {
		Unicomplex(system).uniActor ! GracefulStop
	}

	"CubeSupervisor" must {

		"get init reports from cube actors" in {
			system.actorSelection("/user/CubeA") ! CheckInitStatus
			val report = expectMsgType[(InitReports, Boolean)]._1
			report.state should be(Active)
			report.reports.size should be(2)
		}

		"get init reports from cube actors even if the actor failed in init" in {
			system.actorSelection("/user/InitFail") ! CheckInitStatus
			val report = expectMsgType[(InitReports, Boolean)]._1
			report.state should be(Failed)
			report.reports.size should be(1)
		}

		"deal with the situation that cube actors are not able to send the reports" in {
			system.actorSelection("/user/InitBlock") ! CheckInitStatus
			val report = expectMsgType[(InitReports, Boolean)]._1
			report.state should be(Initializing)
			report.reports.size should be(1)
		}
	}

	"Unicomplex" must {

		"get cube init reports" in {
			Unicomplex(system).uniActor ! ReportStatus
			val StatusReport(systemState, cubes, extensions) = expectMsgType[StatusReport]
			systemState should be(Failed)

			val cubeAReport = cubes.values.find(_._1.info.name == "CubeA").flatMap(_._2)
			cubeAReport should not be None
			cubeAReport.get.state should be(Active)

			val cubeBReport = cubes.values.find(_._1.info.name == "CubeB").flatMap(_._2)
			cubeBReport should not be None
			cubeBReport.get.state should be(Active)

			val initFailReport = cubes.values.find(_._1.info.name == "InitFail").flatMap(_._2)
			initFailReport should not be None
			initFailReport.get.state should be(Failed)

			val initBlockReport = cubes.values.find(_._1.info.name == "InitBlock").flatMap(_._2)
			initBlockReport should not be None
			initBlockReport.get.state should be(Initializing)

      val extensionFailedReportA = extensions find (_.extLifecycle.exists(_.isInstanceOf[DummyFailedExtensionA]))
      extensionFailedReportA should not be None
      extensionFailedReportA.get.exceptions should have size 1
      extensionFailedReportA.get.exceptions map (_._1) should contain ("preInit")

      val extensionFailedReportB = extensions find (_.extLifecycle.exists(_.isInstanceOf[DummyFailedExtensionB]))
      extensionFailedReportB should not be None
      extensionFailedReportB.get.exceptions should have size 1
      extensionFailedReportB.get.exceptions map (_._1) should contain ("postInit")

      val extensionFailedReportC = extensions find (_.extLifecycle.isEmpty)
      extensionFailedReportC should not be None
      extensionFailedReportC.get.exceptions should have size 1
      extensionFailedReportC.get.exceptions map (_._1) should contain ("load")

      Unicomplex(system).uniActor ! InitReports(Failed, Map.empty)

      Unicomplex(system).uniActor ! SystemState
      expectMsg(Failed)

      Unicomplex(system).uniActor ! ObtainLifecycleEvents(Active)

      Unicomplex(system).uniActor ! LifecycleTimesRequest
      expectMsgClass(classOf[LifecycleTimes])
    }
	}
}
