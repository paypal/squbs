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
package org.squbs.streams.circuitbreaker

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.streams.circuitbreaker.impl.AtomicCircuitBreakerState

import java.lang.management.ManagementFactory
import javax.management.ObjectName
import scala.language.postfixOps

class CircuitBreakerStateSpec extends TestKit(ActorSystem("CircuitBreakerStateSpec"))
  with AnyFlatSpecLike with Matchers {

  implicit val scheduler = system.scheduler
  import system.dispatcher

  import scala.concurrent.duration._

  it should "use default exponential backoff settings" in {
    AtomicCircuitBreakerState(
      "params-with-default-exponential-backoff",
      1,
      50.milliseconds,
      20.milliseconds)

    assertJmxValue("params-with-default-exponential-backoff", "MaxFailures", 1)
    assertJmxValue("params-with-default-exponential-backoff", "CallTimeout", "50 milliseconds")
    assertJmxValue("params-with-default-exponential-backoff", "ResetTimeout", "20 milliseconds")
    assertJmxValue("params-with-default-exponential-backoff", "MaxResetTimeout", "36500 days")
    assertJmxValue("params-with-default-exponential-backoff", "ExponentialBackoffFactor", 1.0)
  }

  it should "create circuit breaker state with provided exponential backoff settings" in {
    AtomicCircuitBreakerState(
      "params-with-custom-exponential-backoff",
      1,
      50.milliseconds,
      20.milliseconds,
      2.minutes,
      16.0)
    assertJmxValue("params-with-custom-exponential-backoff", "MaxFailures", 1)
    assertJmxValue("params-with-custom-exponential-backoff", "CallTimeout", "50 milliseconds")
    assertJmxValue("params-with-custom-exponential-backoff", "ResetTimeout", "20 milliseconds")
    assertJmxValue("params-with-custom-exponential-backoff", "MaxResetTimeout", "2 minutes")
    assertJmxValue("params-with-custom-exponential-backoff", "ExponentialBackoffFactor", 16.0)
  }

  it should "create circuit breaker state from configuration" in {
    val config = ConfigFactory.parseString(
      """
        |max-failures = 1
        |call-timeout = 50 ms
        |reset-timeout = 20 ms
        |max-reset-timeout = 1 minute
        |exponential-backoff-factor = 16.0
      """.stripMargin)

    AtomicCircuitBreakerState("from-config", config)
    assertJmxValue("from-config", "MaxFailures", 1)
    assertJmxValue("from-config", "CallTimeout", "50 milliseconds")
    assertJmxValue("from-config", "ResetTimeout", "20 milliseconds")
    assertJmxValue("from-config", "MaxResetTimeout", "1 minute")
    assertJmxValue("from-config", "ExponentialBackoffFactor", 16.0)
  }

  it should "fallback to default values when configuration is empty" in {
    AtomicCircuitBreakerState("empty-config", ConfigFactory.empty())
    assertJmxValue("empty-config", "MaxFailures", 5)
    assertJmxValue("empty-config", "CallTimeout", "1 second")
    assertJmxValue("empty-config", "ResetTimeout", "5 seconds")
    assertJmxValue("empty-config", "MaxResetTimeout", "36500 days")
    assertJmxValue("empty-config", "ExponentialBackoffFactor", 1.0)
  }

  def assertJmxValue(name: String, key: String, expectedValue: Any) = {
    val oName = ObjectName.getInstance(
      s"org.squbs.configuration:type=squbs.circuitbreaker,name=${ObjectName.quote(name)}")
    val actualValue = ManagementFactory.getPlatformMBeanServer.getAttribute(oName, key)
    actualValue shouldEqual expectedValue
  }
}
