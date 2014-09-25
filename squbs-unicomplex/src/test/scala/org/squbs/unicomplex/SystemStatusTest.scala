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
package org.squbs.unicomplex

import com.typesafe.config.ConfigFactory
import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest._
import org.squbs.lifecycle.GracefulStop
import scala.concurrent.Await

object SystemStatusTest {

  val dummyJarsDir = "src/test/resources/classpaths"

  val classPaths = Array(
    "InitBlockCube",
    "InitCubeA",
    "InitCubeB",
    "InitFailCube"
  ) map (dummyJarsDir + "/" + _)

  import collection.JavaConversions._

  val mapConfig = ConfigFactory.parseMap(
    Map(
      "squbs.actorsystem-name"    -> "SystemStatusTest",
      "squbs." + JMX.prefixConfig -> Boolean.box(true),
      "default-listener.bind-service" -> Boolean.box(false)
    )
  )

  val boot = UnicomplexBoot(mapConfig)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()


}

class SystemStatusTest extends TestKit(SystemStatusTest.boot.actorSystem) with ImplicitSender
                        with WordSpecLike with Matchers with BeforeAndAfterAll
                        with SequentialNestedSuiteExecution{

  def checkStatus() {
    Unicomplex(system).uniActor ! ReportStatus

    val state = expectMsgType[LifecycleState]

    if ((Seq[LifecycleState](Active, Stopped, Failed) indexOf(state)) >= 0 ) {
      Await.wait(3000)
      checkStatus()
    }
  }
  override def beforeAll() {
    checkStatus()
  }


  override def afterAll() {
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

  "UniComplex" must {

    "get cube init reports" in {
      Unicomplex(system).uniActor ! ReportStatus
      val (systemState, cubes) = expectMsgType[(LifecycleState, Map[ActorRef, (CubeRegistration, Option[InitReports])])]
      systemState should be(Failed)
      val cubeAReport = cubes.values.find(_._1.name == "CubeA").flatMap(_._2)
      cubeAReport should not be (None)
      cubeAReport.get.state should be (Active)
      val cubeBReport = cubes.values.find(_._1.name == "CubeB").flatMap(_._2)
      cubeBReport should not be (None)
      cubeBReport.get.state should be(Active)
      val initFailReport = cubes.values.find(_._1.name == "InitFail").flatMap(_._2)
      initFailReport should not be (None)
      initFailReport.get.state should be(Failed)
      val initBlockReport = cubes.values.find(_._1.name == "InitBlock").flatMap(_._2)
      initBlockReport should not be (None)
      initBlockReport.get.state should be(Initializing)
    }
  }
}
