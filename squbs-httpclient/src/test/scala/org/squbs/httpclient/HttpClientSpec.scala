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
import org.scalatest.{FlatSpecLike, BeforeAndAfterAll, Matchers}
import org.squbs.httpclient.dummy.DummyService._
import org.squbs.httpclient.dummy._
import org.squbs.httpclient.endpoint.{Endpoint, EndpointRegistry}
import spray.http.HttpHeaders.RawHeader
import spray.http.{HttpResponse, HttpHeader, StatusCodes}
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.util.Success

class HttpClientSpec extends TestKit(ActorSystem("HttpClientSpec")) with FlatSpecLike
    with DummyService with HttpClientTestKit with Matchers with BeforeAndAfterAll{

  implicit val timeout: Timeout = 3 seconds
  import org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol._


  override def beforeAll() {
    EndpointRegistry(system).register(DummyServiceEndpointResolver)
    startDummyService(system)
  }

  override def afterAll() {
    clearHttpClient()
    shutdownActorSystem()
  }

  "HttpClient with correct Endpoint calling raw.get" should "get the correct response" in {
    val response = HttpClientFactory.get("DummyService").raw.get("/view")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be (fullTeamJson)
  }

  "HttpClient with correct Endpoint calling raw.get and pass requestSettings" should "get the correct response" in {
    val reqSettings = RequestSettings(List[HttpHeader](RawHeader("req1-name", "test123456")), 5 seconds)
    val response = HttpClientFactory.get("DummyService").raw.get("/view", reqSettings)
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.headers should contain (RawHeader("res-req1-name", "res-test123456"))
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be (fullTeamJson)
  }

  "HttpClient with correct Endpoint calling raw.post and pass requestSettings" should "get the correct response" in {
    val reqSettings = RequestSettings(List[HttpHeader](RawHeader("req1-name", "test123456")), 5 seconds)
    val response = HttpClientFactory.get("DummyService").raw.post[Employee]("/view", None, reqSettings)
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.headers should contain (RawHeader("res-req1-name", "res-test123456"))
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be (fullTeamJson)
  }

  "HttpClient with correct Endpoint calling raw.get" should "prepend slash to the uri" in {
    val response = HttpClientFactory.get("DummyService").raw.get("view")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be (fullTeamJson)
  }

  "HttpClient with correct Endpoint calling raw.get and unmarshall object" should "get the correct response" in {
    val response = HttpClientFactory.get("DummyService").raw.get("/view")
    val result = Await.result(response, 3 seconds)
    import org.squbs.httpclient.pipeline.HttpClientUnmarshal._
    result.unmarshalTo[Team] should be (Success(fullTeam))
  }

  "HttpClient with correct Endpoint calling get" should "get the correct response" in {
    val response = HttpClientFactory.get("DummyService").get[Team]("/view")
    val result = Await.result(response, 3 seconds)
    result should be (fullTeam)
  }

  "HttpClient with correct Endpoint calling raw.head" should "get the correct response" in {
    val response = HttpClientFactory.get("DummyService").raw.head("/view")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.entity shouldBe empty
  }

  "HttpClient with correct Endpoint calling raw.options" should "get the correct response" in {
    val response = HttpClientFactory.get("DummyService").raw.options("/view")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
  }

  "HttpClient with correct Endpoint calling options" should "get the correct response" in {
    val response = HttpClientFactory.get("DummyService").options[Team]("/view")
    val result = Await.result(response, 3 seconds)
    result should be (fullTeam)
  }

  "HttpClient with correct Endpoint calling raw.options and unmarshall object" should "get the correct response" in {
    val response = HttpClientFactory.get("DummyService").raw.options("/view")
    val result = Await.result(response, 3 seconds)
    import org.squbs.httpclient.pipeline.HttpClientUnmarshal._
    result.unmarshalTo[Team] should be (Success(fullTeam))
  }

  "HttpClient with correct Endpoint calling raw.delete" should "get the correct response" in {
    val response = HttpClientFactory.get("DummyService").raw.delete("/del/4")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be (fullTeamWithDelJson)
  }

  "HttpClient with correct Endpoint calling raw.delete and unmarshall object" should "get the correct response" in {
    val response = HttpClientFactory.get("DummyService").raw.delete("/del/4")
    val result = Await.result(response, 3 seconds)
    import org.squbs.httpclient.pipeline.HttpClientUnmarshal._
    result.unmarshalTo[Team] should be (Success(fullTeamWithDel))
  }

  "HttpClient with correct Endpoint calling delete" should "get the correct response" in {
    val response = HttpClientFactory.get("DummyService").delete[Team]("/del/4")
    val result = Await.result(response, 3 seconds)
    result should be (fullTeamWithDel)
  }

  "HttpClient with correct Endpoint calling raw.post" should "get the correct response" in {
    val response = HttpClientFactory.get("DummyService").raw.post[Employee]("/add", Some(newTeamMember))
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be (fullTeamWithAddJson)
  }

  "HttpClient with correct Endpoint calling raw.post and unmarshall object" should "get the correct response" in {
    val response = HttpClientFactory.get("DummyService").raw.post[Employee]("/add", Some(newTeamMember))
    val result = Await.result(response, 3 seconds)
    import org.squbs.httpclient.pipeline.HttpClientUnmarshal._
    result.unmarshalTo[Team] should be (Success(fullTeamWithAdd))
  }

  "HttpClient with correct Endpoint calling post" should "get the correct response" in {
    val response = HttpClientFactory.get("DummyService").post[Employee, Team]("/add", Some(newTeamMember))
    val result = Await.result(response, 3 seconds)
    result should be (fullTeamWithAdd)
  }

  "HttpClient with correct Endpoint calling raw.put" should "get the correct response" in {
    val response = HttpClientFactory.get("DummyService").raw.put[Employee]("/add", Some(newTeamMember))
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be (fullTeamWithAddJson)
  }

  "HttpClient with correct Endpoint calling raw.put and unmarshall object" should "get the correct response" in {
    val response = HttpClientFactory.get("DummyService").raw.put[Employee]("/add", Some(newTeamMember))
    val result = Await.result(response, 3 seconds)
    import org.squbs.httpclient.pipeline.HttpClientUnmarshal._
    result.unmarshalTo[Team] should be (Success(fullTeamWithAdd))
  }

  "HttpClient with correct Endpoint calling put" should "get the correct response" in {
    val response = HttpClientFactory.get("DummyService").put[Employee, Team]("/add", Some(newTeamMember))
    val result = Await.result(response, 3 seconds)
    result should be (fullTeamWithAdd)
  }

  "HttpClient could use endpoint as service name directly without registering endpoint resolvers for " +
        "third party service call" should "get the correct response" in {
    val response: Future[HttpResponse] = HttpClientFactory.get(dummyServiceEndpoint).raw.get("/view")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be (fullTeamJson)
  }

  "HttpClient update configuration" should "get the correct behaviour" in {
    val httpClient = HttpClientFactory.get("DummyService")
    val newConfig = Configuration(settings = Settings(hostSettings =
      Configuration.defaultHostSettings.copy(maxRetries = 11)))
    val updatedHttpClient = httpClient.withConfig(newConfig)
    EndpointRegistry(system).resolve("DummyService") should be (Some(Endpoint(dummyServiceEndpoint)))
    updatedHttpClient.endpoint should be (Endpoint(dummyServiceEndpoint, newConfig))
  }

  "HttpClient update settings" should "get the correct behaviour" in {
    val httpClient = HttpClientFactory.get("DummyService")
    val settings = Settings(hostSettings = Configuration.defaultHostSettings.copy(maxRetries = 20))
    val updatedHttpClient = httpClient.withSettings(settings)
    EndpointRegistry(system).resolve("DummyService") should be (Some(Endpoint(dummyServiceEndpoint)))
    updatedHttpClient.endpoint.config.settings should be (settings)
  }

  "HttpClient update pipeline" should "get the correct behaviour" in {
    val httpClient = HttpClientFactory.get("DummyService")
    val pipeline = Some(DummyRequestPipeline)
    val updatedHttpClient = httpClient.withPipeline(pipeline)
    EndpointRegistry(system).resolve("DummyService") should be (Some(Endpoint(dummyServiceEndpoint)))
    updatedHttpClient.endpoint.config.pipeline should be (pipeline)
  }

  "HttpClient with the correct endpoint sleep 10s" should "restablish the connection and get response" in {
    Thread.sleep(10000)
    val response: Future[HttpResponse] = HttpClientFactory.get("DummyService").raw.get("/view")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be (fullTeamJson)
  }

  "HttpClient with the correct endpoint and wrong unmarshal value" should "throw out PipelineException and failed" in {
    val response: Future[HttpResponse] = HttpClientFactory.get("DummyService").raw.get("/view")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    import org.squbs.httpclient.pipeline.HttpClientUnmarshal._
    result.unmarshalTo[String] shouldBe 'failure
  }

  "HttpClient with correct endpoint calling raw.get with not existing uri and unmarshall value" should "throw out UnsuccessfulResponseException and failed" in {
    val response: Future[HttpResponse] = HttpClientFactory.get("DummyService").raw.get("/notExisting")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.NotFound)
    import org.squbs.httpclient.pipeline.HttpClientUnmarshal._
    result.unmarshalTo[Team] shouldBe 'failure
  }

  "HttpClient with correct endpoint calling raw.get with not existing uri" should "get StatusCodes.NotFound" in {
    val response: Future[HttpResponse] = HttpClientFactory.get("DummyService").raw.get("/notExisting")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.NotFound)
  }

  "HttpClient with not existing endpoint" should "throw out HttpClientEndpointNotExistException" in {
    a[HttpClientEndpointNotExistException] should be thrownBy {
      HttpClientFactory.get("NotExistingService").raw.get("/notExisting")
    }
  }

  "HttpClient buildRequestUri" should "have the correct behaviour" in {
    HttpClientPathBuilder.buildRequestUri("/") should be ("/")
    HttpClientPathBuilder.buildRequestUri("/abc") should be ("/abc")
    HttpClientPathBuilder.buildRequestUri("/abc/") should be ("/abc/")
    HttpClientPathBuilder.buildRequestUri("/abc/", Map ("n1" -> "v1")) should be ("/abc?n1=v1")
    val map1 = Map ("d" -> 1.23d, "f" -> 2.3f, "l" -> List[String]("a", "b", "c"))
    HttpClientPathBuilder.buildRequestUri("/abc/", map1) should include ("d=1.23")
    HttpClientPathBuilder.buildRequestUri("/abc/", map1) should include ("f=2.3") //should be ("/abc?d=1.23&f=2.3")
    val map2 = Map ("d" -> 1.23d, "f" -> 2.3f, "b" -> true, "c" -> 'a', "l" -> 12345L,
        "i" -> 100, "s" -> "Hello", "by" -> "1".toByte, "sh" -> "2".toShort)
    HttpClientPathBuilder.buildRequestUri("/abc/", map2) should include ("s=Hello")
    HttpClientPathBuilder.buildRequestUri("/abc/", map2) should include ("f=2.3")
    HttpClientPathBuilder.buildRequestUri("/abc/", map2) should include ("i=100")
    HttpClientPathBuilder.buildRequestUri("/abc/", map2) should include ("b=true")
    HttpClientPathBuilder.buildRequestUri("/abc/", map2) should include ("by=1")
    HttpClientPathBuilder.buildRequestUri("/abc/", map2) should include ("c=a")
    HttpClientPathBuilder.buildRequestUri("/abc/", map2) should include ("d=1.23")
    //should be ("/abc?s=Hello&f=2.3&i=100&b=true&by=1&c=a&d=1.23&sh=2")
    HttpClientPathBuilder.buildRequestUri("/abc/", map2) should include ("sh=2")
    HttpClientPathBuilder.buildRequestUri("/abc/", Map ("n1" -> "v1", "n2" -> "v2")) should include ("n1=v1")
    //should be ("/abc?n1=v1&n2=v2")
    HttpClientPathBuilder.buildRequestUri("/abc/", Map ("n1" -> "v1", "n2" -> "v2")) should not include "n1=v2"
    HttpClientPathBuilder.buildRequestUri("/abc/", Map ("n1" -> "v1&", "n2" -> "v2%")) should include ("v1%26")
    //should be ("/abc?n1=v1%26&n2=v2%25")
    HttpClientPathBuilder.buildRequestUri("/abc/", Map ("n1" -> "v1&", "n2" -> "v2%")) should include ("n2=v2%25")
  }

  //  "HttpClient with fallback HttpResponse" should "get correct fallback logic" in {
  //    val fallbackHttpResponse = HttpResponse()
  //    val httpClient = HttpClientFactory.get("DummyService").withFallback(Some(fallbackHttpResponse))
  //    httpClient.endpoint.config.circuitBreakerConfig.fallbackHttpResponse should be (Some(fallbackHttpResponse))
  //  }

  //  "MarkDown/MarkUp HttpClient" should "have the correct behaviour" in {
  //    implicit val ec = system.dispatcher
  //    val httpClient = HttpClientFactory.get("DummyService")
  //    httpClient.markDown
  //    val response = httpClient.get("/view")
  //    try{
  //      Await.result(response, 3 seconds)
  //    } catch {
  //      case e: Exception =>
  //        e should be (HttpClientMarkDownException("DummyService"))
  //    }
  //    httpClient.markUp
  //    val updatedResponse = httpClient.get("/view")
  //    val updatedResult = Await.result(updatedResponse, 3 seconds)
  //    updatedResult.status should be (StatusCodes.OK)
  //    updatedResult.entity should not be empty
  //    updatedResult.entity.data should not be empty
  //    updatedResult.entity.data.asString should be (fullTeamJson)
  //  }
}