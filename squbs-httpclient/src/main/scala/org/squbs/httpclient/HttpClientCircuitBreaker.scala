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

package org.squbs.httpclient

import org.apache.pekko.actor.ActorSystem
import org.squbs.httpclient.ServiceCallStatus.ServiceCallStatus

import scala.concurrent.duration._


object CircuitBreakerStatus extends Enumeration {
  type CircuitBreakerStatus = Value
  val Closed, Open, HalfOpen = Value
}

object ServiceCallStatus extends Enumeration {
  type ServiceCallStatus = Value
  val Success, Fallback, FailFast, Exception = Value
}

case class ServiceCall(callTime: Long, status: ServiceCallStatus)


class CircuitBreakerMetrics(val units: Int, val unitSize: FiniteDuration)(implicit val system: ActorSystem) {

  class CBStat(var successTimes: Long, var fallbackTimes: Long, var failFastTimes: Long, var exceptionTimes: Long)

  class CBStatBucket(var successTimes: Int, var fallbackTimes: Int, var failFastTimes: Int, var exceptionTimes: Int)

  val metrics = new TimeBucketMetrics[CBStatBucket](units, unitSize,
    bucketCreator = { () => new CBStatBucket(0, 0, 0, 0) },
    clearBucket = { bucket =>
      bucket.successTimes = 0
      bucket.fallbackTimes = 0
      bucket.failFastTimes = 0
      bucket.exceptionTimes = 0
    })

  private[httpclient] val total = new CBStat(0, 0, 0, 0)

  def add(status: ServiceCallStatus, time: Long): Unit = {
    status match {
      case ServiceCallStatus.Success =>
        total.successTimes += 1
        metrics.currentBucket(time).successTimes += 1
      case ServiceCallStatus.Fallback =>
        total.fallbackTimes += 1
        metrics.currentBucket(time).fallbackTimes += 1
      case ServiceCallStatus.FailFast =>
        total.failFastTimes += 1
        metrics.currentBucket(time).failFastTimes += 1
      case ServiceCallStatus.Exception =>
        total.exceptionTimes += 1
        metrics.currentBucket(time).exceptionTimes += 1
    }
  }

  def cancel(): Unit = metrics.cancel()

  def history = metrics.history(System.nanoTime)
}
