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
package org.squbs.httpclient

import scala.concurrent._
import spray.http.HttpResponse
import scala.Some
import org.squbs.httpclient.ServiceCallStatus.ServiceCallStatus
import scala.collection.mutable.ListBuffer
import org.squbs.httpclient.CircuitBreakerStatus.CircuitBreakerStatus
import akka.actor.ActorSystem

object CircuitBreakerStatus extends Enumeration {
  type CircuitBreakerStatus = Value
  val Closed, Open, HalfOpen = Value
}

object ServiceCallStatus extends Enumeration {
  type ServiceCallStatus = Value
  val Success, Fallback, FailFast, Exception = Value
}

case class ServiceCall(val callTime: Long, val status: ServiceCallStatus)

case class CircuitBreakerMetrics(var status: CircuitBreakerStatus,
                                 var successTimes: Long,
                                 var fallbackTimes: Long,
                                 var failFastTimes: Long,
                                 var exceptionTimes: Long,
                                 var cbLastDurationCall: ListBuffer[ServiceCall])

trait CircuitBreakerSupport{

  def withCircuitBreaker(client: Client, response: Future[HttpResponse])(implicit system: ActorSystem) = {
    implicit val ec = system.dispatcher
    val runCircuitBreaker = client.cb.withCircuitBreaker[HttpResponse](response)
    val fallbackHttpResponse = client.endpoint.config.circuitBreakerConfig.fallbackHttpResponse
    (fallbackHttpResponse, client.cbMetrics.status) match {
      case (Some(response), CircuitBreakerStatus.Closed) =>
        collectCbMetrics(client, ServiceCallStatus.Success)
        runCircuitBreaker fallbackTo future{response}
      case (None, CircuitBreakerStatus.Closed) =>
        collectCbMetrics(client, ServiceCallStatus.Success)
        runCircuitBreaker
      case (Some(response), CircuitBreakerStatus.Open | CircuitBreakerStatus.HalfOpen) =>
        collectCbMetrics(client, ServiceCallStatus.Fallback)
        runCircuitBreaker fallbackTo future{response}
      case (None, CircuitBreakerStatus.Open | CircuitBreakerStatus.HalfOpen)           =>
        collectCbMetrics(client, ServiceCallStatus.FailFast)
        runCircuitBreaker
    }
  }

  def collectCbMetrics(client: Client, status: ServiceCallStatus) = {
    val cbLastDurationCall = client.cbMetrics.cbLastDurationCall
    val currentTime = System.currentTimeMillis
    val lastDuration = client.endpoint.config.circuitBreakerConfig.lastDuration.toMillis
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
  }
}
