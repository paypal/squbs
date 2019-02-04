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
import java.util.concurrent.atomic.AtomicInteger
import javax.management.ObjectName

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.Uri.Path
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.testkit.{EventFilter, ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{AsyncFlatSpecLike, BeforeAndAfterAll, Matchers}
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.JMX.prefix

import scala.concurrent.{Await, Future}

object MaterializerSpec {

  val dummyJarsDir = getClass.getClassLoader.getResource("classpaths").getPath

  val classPaths = Array(
    "MaterializerSpec"
  ) map (dummyJarsDir + "/" + _)

  val config = ConfigFactory.parseString(
    s"""
       |squbs {
       |  actorsystem-name = MaterializerSpec
       |  ${JMX.prefixConfig} = true
       |}
       |
       |default-listener.bind-port = 0
       |
       |akka.loggers = ["akka.testkit.TestEventListener"]
       |
       |test-listener {
       |  type = squbs.listener
       |  bind-address = "0.0.0.0"
       |  full-address = false
       |  bind-port = 8080
       |  secure = false
       |  need-client-auth = false
       |  ssl-context = default
       |  materializer = test-materializer-1
       |}
       |
       |test-materializer-1 {
       |  type = squbs.materializer
       |  class = org.squbs.unicomplex.TestMaterializer1
       |}
       |
       |test-materializer-2 {
       |  type = squbs.materializer
       |  class = org.squbs.unicomplex.TestMaterializer2
       |}
       |
       |test-materializer-3 {
       |  type = squbs.materializer
       |  class = org.squbs.unicomplex.TestMaterializer3
       |}
       |
       |# Adding here just to make sure it does not create unused materializers
       |non-existent-test-materializer {
       |  type = squbs.materializer
       |  class = org.squbs.unicomplex.NonExistentTestMaterializer
       |}
    """.stripMargin
  )

  val customMaterializerConfig = ConfigFactory.parseString(
    """
      |initial-input-buffer-size = 128
      |max-input-buffer-size = 256
      |dispatcher = "blocking-dispatcher"
      |subscription-timeout {
      |  mode = warn
      |  timeout = 12s
      |}
      |debug-logging = on
      |output-burst-limit = 999
      |auto-fusing = off
      |max-fixed-buffer-size = 1000000091
      |sync-processing-limit = 899
      |debug {
      |  fuzzing-mode = on
      |}
      |io.tcp {
      |  write-buffer-size = 16 KiB
      |}
      |stream-ref {
      |  buffer-capacity = 32
      |  demand-redelivery-interval = 1 second
      |  subscription-timeout = 30 seconds
      |  final-termination-signal-deadline = 2 seconds
      |}
      |blocking-io-dispatcher = "akka.stream.default-blocking-io-dispatcher"
    """.stripMargin)

  val boot = UnicomplexBoot(config)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()

}

class MaterializerSpec extends TestKit(
  MaterializerSpec.boot.actorSystem) with AsyncFlatSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll {

  import akka.pattern._
  import Timeouts._

  val portBindings = Await.result((Unicomplex(system).uniActor ? PortBindings).mapTo[Map[String, Int]], awaitMax)
  val defaultListenerPort = portBindings("default-listener")
  val testListenerPort = portBindings("test-listener")

  override def afterAll(): Unit = {
    Unicomplex(system).uniActor ! GracefulStop
  }

  it should "lazy create materializers" in {
    // It should not create non-existent-test-materializer
    // ResumeFlowDefinition binds to both default-listener and test-listener
    Unicomplex(system).materializers should have size 2
  }

  it should "cache the materializer" in {
    val mat = Unicomplex(system).materializer("test-materializer-1")
    Unicomplex(system).materializer("test-materializer-1") shouldEqual mat

    Future {
      Unicomplex(system).materializers should have size 2
    }
  }

  it should "keep default akka.stream.materializer settings for default-materializer" in {
    implicit val mat = Unicomplex(system).materializer("default-materializer")
    val expected = ActorMaterializerSettings(system)
    val actual = mat.asInstanceOf[ActorMaterializer].settings

    // Since decider is custom, we cannot check the equality of ActorMaterializerSettings objects.  So, we need to
    // check equality of each individual setting.
    actual.initialInputBufferSize shouldEqual expected.initialInputBufferSize
    actual.maxInputBufferSize shouldEqual expected.maxInputBufferSize
    actual.dispatcher shouldEqual expected.dispatcher
    actual.subscriptionTimeoutSettings shouldEqual expected.subscriptionTimeoutSettings
    actual.debugLogging shouldEqual expected.debugLogging
    actual.outputBurstLimit shouldEqual expected.outputBurstLimit
    actual.fuzzingMode shouldEqual expected.fuzzingMode
    actual.autoFusing shouldEqual expected.autoFusing
    actual.maxFixedBufferSize shouldEqual expected.maxFixedBufferSize
    actual.syncProcessingLimit shouldEqual expected.syncProcessingLimit

    recoverToSucceededIf[ArithmeticException] {
      // With the default ActorMaterializer, the strategy is Supervision.Stop
      Source(0 :: 1 :: 2 :: 0 :: 4 :: 5 :: 10 :: Nil).map(100 / _).runWith(Sink.seq)
    }
  }

  it should "log the exception in the default-matealizer's decider" in {
    implicit val mat = Unicomplex(system).materializer("default-materializer")

    EventFilter[ArithmeticException](occurrences = 1) intercept {
      Source(0 :: 1 :: 2 :: 0 :: 4 :: 5 :: 10 :: Nil).map(100 / _).runWith(Sink.seq)
    }

    // If there were no logs, the above EventFilter check would fail.
    succeed
  }

  it should "use materializer from configuration" in {
    // With the default ActorMaterializer, it would throw an exception
    implicit val mat = Unicomplex(system).materializer("test-materializer-1")
    val result = Source(0 :: 1 :: 2 :: 0 :: 4 :: 5 :: 10 :: Nil).map(100 / _).runWith(Sink.seq)
    result map { _ should contain theSameElementsInOrderAs(100 :: 50 :: 25 :: 20 :: 10 :: Nil)}
  }

  it should "use custom materializer for test-listener" in {
    DeciderVisitCounter.counter.get shouldBe 0
    implicit val mat = Unicomplex(system).materializer("test-materializer-1")
    Http().singleRequest(HttpRequest(uri = Uri(s"http://127.0.0.1:$testListenerPort/custommaterializer/"))) map { response =>
      response.status shouldEqual StatusCodes.InternalServerError
      DeciderVisitCounter.counter.get shouldBe 1
    }
  }

  it should "use custom materializer for default-listener" in {
    DeciderVisitCounter.counter.get shouldBe 1
    implicit val mat = Unicomplex(system).materializer("test-materializer-1")
    Http().singleRequest(HttpRequest(uri = Uri(s"http://127.0.0.1:$defaultListenerPort/custommaterializer/"))) map { response =>
      response.status shouldEqual StatusCodes.InternalServerError
      DeciderVisitCounter.counter.get shouldBe 1
    }
  }

  it should "obtain the default materializer through the Java API" in {
    val actual = MaterializerTest.getMaterializer(system, "default-materializer")
    val expected = Unicomplex(system).materializer("default-materializer")
    actual shouldEqual expected
  }

  it should "obtain a custom materializer through the Java API" in {
    val actual = MaterializerTest.getMaterializer(system, "test-materializer-1")
    val expected = Unicomplex(system).materializer("test-materializer-1")
    actual shouldEqual expected
  }

  it should "have default-materializer defined by default" in {
    Unicomplex(system).materializer("default-materializer")

    Future {
      Unicomplex(system).materializers should have size 2
    }
  }

  it should "show materializer settings as a JMX bean" in {
    // Verify there is a JMX bean for default-listener and it has default settings
    val defaultSettings = ActorMaterializerSettings(system)
    jmxValue("default-materializer", "Name") shouldEqual "default-materializer"
    jmxValue("default-materializer", "FactoryClass") shouldEqual "org.squbs.unicomplex.DefaultMaterializer"
    jmxValue("default-materializer", "ActorSystemName") shouldEqual system.name
    jmxValue("default-materializer", "Shutdown") shouldEqual "no"
    jmxValue("default-materializer", "InitialInputBufferSize") shouldEqual defaultSettings.initialInputBufferSize
    jmxValue("default-materializer", "MaxInputBufferSize") shouldEqual defaultSettings.maxInputBufferSize
    jmxValue("default-materializer", "Dispatcher") shouldEqual defaultSettings.dispatcher
    jmxValue("default-materializer", "StreamSubscriptionTimeoutTerminationMode") shouldEqual "cancel"
    jmxValue("default-materializer", "SubscriptionTimeout") shouldEqual(
      defaultSettings.subscriptionTimeoutSettings.timeout.toString)
    jmxValue("default-materializer", "DebugLogging") shouldEqual "off"
    jmxValue("default-materializer", "OutputBurstLimit") shouldEqual defaultSettings.outputBurstLimit
    jmxValue("default-materializer", "FuzzingMode") shouldEqual "off"
    jmxValue("default-materializer", "AutoFusing") shouldEqual "on"
    jmxValue("default-materializer", "MaxFixedBufferSize") shouldEqual defaultSettings.maxFixedBufferSize
    jmxValue("default-materializer", "SyncProcessingLimit") shouldEqual defaultSettings.syncProcessingLimit

    // Verify there is a JMX bean for test-materializer-1
    jmxValue("test-materializer-1", "Name") shouldEqual "test-materializer-1"
    jmxValue("test-materializer-1", "FactoryClass") shouldEqual "org.squbs.unicomplex.TestMaterializer1"
    jmxValue("test-materializer-1", "ActorSystemName") shouldEqual system.name
    jmxValue("test-materializer-1", "Shutdown") shouldEqual "no"
  }

  it should "reflect modifications on each setting on JMX bean" in {
    Unicomplex(system).materializer("test-materializer-2")
    Unicomplex(system).materializers should have size 3

    jmxValue("test-materializer-2", "Name") shouldEqual "test-materializer-2"
    jmxValue("test-materializer-2", "FactoryClass") shouldEqual "org.squbs.unicomplex.TestMaterializer2"
    jmxValue("test-materializer-2", "ActorSystemName") shouldEqual system.name
    jmxValue("test-materializer-2", "Shutdown") shouldEqual "no"
    jmxValue("test-materializer-2", "InitialInputBufferSize") shouldEqual 128
    jmxValue("test-materializer-2", "MaxInputBufferSize") shouldEqual 256
    jmxValue("test-materializer-2", "Dispatcher") shouldEqual "blocking-dispatcher"
    jmxValue("test-materializer-2", "StreamSubscriptionTimeoutTerminationMode") shouldEqual "warn"
    jmxValue("test-materializer-2", "SubscriptionTimeout") shouldEqual "12000 milliseconds" // It actually converts to ms
    jmxValue("test-materializer-2", "DebugLogging") shouldEqual "on"
    jmxValue("test-materializer-2", "OutputBurstLimit") shouldEqual 999
    jmxValue("test-materializer-2", "FuzzingMode") shouldEqual "on"
    jmxValue("test-materializer-2", "AutoFusing") shouldEqual "off"
    jmxValue("test-materializer-2", "MaxFixedBufferSize") shouldEqual 1000000091
    jmxValue("test-materializer-2", "SyncProcessingLimit") shouldEqual 899

    // Verify StreamSubscriptionTimeoutTerminationMode for noop
    Unicomplex(system).materializer("test-materializer-3")
    Unicomplex(system).materializers should have size 4
    jmxValue("test-materializer-3", "Name") shouldEqual "test-materializer-3"
    jmxValue("test-materializer-3", "StreamSubscriptionTimeoutTerminationMode") shouldEqual "noop"
  }

  def jmxValue(name: String, key: String) = {
    val oName = ObjectName.getInstance(
      s"${prefix(system)}org.squbs.unicomplex:type=Materializer,name=${ObjectName.quote(name)}")
    ManagementFactory.getPlatformMBeanServer.getAttribute(oName, key)
  }
}

object DeciderVisitCounter {
  val counter = new AtomicInteger(0)
}

class TestMaterializer1 {

  val decider: Supervision.Decider = {
    case _: ArithmeticException => Supervision.Resume
    case DummyHttpException     =>
      DeciderVisitCounter.counter.incrementAndGet()
      Supervision.Stop
    case _                      => Supervision.Stop
  }

  def createMaterializer(implicit system: ActorSystem): ActorMaterializer =
    ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))
}

class TestMaterializer2 {

  def createMaterializer(implicit system: ActorSystem): ActorMaterializer =
    ActorMaterializer(ActorMaterializerSettings(MaterializerSpec.customMaterializerConfig))
}

class TestMaterializer3 {

  def createMaterializer(implicit system: ActorSystem): ActorMaterializer =
    ActorMaterializer(ActorMaterializerSettings(ConfigFactory.parseString("subscription-timeout.mode = noop")
      .withFallback(MaterializerSpec.customMaterializerConfig)))
}

object DummyHttpException extends RuntimeException

class ResumeFlowDefinition extends FlowDefinition with WebContext {

  val path = s"/$webContext/"
  def flow = Flow[HttpRequest].map {
    case HttpRequest(HttpMethods.GET, Uri(_, _, Path(`path`), _, _), _, _, _) => throw DummyHttpException
  }
}