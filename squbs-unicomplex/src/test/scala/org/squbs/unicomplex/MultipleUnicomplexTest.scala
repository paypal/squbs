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

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest._
import org.squbs._
import org.squbs.lifecycle.GracefulStop

object MultipleUnicomplexTest {

  val dummyJarsDir = getClass.getClassLoader.getResource("classpaths").getPath

  val classPaths = Array(
    "InitBlockCube",
    "InitCubeA",
    "InitCubeB",
    "InitFailCube"
  ) map (dummyJarsDir + "/" + _)

  import scala.collection.JavaConversions._

  val mapConfig = ConfigFactory.parseMap(
    Map(
      "squbs.actorsystem-name"    -> "MultipleUnicomplexTest1",
      "squbs." + JMX.prefixConfig -> Boolean.box(true),
      "default-listener.bind-port" -> nextPort.toString
    )
  )

  val mapConfig2 = ConfigFactory.parseMap(
    Map(
      "squbs.actorsystem-name"    -> "MultipleUnicomplexTest2",
      "squbs." + JMX.prefixConfig -> Boolean.box(false),
      "default-listener.bind-port" -> nextPort.toString
    )
  )

  val boot = UnicomplexBoot(mapConfig)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()

  val boot2 = UnicomplexBoot(mapConfig2)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()

}

class MultipleUnicomplexTest extends TestKit(MultipleUnicomplexTest.boot.actorSystem) with ImplicitSender
		with WordSpecLike with Matchers with BeforeAndAfterAll with SequentialNestedSuiteExecution {
  
  val sys1 = system
  val sys2 = MultipleUnicomplexTest.boot2.actorSystem
  
  override def beforeAll {
    sys.addShutdownHook {
      Unicomplex(sys2).uniActor ! GracefulStop
      Unicomplex(sys1).uniActor ! GracefulStop
    }
  }
  
  override def afterAll {
    Unicomplex(sys2).uniActor ! GracefulStop
    Unicomplex(sys1).uniActor ! GracefulStop
  }

  "UniComplex" must {

    "get cube init reports" in {
      Unicomplex(sys1).uniActor ! ReportStatus
      val StatusReport(systemState, cubes, _) = expectMsgType[StatusReport]
      systemState should be(Failed)
      val cubeAReport = cubes.values.find(_._1.info.name == "CubeA").flatMap(_._2)
      cubeAReport should not be None
      assert(cubeAReport.get.state == Active)
      val cubeBReport = cubes.values.find(_._1.info.name == "CubeB").flatMap(_._2)
      cubeBReport should not be None
      cubeBReport.get.state should be(Active)
      val initFailReport = cubes.values.find(_._1.info.name == "InitFail").flatMap(_._2)
      initFailReport should not be None
      initFailReport.get.state should be(Failed)
      val initBlockReport = cubes.values.find(_._1.info.name == "InitBlock").flatMap(_._2)
      initBlockReport should not be None
      initBlockReport.get.state should be(Initializing)

      Unicomplex(sys2).uniActor ! ReportStatus
      val StatusReport(systemState2, cubes2, _) = expectMsgType[StatusReport]
      systemState2 should be(Failed)
      val cubeAReport2 = cubes2.values.find(_._1.info.name == "CubeA").flatMap(_._2)
      cubeAReport2 should not be None
      assert(cubeAReport2.get.state == Active)
      val cubeBReport2 = cubes2.values.find(_._1.info.name == "CubeB").flatMap(_._2)
      cubeBReport2 should not be None
      cubeBReport2.get.state should be(Active)
      val initFailReport2 = cubes.values.find(_._1.info.name == "InitFail").flatMap(_._2)
      initFailReport2 should not be None
      initFailReport2.get.state should be(Failed)
      val initBlockReport2 = cubes.values.find(_._1.info.name == "InitBlock").flatMap(_._2)
      initBlockReport2 should not be None
      initBlockReport2.get.state should be(Initializing)
    }
  }
}