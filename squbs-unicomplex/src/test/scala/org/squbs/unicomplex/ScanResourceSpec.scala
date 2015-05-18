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

import java.util.concurrent.TimeUnit
import javax.management.ObjectName

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.AsyncAssertions
import org.scalatest.{BeforeAndAfterAll, Inspectors, Matchers, WordSpecLike}
import org.squbs.lifecycle.GracefulStop
import spray.util.Utils

import scala.concurrent.duration._
import scala.util.Try

object ScanResourceSpec {

  val (_, port) = Utils.temporaryServerHostnameAndPort()

  val jmxPrefix = "ScanResourceSpec"

  var config = ConfigFactory.parseString(
    s"""
       |squbs {
       |  actorsystem-name = scanResourceSpec
       |  ${JMX.prefixConfig} = true
       |}
       |
       |default-listener.bind-port = $port
    """.stripMargin)

  implicit val akkaTimeout: Timeout =
    Try(System.getProperty("test.timeout").toLong) map { millis =>
      akka.util.Timeout(millis, TimeUnit.MILLISECONDS)
    } getOrElse (10 seconds)

  val boot = UnicomplexBoot(config)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanResources()
    .initExtensions.start()
}

class ScanResourceSpec extends TestKit(ScanResourceSpec.boot.actorSystem) with ImplicitSender with WordSpecLike
    with Matchers with Inspectors with BeforeAndAfterAll with AsyncAssertions {

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
      wellKnownActors should include ("Actor[akka://scanResourceSpec/user/ScanResourceCube/Prepender#")
      wellKnownActors should include ("Actor[akka://scanResourceSpec/user/ScanResourceCube/Appender#")
    }
  }

  override protected def afterAll(): Unit = {
    Unicomplex(system).uniActor ! GracefulStop
  }
}
