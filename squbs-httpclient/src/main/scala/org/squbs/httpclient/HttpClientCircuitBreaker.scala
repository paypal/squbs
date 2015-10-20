/*
 *  Copyright 2015 PayPal
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

import akka.actor.{ActorRefFactory, ActorSystem}
import akka.pattern.CircuitBreakerOpenException
import org.squbs.httpclient.ServiceCallStatus.ServiceCallStatus
import spray.http.HttpResponse

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}


object CircuitBreakerStatus extends Enumeration {
  type CircuitBreakerStatus = Value
  val Closed, Open, HalfOpen = Value
}

object ServiceCallStatus extends Enumeration {
  type ServiceCallStatus = Value
  val Success, Fallback, FailFast, Exception = Value
}

case class ServiceCall(callTime: Long, status: ServiceCallStatus)

trait CircuitBreakerSupport{

  def withCircuitBreaker(client: HttpClientState, response: => Future[HttpResponse])
                        (implicit actorFactory: ActorRefFactory) = {
    import actorFactory.dispatcher
    val runCircuitBreaker = client.cb.withCircuitBreaker[HttpResponse](response)
    import client.endpoint.config.settings.circuitBreakerConfig.fallbackHttpResponse
    runCircuitBreaker onComplete {
      case Success(r) => client.cbMetrics.add(ServiceCallStatus.Success, System.nanoTime)
      case Failure(e: CircuitBreakerOpenException) => fallbackHttpResponse match {
        case Some(r) => client.cbMetrics.add(ServiceCallStatus.Fallback, System.nanoTime)
        case None => client.cbMetrics.add(ServiceCallStatus.FailFast, System.nanoTime)
      }
      case Failure(e) => client.cbMetrics.add(ServiceCallStatus.Exception, System.nanoTime)
    }
    fallbackHttpResponse map { fallback =>
      runCircuitBreaker recover {
        case e: CircuitBreakerOpenException => fallback
      }
    } getOrElse runCircuitBreaker
  }
}

class CircuitBreakerMetrics(val units: Int, val unitSize: FiniteDuration)(implicit val system: ActorSystem) {

  require(units >= 1)

  class CBStat(var successTimes: Long, var fallbackTimes: Long, var failFastTimes: Long, var exceptionTimes: Long)

  class CBStatBucket(var successTimes: Int, var fallbackTimes: Int, var failFastTimes: Int, var exceptionTimes: Int)

  private[httpclient] val bucketCount = units + 1

  // Note: We use Array for the fastest access and lightest weight to this non-changing list
  private[httpclient] val buckets = Array.fill(bucketCount){ new CBStatBucket(0, 0, 0, 0) }
  private[httpclient] val total = new CBStat(0, 0, 0, 0)
  private val unitNanos = unitSize.toNanos
  private val cancellable = scheduleCleanup()

  def add(status: ServiceCallStatus, time: Long): Unit = {
    status match {
      case ServiceCallStatus.Success =>
        total.successTimes += 1
        currentBucket(time).successTimes += 1
      case ServiceCallStatus.Fallback =>
        total.fallbackTimes += 1
        currentBucket(time).fallbackTimes += 1
      case ServiceCallStatus.FailFast =>
        total.failFastTimes += 1
        currentBucket(time).failFastTimes += 1
      case ServiceCallStatus.Exception =>
        total.exceptionTimes += 1
        currentBucket(time).exceptionTimes += 1
    }
  }

  def currentBucket(time: Long) = buckets(currentIndex(time))

  def currentIndex(time: Long) = {
    // Note: currentTime can be negative so signedIdx can be negative
    // signShift is 0 if positive and 1 if negative
    // Avoid using if statements (branches) to not throw off the CPU's branch prediction.
    val signShift = time >>> 63
    val signedIdx = ((time / unitNanos % bucketCount) - signShift).toInt
    (signedIdx + bucketCount) % bucketCount // This ensures the index is 0 or positive
  }

  def cancel(): Unit = cancellable.cancel()

  private def clearNext(time: Long) = {
    val clearBucket = buckets((currentIndex(time) + 1) % bucketCount)
    clearBucket.successTimes = 0
    clearBucket.fallbackTimes = 0
    clearBucket.failFastTimes = 0
    clearBucket.exceptionTimes = 0
  }

  private def scheduleCleanup() = {
    val currentTime = System.nanoTime
    val bucketBase = currentTime - (currentTime % unitNanos)

    // Schedule the first cleanup 10ms or a quarter into the next bucket time, whichever is smaller.
    // 10ms is a safe margin already.
    val offset = math.min((10 milliseconds).toNanos, unitNanos / 4)

    // In case of negative current time, bucket base is larger than the current time.
    val firstCleanup =
      if (bucketBase > currentTime) bucketBase + offset
      else bucketBase + unitNanos + offset
    import system.dispatcher
    system.scheduler.schedule((firstCleanup - currentTime) nanos, unitSize) { clearNext(System.nanoTime) }
  }
}