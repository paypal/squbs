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

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, SequentialNestedSuiteExecution, WordSpecLike}
import org.squbs.lifecycle.GracefulStop
import spray.util.Utils

object BadProxySpec {

  val dummyJarsDir = getClass.getClassLoader.getResource("classpaths").getPath

  val classPaths = Array("UndefinedProxyRoute") map (dummyJarsDir + "/" + _)

  val (host, port) = Utils.temporaryServerHostnameAndPort()

  import scala.collection.JavaConversions._

  val mapConfig = ConfigFactory.parseMap(
    Map(
      "squbs.actorsystem-name" -> "BadProxySpec",
      "squbs." + JMX.prefixConfig -> Boolean.box(true),
      "default-listener.bind-address" -> host,
      "default-listener.bind-port" -> port.toString)
  )

  val boot = UnicomplexBoot(mapConfig)
    .createUsing { (name, config) => ActorSystem(name, config) }
    .scanComponents(classPaths)
    .start()
}

class BadProxySpec extends TestKit(BadProxySpec.boot.actorSystem) with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll with SequentialNestedSuiteExecution {

  override def beforeAll() {
    while (true) {
      try {
        Thread.sleep(5)
      } catch {
        case e: Throwable =>
      }

      val uniActor = Unicomplex(system).uniActor
      println(uniActor)
      Unicomplex(system).uniActor ! ReportStatus

      val (state, _, _) = expectMsgType[(LifecycleState, _, _)]

      if (Seq(Active, Stopped, Failed) contains state) return
    }
  }

  override def afterAll() {
    Unicomplex(system).uniActor ! GracefulStop
  }

  "CubeSupervisor" must {

    "get init reports from cube actors even if the actor failed in init" in {
      system.actorSelection("/user/UndefinedProxyRoute") ! CheckInitStatus
      val report = expectMsgType[(InitReports, Boolean)]._1
      report.state should be(Failed)
      report.reports.size should be(1)
    }
  }

  "UniComplex" must {

    "get cube init reports" in {
      Unicomplex(system).uniActor ! ReportStatus
      val (systemState, cubes, _) =
        expectMsgType[(LifecycleState, Map[ActorRef, (CubeRegistration, Option[InitReports])], Seq[Extension])]
      systemState should be(Failed)

      val initFailReport = cubes.values.find(_._1.info.name == "UndefinedProxyRoute").flatMap(_._2)
      initFailReport should not be None
      initFailReport.get.state should be(Failed)
    }
  }
}
