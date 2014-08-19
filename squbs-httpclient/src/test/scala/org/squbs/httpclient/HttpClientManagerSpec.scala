/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the CONTRIBUTING file distributed with this work for
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

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{ActorRef, ActorSystem}
import org.scalatest.{BeforeAndAfterAll, Matchers, FlatSpecLike}
import org.squbs.httpclient.endpoint.{Endpoint, EndpointRegistry}
import org.squbs.httpclient.dummy.{Team, Employee, DummyService, DummyServiceEndpointResolver}
import org.squbs.httpclient.HttpClientManagerMessage._
import scala.collection.concurrent.TrieMap
import org.squbs.httpclient.env.{Default, Environment}
import org.squbs.httpclient.dummy.DummyService._
import org.squbs.httpclient.HttpClientManagerMessage.Get
import org.squbs.httpclient.HttpClientManagerMessage.Delete
import scala.Some
import spray.http.StatusCodes
import spray.util._

class HttpClientManagerSpec extends TestKit(ActorSystem("HttpClientManagerSpec")) with FlatSpecLike with HttpClientTestKit with Matchers with ImplicitSender with BeforeAndAfterAll with DummyService{

  import org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol._

  override def beforeAll {
    EndpointRegistry.register(DummyServiceEndpointResolver)
    startDummyService(system)
    Thread.sleep(2000)
  }

  override def afterAll {
    clearHttpClient
    shutdownActorSystem
  }

  "httpClientMap" should "be emtpy before creating any httpclients" in {
    val httpClientManager = HttpClientManager(system).httpClientManager
    httpClientManager ! GetAll
    type HttpClientMap = TrieMap[(String, Environment), (Client, ActorRef)]
    expectMsgType[HttpClientMap].isEmpty should be (true)
  }

  "create a not existing httpclient" should "return ActorRef of HttpClientActor" in {
    createHttpClient("DummyService")
    HttpClientManager.httpClientMap.get(("DummyService", Default)) should not be (None)
    val client = HttpClientManager.httpClientMap.get(("DummyService", Default)).get._1
    client.name should be ("DummyService")
    client.env should be (Default)
    client.status should be (Status.UP)
    client.endpoint.config.pipeline should be (None)
    client.endpoint should be (Endpoint(dummyServiceEndpoint))
    deleteHttpClient("DummyService")
  }

  "get an existing httpclient" should "return ActorRef of HttpClientActor" in {
    createHttpClient("DummyService")
    HttpClientManager.httpClientMap.get(("DummyService", Default)) should not be (None)
    val client = HttpClientManager.httpClientMap.get(("DummyService", Default)).get._1
    client.name should be ("DummyService")
    client.env should be (Default)
    client.status should be (Status.UP)
    client.endpoint.config.pipeline should be (None)
    client.endpoint should be (Endpoint(dummyServiceEndpoint))
    deleteHttpClient("DummyService")
  }

  "delete an existing httpclient" should "return DeleteHttpClientSuccess" in {
    createHttpClient("DummyService")
    deleteHttpClient("DummyService")
    HttpClientManager.httpClientMap should be (TrieMap.empty)
  }

  "delete a not existing httpclient" should "return HttpClientNotExistException" in {
    val httpClientManager = HttpClientManager(system).httpClientManager
    httpClientManager ! Delete("DummyService")
    val existException = expectMsgType[HttpClientNotExistException]
    existException should be (HttpClientNotExistException("DummyService"))
  }

  "delete all existing httpclient" should "return DeleteAllHttpClientSuccess" in {
    createHttpClient("DummyService")
    createHttpClient("http://localhost:8080/test")
    HttpClientManager.httpClientMap.size should be (2)
    val httpClientManager = HttpClientManager(system).httpClientManager
    httpClientManager ! DeleteAll
    expectMsg(DeleteAllSuccess)
    HttpClientManager.httpClientMap should be (TrieMap.empty)
  }

  "get all existing httpclient" should "return TrieMap[(String, Environment), (Client, ActorRef)]" in {
    createHttpClient("DummyService")
    createHttpClient("http://localhost:8080/test")
    HttpClientManager.httpClientMap.size should be (2)
    val httpClientManager = HttpClientManager(system).httpClientManager
    httpClientManager ! GetAll
    type HttpClientMap = TrieMap[(String, Environment), (Client, ActorRef)]
    val httpClientMap = expectMsgType[HttpClientMap]
    httpClientMap.size should be (2)
    httpClientMap.contains(("DummyService", Default)) should be (true)
    httpClientMap.contains(("http://localhost:8080/test", Default)) should be (true)
    deleteHttpClient("DummyService")
    deleteHttpClient("http://localhost:8080/test")
  }

  "HttpClientActor with correct endpoint send Get message" should "get the correct response" in {
    val httpClientActorRef = createHttpClient("DummyService")
    httpClientActorRef ! HttpClientActorMessage.Get("/view")
    val result = expectMsgType[HttpResponseWrapper]
    result.status should be (StatusCodes.OK)
    result.content.get.entity.nonEmpty should be (true)
    result.content.get.entity.data.nonEmpty should be (true)
    result.content.get.entity.data.asString should be (fullTeamJson)
    httpClientActorRef ! HttpClientActorMessage.Close
    expectMsg(HttpClientActorMessage.CloseSuccess)
  }

  "HttpClientActor with correct endpoint send Get message and unmarshall HttpResponse" should "get the correct response" in {
    import HttpClientManager._
    val httpClientActorRef = createHttpClient("DummyService")
    httpClientActorRef ! HttpClientActorMessage.Get("/view")
    val result = expectMsgType[HttpResponseWrapper]
    result.status should be (StatusCodes.OK)
    val httpResponse = result.content.get
    httpResponse.unmarshalTo[Team] should be (Right(fullTeam))
    httpClientActorRef ! HttpClientActorMessage.Close
    expectMsg(HttpClientActorMessage.CloseSuccess)
  }

  "HttpClientActor with correct endpoint send Head message" should "get the correct response" in {
    val httpClientActorRef = createHttpClient("DummyService")
    httpClientActorRef ! HttpClientActorMessage.Head("/view")
    val result = expectMsgType[HttpResponseWrapper]
    result.status should be (StatusCodes.OK)
    result.content.get.entity.nonEmpty should be (false)
    httpClientActorRef ! HttpClientActorMessage.Close
    expectMsg(HttpClientActorMessage.CloseSuccess)
  }

  "HttpClientActor with correct endpoint send Options message" should "get the correct response" in {
    val httpClientActorRef = createHttpClient("DummyService")
    httpClientActorRef ! HttpClientActorMessage.Options("/view")
    val result = expectMsgType[HttpResponseWrapper]
    result.status should be (StatusCodes.OK)
    result.content.get.entity.nonEmpty should be (true)
    result.content.get.entity.data.nonEmpty should be (true)
    result.content.get.entity.data.asString should be (fullTeamJson)
    httpClientActorRef ! HttpClientActorMessage.Close
    expectMsg(HttpClientActorMessage.CloseSuccess)
  }

  "HttpClientActor with correct endpoint send Options message nad unmarshall HttpResponse" should "get the correct response" in {
    import HttpClientManager._
    val httpClientActorRef = createHttpClient("DummyService")
    httpClientActorRef ! HttpClientActorMessage.Options("/view")
    val result = expectMsgType[HttpResponseWrapper]
    result.status should be (StatusCodes.OK)
    val httpResponse = result.content.get
    httpResponse.unmarshalTo[Team] should be (Right(fullTeam))
    httpClientActorRef ! HttpClientActorMessage.Close
    expectMsg(HttpClientActorMessage.CloseSuccess)
  }

  "HttpClientActor with correct endpoint send Delete message" should "get the correct response" in {
    val httpClientActorRef = createHttpClient("DummyService")
    httpClientActorRef ! HttpClientActorMessage.Delete("/del/4")
    val result = expectMsgType[HttpResponseWrapper]
    result.status should be (StatusCodes.OK)
    result.content.get.entity.nonEmpty should be (true)
    result.content.get.entity.data.nonEmpty should be (true)
    result.content.get.entity.data.asString should be (fullTeamWithDelJson)
    httpClientActorRef ! HttpClientActorMessage.Close
    expectMsg(HttpClientActorMessage.CloseSuccess)
  }

  "HttpClientActor with correct endpoint send Delete message and unmarshal HttpResponse" should "get the correct response" in {
    import HttpClientManager._
    val httpClientActorRef = createHttpClient("DummyService")
    httpClientActorRef ! HttpClientActorMessage.Delete("/del/4")
    val result = expectMsgType[HttpResponseWrapper]
    result.status should be (StatusCodes.OK)
    val httpResponse = result.content.get
    httpResponse.unmarshalTo[Team] should be (Right(fullTeamWithDel))
    httpClientActorRef ! HttpClientActorMessage.Close
    expectMsg(HttpClientActorMessage.CloseSuccess)
  }

  "HttpClientActor with correct endpoint send Post message" should "get the correct response" in {
    val httpClientActorRef = createHttpClient("DummyService")
    httpClientActorRef ! HttpClientActorMessage.Post[Employee]("/add", Some(newTeamMember))
    val result = expectMsgType[HttpResponseWrapper]
    result.status should be (StatusCodes.OK)
    result.content.get.entity.nonEmpty should be (true)
    result.content.get.entity.data.nonEmpty should be (true)
    result.content.get.entity.data.asString should be (fullTeamWithAddJson)
    httpClientActorRef ! HttpClientActorMessage.Close
    expectMsg(HttpClientActorMessage.CloseSuccess)
  }

  "HttpClientActor with correct endpoint send Post message and unmarshal HttpResponse" should "get the correct response" in {
    import HttpClientManager._
    val httpClientActorRef = createHttpClient("DummyService")
    httpClientActorRef ! HttpClientActorMessage.Post[Employee]("/add", Some(newTeamMember))
    val result = expectMsgType[HttpResponseWrapper]
    result.status should be (StatusCodes.OK)
    val httpResponse = result.content.get
    httpResponse.unmarshalTo[Team] should be (Right(fullTeamWithAdd))
    httpClientActorRef ! HttpClientActorMessage.Close
    expectMsg(HttpClientActorMessage.CloseSuccess)
  }

  "HttpClientActor with correct endpoint send Put message" should "get the correct response" in {
    val httpClientActorRef = createHttpClient("DummyService")
    httpClientActorRef ! HttpClientActorMessage.Put[Employee]("/add", Some(newTeamMember))
    val result = expectMsgType[HttpResponseWrapper]
    result.status should be (StatusCodes.OK)
    result.content.get.entity.nonEmpty should be (true)
    result.content.get.entity.data.nonEmpty should be (true)
    result.content.get.entity.data.asString should be (fullTeamWithAddJson)
    httpClientActorRef ! HttpClientActorMessage.Close
    expectMsg(HttpClientActorMessage.CloseSuccess)
  }

  "HttpClientActor with correct endpoint send Put message and unmarshal HttpResponse" should "get the correct response" in {
    import HttpClientManager._
    val httpClientActorRef = createHttpClient("DummyService")
    httpClientActorRef ! HttpClientActorMessage.Put[Employee]("/add", Some(newTeamMember))
    val result = expectMsgType[HttpResponseWrapper]
    result.status should be (StatusCodes.OK)
    val httpResponse = result.content.get
    httpResponse.unmarshalTo[Team] should be (Right(fullTeamWithAdd))
    httpClientActorRef ! HttpClientActorMessage.Close
    expectMsg(HttpClientActorMessage.CloseSuccess)
  }

  def createHttpClient(name: String) = {
    val httpClientManager = HttpClientManager(system).httpClientManager
    httpClientManager ! Get(name)
    expectMsgType[ActorRef]
  }

  def deleteHttpClient(name: String, env: Environment = Default) = {
    val httpClientManager = HttpClientManager(system).httpClientManager
    httpClientManager ! Delete(name, env)
    expectMsg(DeleteSuccess)
  }
}
