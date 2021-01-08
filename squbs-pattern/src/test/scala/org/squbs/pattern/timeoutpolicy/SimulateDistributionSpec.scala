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

package org.squbs.pattern.timeoutpolicy

import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Random, Try}

class SimulateDistributionSpec extends AnyFlatSpecLike with Matchers {

  "Random.nextGaussian" should "work as expected" in {
    import scala.concurrent.ExecutionContext.Implicits.global
    val timeoutPolicy = TimeoutPolicy(Some("test"), initial = 1.seconds, rule = 3.sigma, minSamples = 100, startOverCount = 500)
    val sigma = 30
    val mean = 50
    for (i <- 0 until 1000) {
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
    val delay = getDelay(truncate = truncate, cycleMin = 20.millis, cycleMean = 30.millis, cycleMax = 50.milliseconds)

    import scala.concurrent.ExecutionContext.Implicits.global
    val timeoutPolicy = TimeoutPolicy(Some("test"), initial = 1.seconds, rule = 3.sigma)
    for (i <- 0 until 1000) {
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
               cycleMin: FiniteDuration = 0.seconds,
               cycleMean: FiniteDuration = 1.seconds,
               cycleMax: FiniteDuration = 5.seconds): () => FiniteDuration = {

    val (shift, mean) =
      if (!truncate) {
        val shift1 = cycleMin.toNanos
        val mean1 = cycleMean.toNanos - shift1
        (shift1, mean1)
      } else (0L, cycleMean.toNanos)

    () => {
      val delay =
        if (cycleMean.toNanos > 0) {
          val x = {
            val ix = Random.nextDouble()
            if (ix == 0d) Double.MinPositiveValue else ix
          }
          val iDelay = shift + (mean * -Math.log(x)).toLong
          if (iDelay < cycleMin.toNanos)
            cycleMin.toNanos
          else if (iDelay > cycleMax.toNanos)
            cycleMax.toNanos
          else iDelay
        } else 0L
      delay.nanoseconds
    }
  }
}
