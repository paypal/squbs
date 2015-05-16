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
package org.squbs.dispatcher

import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit
import javax.management.ObjectName

import akka.actor.ActorSystem
import akka.io.IO
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.AsyncAssertions
import org.scalatest.{BeforeAndAfterAll, Inspectors, Matchers, WordSpecLike}
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.{Unicomplex, JMX, UnicomplexBoot}
import spray.can.Http
import spray.http._
import spray.util.Utils

import scala.concurrent.duration._
import scala.util.Try

object ForkJoinConfiguratorSpec {

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

  val jmxPrefix = "forkJoinConfiguratorSpec"

  var config = ConfigFactory.parseString(
    s"""
      |squbs {
      |  actorsystem-name = forkJoinConfiguratorSpec
      |  ${JMX.prefixConfig} = true
      |}
      |
      |default-listener.bind-port = $port
      |
      |akka.actor {
      |  default-dispatcher {
      |    default-executor.fallback = org.squbs.dispatcher.ForkJoinConfigurator
      |    fork-join-executor {
      |      # Avoid JMX naming conflict in case of multiple tests.
      |      jmx-name-prefix = $jmxPrefix
      |    }
      |  }
      |}
    """.stripMargin)

  implicit val akkaTimeout: Timeout =
    Try(System.getProperty("test.timeout").toLong) map { millis =>
      akka.util.Timeout(millis, TimeUnit.MILLISECONDS)
    } getOrElse (10 seconds)


  val boot = UnicomplexBoot(config)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()
}

class ForkJoinConfiguratorSpec extends TestKit(ForkJoinConfiguratorSpec.boot.actorSystem) with ImplicitSender
    with WordSpecLike with Matchers with Inspectors with BeforeAndAfterAll with AsyncAssertions {

  import ForkJoinConfiguratorSpec._
  import system.dispatcher

  "The ForkJoinConfigurator" must {

    "have some actors started" in {
      val w = new Waiter

      system.actorSelection("/user/DummyCube").resolveOne().onComplete { result =>
        w {
          assert(result.isSuccess)
        }
        w.dismiss()
      }
      w.await()
    }

    "be able to handle a simple Spray request" in {
      IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/dummysvc/msg/hello"))
      within(akkaTimeout.duration) {
        val response = expectMsgType[HttpResponse]
        response.status should be(StatusCodes.OK)
        response.entity.asString should be("^hello$")
      }
    }

    "expose proper ForkJoinPool MXBean stats" in {
      import org.squbs.unicomplex.JMX._
      val mBeanServer = ManagementFactory.getPlatformMBeanServer
      val fjName =
        new ObjectName(jmxPrefix + '.' + forkJoinStatsName + "forkJoinConfiguratorSpec-akka.actor.default-dispatcher")

      val poolSize = mBeanServer.getAttribute(fjName, "PoolSize")
      poolSize shouldBe a [java.lang.Integer]
      poolSize.asInstanceOf[java.lang.Integer].intValue should be > 0

      val activeThreadCount = mBeanServer.getAttribute(fjName, "ActiveThreadCount")
      activeThreadCount shouldBe a [java.lang.Integer]
      activeThreadCount.asInstanceOf[java.lang.Integer].intValue should be >= 0

      val parallelism = mBeanServer.getAttribute(fjName, "Parallelism")
      parallelism shouldBe a [java.lang.Integer]
      parallelism.asInstanceOf[java.lang.Integer].intValue should be > 0

      val stealCount = mBeanServer.getAttribute(fjName, "StealCount")
      stealCount shouldBe a [java.lang.Long]
      stealCount.asInstanceOf[java.lang.Long].longValue should be > 0L

      val mode = mBeanServer.getAttribute(fjName, "Mode")
      mode shouldBe a [String]
      mode.asInstanceOf[String] should be ("Async")

      val queuedSubmissionCount = mBeanServer.getAttribute(fjName, "QueuedSubmissionCount")
      queuedSubmissionCount shouldBe a [java.lang.Integer]
      queuedSubmissionCount.asInstanceOf[java.lang.Integer].intValue should be >= 0

      val queuedTaskCount = mBeanServer.getAttribute(fjName, "QueuedTaskCount")
      queuedTaskCount shouldBe a [java.lang.Long]
      queuedTaskCount.asInstanceOf[java.lang.Long].longValue should be >= 0L

      val runningThreadCount = mBeanServer.getAttribute(fjName, "RunningThreadCount")
      runningThreadCount shouldBe a [java.lang.Integer]
      runningThreadCount.asInstanceOf[java.lang.Integer].intValue should be >= 0

      val quiescent = mBeanServer.getAttribute(fjName, "Quiescent")
      quiescent shouldBe a [java.lang.Boolean]
    }
  }
  override def afterAll() {
    Unicomplex(system).uniActor ! GracefulStop
  }
}
