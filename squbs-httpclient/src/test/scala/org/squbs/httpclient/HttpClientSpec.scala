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
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import org.squbs.httpclient.dummy.DummyService._
import org.squbs.httpclient.dummy._
import org.squbs.httpclient.endpoint.{Endpoint, EndpointRegistry}
import spray.http.StatusCodes
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Success

class HttpClientSpec extends FlatSpec with DummyService with HttpClientTestKit with Matchers with BeforeAndAfterAll{

  implicit val system = ActorSystem("HttpClientSpec")
  import org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol._


  override def beforeAll {
    EndpointRegistry.register(DummyServiceEndpointResolver)
    startDummyService(system)
  }

  override def afterAll {
    clearHttpClient
    shutdownActorSystem
  }

  "HttpClient with correct Endpoint calling get" should "get the correct response" in {
    val response = HttpClientFactory.get("DummyService").get("/view")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.entity.nonEmpty should be (true)
    result.entity.data.nonEmpty should be (true)
    result.entity.data.asString should be (fullTeamJson)
  }

  "HttpClient with correct Endpoint calling get" should "prepend slash to the uri" in {
    val response = HttpClientFactory.get("DummyService").get("view")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.entity.nonEmpty should be (true)
    result.entity.data.nonEmpty should be (true)
    result.entity.data.asString should be (fullTeamJson)
  }

  "HttpClient with correct Endpoint calling get and unmarshall object" should "get the correct response" in {
    val response = HttpClientFactory.get("DummyService").get("/view")
    val result = Await.result(response, 3 seconds)
    import org.squbs.httpclient.pipeline.HttpClientUnmarshal._
    result.unmarshalTo[Team] should be (Success(fullTeam))
  }

  "HttpClient with correct Endpoint calling head" should "get the correct response" in {
    val response = HttpClientFactory.get("DummyService").head("/view")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.entity.nonEmpty should be (false)
  }

  "HttpClient with correct Endpoint calling options" should "get the correct response" in {
    val response = HttpClientFactory.get("DummyService").options("/view")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.entity.nonEmpty should be (true)
    result.entity.data.nonEmpty should be (true)
  }

  "HttpClient with correct Endpoint calling options and unmarshall object" should "get the correct response" in {
    val response = HttpClientFactory.get("DummyService").options("/view")
    val result = Await.result(response, 3 seconds)
    import org.squbs.httpclient.pipeline.HttpClientUnmarshal._
    result.unmarshalTo[Team] should be (Success(fullTeam))
  }

  "HttpClient with correct Endpoint calling delete" should "get the correct response" in {
    val response = HttpClientFactory.get("DummyService").delete("/del/4")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.entity.nonEmpty should be (true)
    result.entity.data.nonEmpty should be (true)
    result.entity.data.asString should be (fullTeamWithDelJson)
  }

  "HttpClient with correct Endpoint calling delete and unmarshall object" should "get the correct response" in {
    val response = HttpClientFactory.get("DummyService").delete("/del/4")
    val result = Await.result(response, 3 seconds)
    import org.squbs.httpclient.pipeline.HttpClientUnmarshal._
    result.unmarshalTo[Team] should be (Success(fullTeamWithDel))
  }

  "HttpClient with correct Endpoint calling post" should "get the correct response" in {
    val response = HttpClientFactory.get("DummyService").post[Employee]("/add", newTeamMember)
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.entity.nonEmpty should be (true)
    result.entity.data.nonEmpty should be (true)
    result.entity.data.asString should be (fullTeamWithAddJson)
  }

  "HttpClient with correct Endpoint calling post and unmarshall object" should "get the correct response" in {
    val response = HttpClientFactory.get("DummyService").post[Employee]("/add", newTeamMember)
    val result = Await.result(response, 3 seconds)
    import org.squbs.httpclient.pipeline.HttpClientUnmarshal._
    result.unmarshalTo[Team] should be (Success(fullTeamWithAdd))
  }

  "HttpClient with correct Endpoint calling put" should "get the correct response" in {
    val response = HttpClientFactory.get("DummyService").put[Employee]("/add", newTeamMember)
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.entity.nonEmpty should be (true)
    result.entity.data.nonEmpty should be (true)
    result.entity.data.asString should be (fullTeamWithAddJson)
  }

  "HttpClient with correct Endpoint calling put and unmarshall object" should "get the correct response" in {
    val response = HttpClientFactory.get("DummyService").put[Employee]("/add", newTeamMember)
    val result = Await.result(response, 3 seconds)
    import org.squbs.httpclient.pipeline.HttpClientUnmarshal._
    result.unmarshalTo[Team] should be (Success(fullTeamWithAdd))
  }

  "HttpClient could be use endpoint as service name directly without registry endpoint resolvers, major target for third party service call" should "get the correct response" in {
    val response = HttpClientFactory.get(dummyServiceEndpoint).get("/view")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.entity.nonEmpty should be (true)
    result.entity.data.nonEmpty should be (true)
    result.entity.data.asString should be (fullTeamJson)
  }

  "HttpClient update configuration" should "get the correct behaviour" in {
    val httpClient = HttpClientFactory.get("DummyService")
    val newConfig = Configuration(hostSettings = Configuration.defaultHostSettings.copy(maxRetries = 11))
    val updatedHttpClient = httpClient.withConfig(newConfig)
    EndpointRegistry.resolve("DummyService") should be (Some(Endpoint(dummyServiceEndpoint)))
    updatedHttpClient.endpoint should be (Endpoint(dummyServiceEndpoint, newConfig))
  }

  "HttpClient with the correct endpoint sleep 10s" should "restablish the connection and get response" in {
    Thread.sleep(10000)
    val response = HttpClientFactory.get("DummyService").get("/view")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.entity.nonEmpty should be (true)
    result.entity.data.nonEmpty should be (true)
    result.entity.data.asString should be (fullTeamJson)
  }

  "HttpClient with the correct endpoint and wrong unmarshal value" should "throw out PipelineException and failed" in {
    val response = HttpClientFactory.get("DummyService").get("/view")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    import org.squbs.httpclient.pipeline.HttpClientUnmarshal._
    result.unmarshalTo[String].isFailure should be (true)
  }

  "HttpClient with correct endpoint calling get with not existing uri and unmarshall value" should "throw out UnsuccessfulResponseException and failed" in {
    val response = HttpClientFactory.get("DummyService").get("/notExisting")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.NotFound)
    import org.squbs.httpclient.pipeline.HttpClientUnmarshal._
    result.unmarshalTo[Team].isFailure should be (true)
  }

  "HttpClient with correct endpoint calling get with not existing uri" should "get StatusCodes.NotFound" in {
    val response = HttpClientFactory.get("DummyService").get("/notExisting")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.NotFound)
  }

  "HttpClient with not existing endpoint" should "throw out HttpClientEndpointNotExistException" in {
    a[HttpClientEndpointNotExistException] should be thrownBy {
      HttpClientFactory.get("NotExistingService").get("/notExisting")
    }
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
//    updatedResult.entity.nonEmpty should be (true)
//    updatedResult.entity.data.nonEmpty should be (true)
//    updatedResult.entity.data.asString should be (fullTeamJson)
//  }
}
