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
import org.apache.pekko.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.Waiters
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, Inspectors}
import org.squbs.lifecycle.GracefulStop

import java.util.concurrent.TimeUnit
import javax.management.ObjectName
import scala.util.Try

object ScanResourceSpec {

  val jmxPrefix = "ScanResourceSpec"

  val config = ConfigFactory.parseString(
    s"""
       |squbs {
       |  actorsystem-name = scanResourceSpec
       |  ${JMX.prefixConfig} = true
       |}
       |
       |default-listener.bind-port = 0
    """.stripMargin)

  implicit val pekkoTimeout: Timeout =
    Try(System.getProperty("test.timeout").toLong) map { millis =>
      org.apache.pekko.util.Timeout(millis, TimeUnit.MILLISECONDS)
    } getOrElse Timeouts.askTimeout

  val boot = UnicomplexBoot(config)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanResources()
    .initExtensions.start()
}

class ScanResourceSpec extends TestKit(ScanResourceSpec.boot.actorSystem) with ImplicitSender with AnyWordSpecLike
    with Matchers with Inspectors with BeforeAndAfterAll with Waiters {

  import ScanResourceSpec._
  import system.dispatcher

  "The scanned resource" must {

    "have some actors started" in {
      val w = new Waiter

      system.actorSelection("/user/ScanResourceCube").resolveOne().onComplete { result =>
        w {
          assert(result.isSuccess)
        }
        w.dismiss()
      }
      w.await()
    }

    "expose proper cube state through MXBean" in {
      import org.squbs.unicomplex.JMX._
      val cubeName = "ScanResourceCube"
      val cubesName = new ObjectName(prefix(system) + cubeStateName + cubeName)
      get(cubesName, "Name") should be (cubeName)
      get(cubesName, "CubeState") should be ("Active")
      val wellKnownActors = get(cubesName, "WellKnownActors").asInstanceOf[String]
      println(wellKnownActors)
      wellKnownActors should include ("Actor[pekko://scanResourceSpec/user/ScanResourceCube/Prepender#")
      wellKnownActors should include ("Actor[pekko://scanResourceSpec/user/ScanResourceCube/Appender#")
    }
  }

  override protected def afterAll(): Unit = {
    Unicomplex(system).uniActor ! GracefulStop
  }
}
