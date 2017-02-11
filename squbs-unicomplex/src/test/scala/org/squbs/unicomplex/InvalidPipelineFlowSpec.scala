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

import akka.actor.ActorSystem
import akka.pattern._
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues._
import org.scalatest.{FlatSpecLike, Matchers}

import scala.concurrent.Await
import scala.util.Failure

object InvalidPipelineFlowSpec {

  val dummyJarsDir = getClass.getClassLoader.getResource("classpaths").getPath

  val classPath = dummyJarsDir + "/InvalidPipelineFlowSvc/META-INF/squbs-meta.conf"

  val config = ConfigFactory.parseString(
    s"""
       |squbs {
       |  actorsystem-name = InvalidPipelineFlowSpec
       |  ${JMX.prefixConfig} = true
       |}
       |default-listener.bind-port = 0
       |akka.http.server.remote-address-header = on
    """.stripMargin
  )

  import Timeouts._

  val boot = UnicomplexBoot(config)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanResources(withClassPath = false, classPath)
    .start(startupTimeout)
}


class InvalidPipelineFlowSpec extends TestKit(InvalidPipelineFlowSpec.boot.actorSystem) with FlatSpecLike with Matchers {

  "The InvalidPipelineFlowSvc" should "fail" in {
    import Timeouts._
    Await.result(Unicomplex(system).uniActor ? SystemState, awaitMax) shouldBe Failed
  }

  "The InvalidPipelineFlowSvc" should "expose errors" in {
    import Timeouts._
    val report = Await.result((Unicomplex(system).uniActor ? ReportStatus).mapTo[StatusReport], awaitMax)
    report.state shouldBe Failed
    val initTry = report.cubes.values.head._2.value.reports.values.head.value
    initTry should matchPattern { case Failure(e: IllegalArgumentException) => }
  }
}
