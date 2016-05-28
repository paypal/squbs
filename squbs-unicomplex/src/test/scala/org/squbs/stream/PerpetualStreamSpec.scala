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
package org.squbs.stream

import akka.actor.ActorSystem
import akka.pattern._
import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues._
import org.scalatest.{FlatSpec, Matchers}
import org.squbs.unicomplex._

import scala.concurrent.Await

class PerpetualStreamSpec extends FlatSpec with Matchers {

  val dummyJarsDir = getClass.getClassLoader.getResource("classpaths").getPath

  it should "throw an IllegalStateException when accessing matValue before stream starts" in {

    val classPaths = Array("IllegalStateStream") map (dummyJarsDir + "/" + _)

    val config = ConfigFactory.parseString(
      s"""
         |squbs {
         |  actorsystem-name = IllegalStateStream
         |  ${JMX.prefixConfig} = true
         |}
    """.stripMargin
    )

    val boot = UnicomplexBoot(config)
      .createUsing {
        (name, config) => ActorSystem(name, config)
      }
      .scanComponents(classPaths)
      .start()

    import Timeouts._

    val reportF = (Unicomplex(boot.actorSystem).uniActor ? ReportStatus).mapTo[StatusReport]
    val StatusReport(state, cubes, _) = Await.result(reportF, awaitMax)
    state shouldBe Failed
    cubes.values should have size 1
    val InitReports(cubeState, actorReports) = cubes.values.head._2.value
    cubeState shouldBe Failed
    the [IllegalStateException] thrownBy actorReports.values.head.value.get should have message
      "Materialized value not available before streamGraph is started!"
  }

  it should "recover from upstream failure" in {
    val classPaths = Array("ThrowExceptionStream") map (dummyJarsDir + "/" + _)

    val config = ConfigFactory.parseString(
      s"""
         |squbs {
         |  actorsystem-name = ThrowExceptionStream
         |  ${JMX.prefixConfig} = true
         |}
    """.stripMargin
    )

    val boot = UnicomplexBoot(config)
      .createUsing {
        (name, config) => ActorSystem(name, config)
      }
      .scanComponents(classPaths)
      .start()

    import Timeouts._

    val reportF = (Unicomplex(boot.actorSystem).uniActor ? ReportStatus).mapTo[StatusReport]
    val StatusReport(state, cubes, _) = Await.result(reportF, awaitMax)
  }
}
