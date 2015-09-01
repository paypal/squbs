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
package org.squbs.httpclient.demo

import akka.actor.ActorSystem
import akka.pattern.CircuitBreakerOpenException
import org.squbs.httpclient._
import org.squbs.httpclient.endpoint.{Endpoint, EndpointRegistry, EndpointResolver}
import org.squbs.httpclient.env.Environment
import org.squbs.testkit.Timeouts._

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object CircuitBreakerMain1 extends App{

  implicit val system = ActorSystem("CircuitBreakerMain1")
  implicit val ec = system.dispatcher


  EndpointRegistry(system).register(new EndpointResolver{

    override def resolve(svcName: String, env: Environment): Option[Endpoint] = {
      if (svcName == name) Some(Endpoint("http://localhost:8888")) else None
    }

    override def name: String = "DummyService"
  })
  val httpClient = HttpClientFactory.get("DummyService").
    withConfig(Configuration().copy(settings = Settings(hostSettings =
    Configuration.defaultHostSettings.copy(maxRetries = 0),
    circuitBreakerConfig = CircuitBreakerSettings().copy(callTimeout = 1 second))))
  while(true){
    Thread.sleep(2000)
    //    httpClient.get("/view") onComplete {
    //      case Success(httpResponse) =>
    //        println(s"call success, body is: ${httpResponse.entity.data.asString}, (status, success, fallback, failfast, exception):(${httpClient.cbMetrics.status}, ${httpClient.cbMetrics.successTimes}, ${httpClient.cbMetrics.fallbackTimes}, ${httpClient.cbMetrics.failFastTimes}, ${httpClient.cbMetrics.exceptionTimes}), cbLastMinCall:${httpClient.cbMetrics.cbLastMinCall.size}")
    //      case Failure(e: CircuitBreakerOpenException) =>
    //        println(s"circuitBreaker open! remaining time is: ${e.remainingDuration.toSeconds}, (status, success, fallback, failfast, exception):(${httpClient.cbMetrics.status}, ${httpClient.cbMetrics.successTimes}, ${httpClient.cbMetrics.fallbackTimes}, ${httpClient.cbMetrics.failFastTimes}, ${httpClient.cbMetrics.exceptionTimes}), cbLastMinCall:${httpClient.cbMetrics.cbLastMinCall.size}")
    //      case Failure(throwable) =>
    //        println(s"exception is: ${throwable.getMessage}, (status, success, fallback, failfast, exception):(${httpClient.cbMetrics.status}, ${httpClient.cbMetrics.successTimes}, ${httpClient.cbMetrics.fallbackTimes}, ${httpClient.cbMetrics.failFastTimes}, ${httpClient.cbMetrics.exceptionTimes}), cbLastMinCall:${httpClient.cbMetrics.cbLastMinCall.size}")
    //    }
    while(true){
      Thread.sleep(2000)
      httpClient.raw.get("/view") onComplete {
        case Success(httpResponse) =>
          println(s"call success, body is: ${httpResponse.entity.data.asString}, " +
            s"error rate is: ${CircuitBreakerBean(system).getHttpClientCircuitBreakerInfo.get(0).lastDurationErrorRate}")
        case Failure(e: CircuitBreakerOpenException) =>
          println(s"circuitBreaker open! remaining time is: ${e.remainingDuration.toSeconds}, " +
            s"error rate is: ${CircuitBreakerBean(system).getHttpClientCircuitBreakerInfo.get(0).lastDurationErrorRate}")
        case Failure(throwable) =>
          println(s"exception is: ${throwable.getMessage}, " +
            s"error rate is: ${CircuitBreakerBean(system).getHttpClientCircuitBreakerInfo.get(0).lastDurationErrorRate}")
      }
    }
  }
}