/*
 * Copyright 2018 PayPal
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

package org.squbs.streams

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}
import org.apache.pekko.stream.testkit.scaladsl.TestSink
import org.apache.pekko.testkit.TestKit
import org.scalatest.OptionValues._
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.metrics.MetricsExtension

import java.lang.management.ManagementFactory
import javax.management.ObjectName
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

class RetryJMXSpec extends TestKit(ActorSystem("RetryJMXSpec")) with AnyFlatSpecLike with Matchers {

  val failure = Failure(new Exception("failed"))

  it should "collect retry metrics" in {
    var first = true
    val bottom = Flow[(String, Long)].map {
      case ("a", ctx) => (failure, ctx) // a always fails
      case ("b", ctx) if first => // b fails only first attempt
        first = false
        (failure, ctx)
      case (elem, ctx) =>
        first = true
        (Success(elem), ctx)
    }

    var context = 0L
    Source("a" :: "b" :: "c" :: Nil)
      .map { s => context += 1; (s, context) }
      .via(Retry(RetrySettings[String, String, Long](1).withMetrics("Retry")).join(bottom))
      .map { case (s, ctx) => s }
      .runWith(Sink.seq)

    awaitAssert {
      metricsJmxValue("Retry.retry-count", "Count").value shouldBe 2 // a, b
      metricsJmxValue("Retry.failed-count", "Count").value shouldBe 1 // a
      metricsJmxValue("Retry.success-count", "Count").value shouldBe 2 // b + c
    }
  }

  it should "update metrics name on subsequent materializations" in {
    val bottom = Flow[(String, Long)].map {
      case (_, ctx) => (failure, ctx)
    }
    val retry = Retry(RetrySettings[String, String, Long](1).withMetrics("myRetry"))
    var context = 0L
    Source("a" :: "b" :: "c" :: Nil)
      .map { s => context += 1; (s, context) }
      .via(retry.join(bottom))
      .map { case (s, _) => s }
      .runWith(Sink.seq)
    awaitAssert(metricsJmxValue("myRetry.retry-count", "Count").value shouldBe 3, 10 seconds)

    Source("d" :: "e" :: "f" :: Nil)
      .map { s => context += 1; (s, context) }
      .via(retry.join(bottom))
      .map { case (s, _) => s }
      .runWith(Sink.seq)
    awaitAssert(metricsJmxValue("myRetry-1.retry-count", "Count").value shouldBe 3, 10 seconds)
  }

  it should "publish the retry setting via jmx" in {
    val bottom = Flow[(String, Long)].delay(1 second).map {
      case (_, ctx) => (Failure(new Exception("failed")), ctx)
    }
    val retry = Retry(RetrySettings[String, String, Long](3)
      .withDelay(1.second)
      .withExponentialBackoff(3)
      .withMaxDelay(2.seconds)
      .withMaxWaitingRetries(10)
      .withMetrics("retrySettingJmx"))

    var context = 0L
    val sink = Source("a" :: "b" :: "c" :: Nil)
      .map { s => context += 1; (s, context) }
      .via(retry.join(bottom))
      .map { case (s, _) => s }
      .runWith(TestSink.probe)
    sink.request(3)
    awaitAssert {
      jmxSettingsValue("retrySettingJmx", "MaxRetries").value shouldBe 3
      jmxSettingsValue("retrySettingJmx", "Delay").value shouldBe "1 second"
      jmxSettingsValue("retrySettingJmx", "Name").value shouldBe "retrySettingJmx"
      jmxSettingsValue("retrySettingJmx", "MaxDelay").value shouldBe "2 seconds"
      jmxSettingsValue("retrySettingJmx", "MaxBufferSize").value shouldBe 10
      jmxSettingsValue("retrySettingJmx", "ExponentialBackoffFactor").value shouldBe 3
    }
  }

  private def metricsJmxValue(beanName: String, key: String): Option[AnyRef] = {
    val oName = ObjectName.getInstance(s"${MetricsExtension(system).Domain}:name=${beanName}")
    Option(ManagementFactory.getPlatformMBeanServer.getAttribute(oName, key))
  }

  private def jmxSettingsValue(name: String, key: String): Option[AnyRef] = {
    val oName = ObjectName.getInstance(
      s"org.squbs.configuration:type=squbs.retry.settings,name=${ObjectName.quote(name)}")
    Option(ManagementFactory.getPlatformMBeanServer.getAttribute(oName, key))
  }
}

class RetryStateJmxSpec extends TestKit(ActorSystem("RetryStateJmxSpec")) with AnyFlatSpecLike with Matchers {

  it should "publish registry and queue size via jmx" in {
    val bottom = Flow[(String, Long)].delay(1 second).map {
      case (_, ctx) => (Failure(new Exception("failed")), ctx)
    }
    val retry = Retry(RetrySettings[String, String, Long](1, delay = 1 second).withMetrics("Retry"))
    var context = 0L
    val sink = Source("a" :: "b" :: "c" :: Nil)
      .map { s => context += 1; (s, context) }
      .via(retry.join(bottom))
      .map { case (s, _) => s }
      .runWith(TestSink.probe)

    sink.request(3)
    awaitAssert(jmxState("Retry", "RegistrySize").value shouldBe 3)
    awaitAssert(jmxState("Retry", "QueueSize").value shouldBe 3)
    awaitAssert(jmxState("Retry", "Name").value shouldBe "Retry")
  }

  private def jmxState(name: String, key: String): Option[AnyRef] = {
    val oName = ObjectName.getInstance(
      s"org.squbs.configuration:type=squbs.retry.state,name=${ObjectName.quote(name)}")
    Option(ManagementFactory.getPlatformMBeanServer.getAttribute(oName, key))
  }
}

