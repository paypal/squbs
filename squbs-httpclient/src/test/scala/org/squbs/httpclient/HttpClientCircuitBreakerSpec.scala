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

import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll, FlatSpec, Matchers}
import akka.actor.ActorSystem
import org.squbs.httpclient.endpoint.EndpointRegistry
import org.squbs.httpclient.dummy.DummyServiceEndpointResolver

class HttpClientCircuitBreakerSpec extends FlatSpec with Matchers with CircuitBreakerSupport with HttpClientTestKit with BeforeAndAfterAll with BeforeAndAfterEach{

  implicit val system = ActorSystem("HttpClientCircuitBreakerSpec")

  override def beforeEach {
    EndpointRegistry.register(DummyServiceEndpointResolver)
  }

  override def afterAll {
    shutdownActorSystem
  }

  override def afterEach {
    clearHttpClient
  }

  "HttpClient with Success ServiceCallStatus" should "go through the correct logic" in {
    val httpClient = HttpClientFactory.get("DummyService")
    httpClient.cbMetrics.successTimes should be (0)
    httpClient.cbMetrics.cbLastDurationCall.size should be (0)
    collectCbMetrics(httpClient, ServiceCallStatus.Success)
    httpClient.cbMetrics.successTimes should be (1)
    httpClient.cbMetrics.cbLastDurationCall.size should be (1)
    httpClient.cbMetrics.cbLastDurationCall(0).status should be (ServiceCallStatus.Success)
  }

  "HttpClient with Fallback ServiceCallStatus" should "go through the correct logic" in {
    val httpClient = HttpClientFactory.get("DummyService")
    httpClient.cbMetrics.fallbackTimes should be (0)
    httpClient.cbMetrics.cbLastDurationCall.size should be (0)
    collectCbMetrics(httpClient, ServiceCallStatus.Fallback)
    httpClient.cbMetrics.fallbackTimes should be (1)
    httpClient.cbMetrics.cbLastDurationCall.size should be (1)
    httpClient.cbMetrics.cbLastDurationCall(0).status should be (ServiceCallStatus.Fallback)
  }

  "HttpClient with FailFast ServiceCallStatus" should "go through the correct logic" in {
    val httpClient = HttpClientFactory.get("DummyService")
    httpClient.cbMetrics.failFastTimes should be (0)
    httpClient.cbMetrics.cbLastDurationCall.size should be (0)
    collectCbMetrics(httpClient, ServiceCallStatus.FailFast)
    httpClient.cbMetrics.failFastTimes should be (1)
    httpClient.cbMetrics.cbLastDurationCall.size should be (1)
    httpClient.cbMetrics.cbLastDurationCall(0).status should be (ServiceCallStatus.FailFast)
  }

  "HttpClient with Exception ServiceCallStatus" should "go through the correct logic" in {
    val httpClient = HttpClientFactory.get("DummyService")
    httpClient.cbMetrics.exceptionTimes should be (0)
    httpClient.cbMetrics.cbLastDurationCall.size should be (0)
    collectCbMetrics(httpClient, ServiceCallStatus.Exception)
    httpClient.cbMetrics.exceptionTimes should be (1)
    httpClient.cbMetrics.cbLastDurationCall.size should be (1)
    httpClient.cbMetrics.cbLastDurationCall(0).status should be (ServiceCallStatus.Exception)
  }
}
