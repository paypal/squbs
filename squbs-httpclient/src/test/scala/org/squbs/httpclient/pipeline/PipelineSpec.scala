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
package org.squbs.httpclient.pipeline

import akka.actor.ActorSystem
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import org.squbs.httpclient.dummy.DummyService._
import org.squbs.httpclient.dummy._
import org.squbs.httpclient.endpoint.EndpointRegistry
import org.squbs.httpclient.{Configuration, HttpClientFactory, HttpClientTestKit}
import spray.http.HttpHeaders.RawHeader
import spray.http._
import spray.util._

class PipelineSpec extends FlatSpec with DummyService with HttpClientTestKit with Matchers with BeforeAndAfterAll with PipelineManager{

  implicit val system = ActorSystem("PipelineSpec")

  override def beforeAll = {
    EndpointRegistry.register(DummyServiceEndpointResolver)
    startDummyService(system)
  }

  override def afterAll = {
    clearHttpClient
    shutdownActorSystem
  }

  "Request Pipeline (invokeToHttpResponse)" should "have the correct behaviour" in {
    val httpClient = HttpClientFactory.get("DummyService").withConfig(Configuration().copy(pipeline = Some(DummyRequestPipeline)))
    val sendReceive = invokeToHttpResponse(httpClient)
    sendReceive.isSuccess should be (true)
    val request = HttpRequest(uri = Uri(s"$dummyServiceEndpoint/view"))
    val response = sendReceive.get(request).await
    response.status should be (StatusCodes.OK)
    response.headers contains (RawHeader("res-req1-name", "res-req1-value"))
  }

  "Response Pipeline (invokeToHttpResponse)" should "have the correct behaviour" in {
    val httpClient = HttpClientFactory.get("DummyService").withConfig(Configuration().copy(pipeline = Some(DummyResponsePipeline)))
    val sendReceive = invokeToHttpResponse(httpClient)
    sendReceive.isSuccess should be (true)
    val request = HttpRequest(uri = Uri(s"$dummyServiceEndpoint/view"))
    val response = sendReceive.get(request).await
    response.status should be (StatusCodes.OK)
    response.headers contains (RawHeader("res1-name", "res1-value"))
  }

  "Request-Response Pipeline (invokeToHttpResponse)" should "have the correct behaviour" in {
    val httpClient = HttpClientFactory.get("DummyService").withConfig(Configuration().copy(pipeline = Some(DummyRequestResponsePipeline)))
    val sendReceive = invokeToHttpResponse(httpClient)
    sendReceive.isSuccess should be (true)
    val request = HttpRequest(uri = Uri(s"$dummyServiceEndpoint/view"))
    val response = sendReceive.get(request).await
    response.status should be (StatusCodes.OK)
    response.headers contains (RawHeader("res-req2-name", "res-req2-value"))
    response.headers contains (RawHeader("res2-name", "res2-value"))
  }
}
