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

import akka.actor.ActorRefFactory
import org.squbs.httpclient.CircuitBreakerStatus.CircuitBreakerStatus
import org.squbs.httpclient.ServiceCallStatus.ServiceCallStatus
import spray.http.HttpResponse

import scala.collection.mutable.ListBuffer
import scala.concurrent._

object CircuitBreakerStatus extends Enumeration {
  type CircuitBreakerStatus = Value
  val Closed, Open, HalfOpen = Value
}

object ServiceCallStatus extends Enumeration {
  type ServiceCallStatus = Value
  val Success, Fallback, FailFast, Exception = Value
}

case class ServiceCall(callTime: Long, status: ServiceCallStatus)

case class CircuitBreakerMetrics(var status: CircuitBreakerStatus,
                                 var successTimes: Long,
                                 var fallbackTimes: Long,
                                 var failFastTimes: Long,
                                 var exceptionTimes: Long,
                                 var cbLastDurationCall: ListBuffer[ServiceCall])

trait CircuitBreakerSupport{

  def withCircuitBreaker(client: HttpClient, response: => Future[HttpResponse])(implicit actorFactory: ActorRefFactory) = {
    import actorFactory.dispatcher
    val runCircuitBreaker = client.cb.withCircuitBreaker[HttpResponse](response)
    val fallbackHttpResponse = client.endpoint.config.settings.circuitBreakerConfig.fallbackHttpResponse
    (fallbackHttpResponse, client.cbMetrics.status) match {
      case (Some(resp), CircuitBreakerStatus.Closed) =>
        collectCbMetrics(client, ServiceCallStatus.Success)
        runCircuitBreaker fallbackTo Future{resp}
      case (None, CircuitBreakerStatus.Closed) =>
        collectCbMetrics(client, ServiceCallStatus.Success)
        runCircuitBreaker
      case (Some(resp), _) =>
        collectCbMetrics(client, ServiceCallStatus.Fallback)
        runCircuitBreaker fallbackTo Future{resp}
      case (None, _)           =>
        collectCbMetrics(client, ServiceCallStatus.FailFast)
        runCircuitBreaker
    }
  }

  def collectCbMetrics(client: HttpClient, status: ServiceCallStatus)(implicit actorFactory: ActorRefFactory) = {
    val cbLastDurationCall = client.cbMetrics.cbLastDurationCall
    val currentTime = System.currentTimeMillis
    val lastDuration = client.endpoint.config.settings.circuitBreakerConfig.lastDuration.toMillis
    status match {
      case ServiceCallStatus.Success =>
        client.cbMetrics.successTimes += 1
        cbLastDurationCall.append(ServiceCall(currentTime, ServiceCallStatus.Success))
      case ServiceCallStatus.Fallback =>
        client.cbMetrics.fallbackTimes += 1
        cbLastDurationCall.append(ServiceCall(currentTime, ServiceCallStatus.Fallback))
      case ServiceCallStatus.FailFast =>
        client.cbMetrics.failFastTimes += 1
        cbLastDurationCall.append(ServiceCall(currentTime, ServiceCallStatus.FailFast))
      case ServiceCallStatus.Exception =>
        client.cbMetrics.exceptionTimes += 1
        cbLastDurationCall.append(ServiceCall(currentTime, ServiceCallStatus.Exception))
    }
    client.cbMetrics.cbLastDurationCall = cbLastDurationCall.dropWhile(_.callTime + lastDuration <= currentTime)
    HttpClientManager.get(actorFactory).httpClientMap.put((client.name, client.env), client)
  }
}