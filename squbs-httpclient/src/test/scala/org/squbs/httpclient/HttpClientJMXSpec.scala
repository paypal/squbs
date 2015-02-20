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

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.Timeout
import org.scalatest._
import org.squbs.httpclient.dummy.DummyService._
import org.squbs.httpclient.dummy.{DummyService, DummyProdEnvironmentResolver, DummyRequestResponsePipeline, DummyServiceEndpointResolver}
import org.squbs.httpclient.endpoint.{Endpoint, EndpointRegistry, EndpointResolver}
import org.squbs.httpclient.env._
import spray.can.Http.ClientConnectionType.Proxied
import spray.can.client.{ClientConnectionSettings, HostConnectorSettings}

import scala.concurrent.duration._
<<<<<<< HEAD
import scala.concurrent.{Future, Await}
import spray.http.{HttpResponse, StatusCodes}

class HttpClientJMXSpec extends TestKit(ActorSystem("HttpClientJMXSpec")) with FlatSpecLike with Matchers
with DummyService with HttpClientTestKit with BeforeAndAfterEach with BeforeAndAfterAll{
=======
import scala.concurrent.Await
import spray.http.StatusCodes

class HttpClientJMXSpec extends TestKit(ActorSystem("HttpClientJMXSpec")) with FlatSpecLike with Matchers
      with DummyService with HttpClientTestKit with BeforeAndAfterEach with BeforeAndAfterAll{
>>>>>>> refractoring the code SQUBS-504

  implicit val timeout: Timeout = 3 seconds

  override def beforeEach = {
    EndpointRegistry(system).register(new EndpointResolver {
      override def resolve(svcName: String, env: Environment = Default): Option[Endpoint] = Some(Endpoint("http://www.ebay.com"))
      override def name: String = "hello"
    })
  }

  override def afterEach = {
    clearHttpClient
  }

  override def beforeAll = {
    startDummyService(system)
  }

  override def afterAll() {
    shutdownActorSystem
  }

  "HttpClient with svcName" should "show up the correct value of HttpClientBean" in {
    HttpClientFactory.get("hello1")
    HttpClientFactory.get("hello2")
    HttpClientBean(system).getHttpClientInfo.size should be (2)
    findHttpClientBean(HttpClientBean(system).getHttpClientInfo, "hello1") should be (HttpClientInfo("hello1", "default", "http://www.ebay.com", "UP", "AutoProxied", 4, 5, 0, 20000, 10000, "", ""))
    findHttpClientBean(HttpClientBean(system).getHttpClientInfo, "hello2") should be (HttpClientInfo("hello2", "default", "http://www.ebay.com", "UP", "AutoProxied", 4, 5, 0, 20000, 10000, "", ""))
  }

  "HttpClient with pipeline" should "show up the correct value of HttpClientBean" in {
    val httpClient = HttpClientFactory.get("hello3").withConfig(Configuration().copy(pipeline = Some(DummyRequestResponsePipeline)))
    HttpClientFactory.get("hello4")
    HttpClientBean(system).getHttpClientInfo.size should be (2)
    Await.result(httpClient.readyFuture, 3 seconds)
<<<<<<< HEAD
    findHttpClientBean(HttpClientBean(system).getHttpClientInfo, "hello3") should be (HttpClientInfo("hello3", "default", "http://www.ebay.com", "UP", "AutoProxied", 4, 5, 0, 20000, 10000, "org.squbs.httpclient.pipeline.impl.RequestAddHeaderHandler","org.squbs.httpclient.pipeline.impl.ResponseAddHeaderHandler"))
=======
    findHttpClientBean(HttpClientBean(system).getHttpClientInfo, "hello3") should be (HttpClientInfo("hello3", "default", "http://www.ebay.com", "UP", "AutoProxied", 4, 5, 0, 20000, 10000, "org.squbs.httpclient.pipeline.impl.RequestAddHeaderHandler$$anonfun$processRequest$1","org.squbs.httpclient.pipeline.impl.ResponseAddHeaderHandler$$anonfun$processResponse$1"))
>>>>>>> refractoring the code SQUBS-504
    findHttpClientBean(HttpClientBean(system).getHttpClientInfo, "hello4") should be (HttpClientInfo("hello4", "default", "http://www.ebay.com", "UP", "AutoProxied", 4, 5, 0, 20000, 10000, "", ""))
  }

  "HttpClient with configuration" should "show up the correct value of HttpClientBean" in {
<<<<<<< HEAD
    HttpClientFactory.get("hello5").withConfig(Configuration(settings = Settings(hostSettings = HostConnectorSettings(10 ,10, 10, true, 10 seconds, ClientConnectionSettings(system)), connectionType = Proxied("www.ebay.com", 80))))
=======
    HttpClientFactory.get("hello5").withConfig(Configuration(hostSettings = HostConnectorSettings(10 ,10, 10, true, 10 seconds, ClientConnectionSettings(system)), connectionType = Proxied("www.ebay.com", 80)))
>>>>>>> refractoring the code SQUBS-504
    val markDownStatus = HttpClientFactory.get("hello6").markDown
    Await.result(markDownStatus, 3 seconds)
    HttpClientBean(system).getHttpClientInfo.size should be (2)
    findHttpClientBean(HttpClientBean(system).getHttpClientInfo, "hello5") should be (HttpClientInfo("hello5", "default", "http://www.ebay.com", "UP", "www.ebay.com:80", 10, 10, 10, 20000, 10000, "", ""))
    findHttpClientBean(HttpClientBean(system).getHttpClientInfo, "hello6") should be (HttpClientInfo("hello6", "default", "http://www.ebay.com", "DOWN", "AutoProxied", 4, 5, 0, 20000, 10000, "", ""))
  }

  "HttpClient Endpoint Resolver Info" should "show up the correct value of EndpointResolverBean" in {
    EndpointRegistry(system).register(DummyServiceEndpointResolver)
    EndpointResolverBean(system).getHttpClientEndpointResolverInfo.size should be (2)
    EndpointResolverBean(system).getHttpClientEndpointResolverInfo.get(0).position should be (0)
    EndpointRegistry(system).resolve("DummyService") should be (Some(Endpoint(dummyServiceEndpoint)))
    EndpointResolverBean(system).getHttpClientEndpointResolverInfo.get(0).resolver should be ("org.squbs.httpclient.dummy.DummyServiceEndpointResolver$")
  }

  "HttpClient Environment Resolver Info" should "show up the correct value of EnvironmentResolverBean" in {
    EnvironmentResolverBean(system).getHttpClientEnvironmentResolverInfo.size should be (0)
    EnvironmentRegistry(system).register(DummyProdEnvironmentResolver)
    EnvironmentResolverBean(system).getHttpClientEnvironmentResolverInfo.size should be (1)
    EnvironmentResolverBean(system).getHttpClientEnvironmentResolverInfo.get(0).position should be (0)
    EnvironmentRegistry(system).resolve("abc") should be (PROD)
    EnvironmentResolverBean(system).getHttpClientEnvironmentResolverInfo.get(0).resolver should be ("org.squbs.httpclient.dummy.DummyProdEnvironmentResolver$")
  }

  "HttpClient Circuit Breaker Info" should "show up some value of CircuitBreakerBean" in {
    CircuitBreakerBean(system).getHttpClientCircuitBreakerInfo.size should be (0)
    EndpointRegistry(system).register(DummyServiceEndpointResolver)
<<<<<<< HEAD
    val response: Future[HttpResponse] = HttpClientFactory.get("DummyService").raw.get("/view")
=======
    val response = HttpClientFactory.get("DummyService").get("/view")
>>>>>>> refractoring the code SQUBS-504
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    CircuitBreakerBean(system).getHttpClientCircuitBreakerInfo.size should be (1)
    val cbInfo = CircuitBreakerBean(system).getHttpClientCircuitBreakerInfo.get(0)
    cbInfo.name should be ("DummyService")
    cbInfo.status should be ("Closed")
    cbInfo.lastDurationConfig should be ("60 Seconds")
    println(s"success times:${cbInfo.successTimes}")
    println(s"fail fast times:${cbInfo.failFastTimes}")
    println(s"fallback times:${cbInfo.fallbackTimes}")
    println(s"exception times:${cbInfo.exceptionTimes}")
    cbInfo.successTimes should be (1)
    cbInfo.failFastTimes should be (0)
    cbInfo.fallbackTimes should be (0)
    cbInfo.exceptionTimes should be (0)
    cbInfo.lastDurationErrorRate should be ("0.00%")
    cbInfo.lastDurationFailFastRate should be ("0.00%")
    cbInfo.lastDurationExceptionRate should be ("0.00%")
  }

  def findHttpClientBean(beans: java.util.List[HttpClientInfo], name: String): HttpClientInfo = {
    import scala.collection.JavaConversions._
    beans.toList.find(_.name == name).get
  }
}