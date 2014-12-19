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

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Random, Try}

class SimulateDistributionSpec extends FlatSpecLike with Matchers{

  "Random.nextGaussian" should "work as expected" in {
    import scala.concurrent.ExecutionContext.Implicits.global
    val timeoutPolicy = TimeoutPolicy(Some("test"), initial = 1 seconds, rule = 3 sigma)
    val sigma = 30
    val mean = 50
    for (i <- 0 until 10000) {
      val tx = timeoutPolicy.transaction
      Try{
        Await.ready(Future{
          val s = (Random.nextGaussian() * sigma + mean).round
          Thread.sleep(s)
        }, tx.waitTime)
      }
      tx.end()
      //      val metrics = timeoutPolicy.metrics
      //      println(s"average=${metrics.averageTime}, standardDeviation=${metrics.standardDeviation}")
    }

    Thread.sleep(5000)
    val metrics = timeoutPolicy.metrics
    println(s"average=${metrics.averageTime.toLong}, standardDeviation=${metrics.standardDeviation.toLong}")
    val succeedPercent = (metrics.totalCount - metrics.timeoutCount) / metrics.totalCount.toDouble
    println(succeedPercent)
    println(metrics)
  }

  "NegativeExponentialTruncated" should "works fine with TimeoutPolicy " in {
    negativeExponential(truncate = true)
  }

  "NegativeExponentialNotTruncated" should "works fine with TimeoutPolicy " in {
    negativeExponential(truncate = false)
  }

  def negativeExponential(truncate: Boolean): Unit = {
    val delay = getDelay(truncate = truncate, cycleMin = 20 millis, cycleMean = 30 millis, cycleMax = 80 milliseconds)

    import scala.concurrent.ExecutionContext.Implicits.global
    val timeoutPolicy = TimeoutPolicy(Some("test"), initial = 1 seconds, rule = 3 sigma)
    for (i <- 0 until 10000) {
      val tx = timeoutPolicy.transaction
      Try{
        Await.ready(Future{
          val s = delay().toMillis
          Thread.sleep(s)
        }, tx.waitTime)
      }
      tx.end()
//      val metrics = timeoutPolicy.metrics
    }

    Thread.sleep(5000)
    val metrics = timeoutPolicy.metrics
    println(s"average=${metrics.averageTime.toLong}, standardDeviation=${metrics.standardDeviation.toLong}")
    val succeedPercent = (metrics.totalCount - metrics.timeoutCount) / metrics.totalCount.toDouble
    println(succeedPercent)
    println(metrics)

  }

  def getDelay(truncate: Boolean = true,
               cycleMin: FiniteDuration = 0 seconds,
               cycleMean: FiniteDuration = 1 seconds,
               cycleMax: FiniteDuration = 5 seconds): () => FiniteDuration = {
    var mean = cycleMean.toNanos
    var shift = 0L

    if (!truncate) {
      shift = cycleMin.toNanos
      mean = mean - shift
    }

    () => {
      var delay = 0L
      if (cycleMean.toNanos > 0) {
        var x = Random.nextDouble()
        if (x == 0) {
          x = 1e-20d
        }
        delay = shift + (mean * -Math.log(x)).toLong
        if (delay < cycleMin.toNanos) {
          delay = cycleMin.toNanos
        } else if (delay > cycleMax.toNanos) {
          delay = cycleMax.toNanos
        }
      }
      delay nanoseconds
    }
  }
}
