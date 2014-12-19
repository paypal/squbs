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
package org.squbs.concurrent.timeout

import org.scalatest.{FlatSpecLike, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Try
class TimeoutPolicySpec extends FlatSpecLike with Matchers{

  "TimeoutPolicy Object" should "works fine" in {
    intercept[IllegalArgumentException](TimeoutPolicy(Some(""), null, null))
    intercept[IllegalArgumentException](TimeoutPolicy(Some(""), 1 second, fixedRule, debug = null))

    val preLength = TimeoutPolicy.policyMetrics.size
    val policy = TimeoutPolicy(None, 1 second, fixedRule)

    TimeoutPolicy.policyMetrics.size should be(preLength)
    policy.isInstanceOf[FixedTimeoutPolicy] should be(true)

    val sigmaPolicy = TimeoutPolicy(Some("test"), 1 seconds, 2 sigma, minSamples = 1)

    TimeoutPolicy.policyMetrics.get("test") shouldNot be (None)
  }

  "Run FixedTimeoutPolicy in function" should "work" in {
    val policy = TimeoutPolicy(name = Some("test"), initial = 1 second, fixedRule, minSamples = 1)

    for(i <- 0 until 10) {
      policy.execute(timeout => {
        timeout should be(1 second)
        Thread.sleep(10)
      })
    }

    Thread.sleep(3000)

    val metrics = policy.metrics
    metrics.name should contain ("test")
    metrics.initial should be (1 second)
    metrics.totalCount should be (10)
  }

  "Run SigmaTimeoutPolicy in explicit transaction" should "work" in {
    val policy = TimeoutPolicy(name = Some("test"), initial = 1 second, 3 sigma, minSamples = 1)

    for(i <- 0 until 10) {
      val tx = policy.transaction
      Try {
        Await.ready(Future{
          Thread.sleep(100)
          if (i > 2) {
            tx.waitTime.toMillis should be < 1000l
          }
        }, tx.waitTime)
      }
      tx.end()
    }

    Thread.sleep(3000)

    val metrics = policy.metrics
    metrics.name should contain ("test")
    metrics.initial should be (1 second)
    metrics.totalCount should be (10)
  }
}

