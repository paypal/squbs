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