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

import akka.actor.Status.Failure
import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import org.squbs.httpclient.Configuration._
import org.squbs.httpclient.HttpClientActorMessage.{MarkDownSuccess, MarkUpSuccess}
import org.squbs.httpclient.HttpClientManagerMessage.{Delete, Get, _}
import org.squbs.httpclient.dummy.DummyService._
import org.squbs.httpclient.dummy._
import org.squbs.httpclient.endpoint.{Endpoint, EndpointRegistry}
import org.squbs.httpclient.env.{Default, Environment}
import org.squbs.httpclient.pipeline.HttpClientUnmarshal
import org.squbs.pipeline.PipelineSetting
import spray.http.{HttpResponse, StatusCodes}
import spray.httpx.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.collection.concurrent.TrieMap
import scala.util.Success

class HttpClientManagerSpec extends TestKit(ActorSystem("HttpClientManagerSpec")) with FlatSpecLike
    with HttpClientTestKit with Matchers with ImplicitSender with BeforeAndAfterAll with DummyService{

  import org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol._

  implicit val _system = system

  override def beforeAll() {
    EndpointRegistry(system).register(new DummyServiceEndpointResolver)
    startDummyService(system)
    Thread.sleep(2000)
  }

  override def afterAll() {
    clearHttpClient()
    shutdownActorSystem()
  }

  "Path end check" should "return correct results" in {
    import spray.http.Uri.Path
    import HttpClientManager._
    Path("/foo/bar/").endsWithSlash shouldBe true
    Path("/foo/bar").endsWithSlash shouldBe false
    Path("foo/bar").endsWithSlash shouldBe false
    Path("foo/").endsWithSlash shouldBe true
    Path("/").endsWithSlash shouldBe true
    Path("").endsWithSlash shouldBe false
  }

  "httpClientMap" should "be empty before creating any http clients" in {
    val httpClientManager = HttpClientManager(system).httpClientManager
    httpClientManager ! GetAll
    type HttpClientMap = TrieMap[(String, Environment), (HttpClientState, ActorRef)]
    expectMsgType[HttpClientMap] shouldBe empty
  }

  //  "create a not existing httpclient" should "return ActorRef of HttpClientActor" in {
  //    createHttpClient("DummyService")
  //    HttpClientManager.httpClientMap.get(("DummyService", Default)) should not be (None)
  //    val client = HttpClientManager.httpClientMap.get(("DummyService", Default)).get
  //    client.name should be ("DummyService")
  //    client.env should be (Default)
  //    client.status should be (Status.UP)
  //    client.endpoint.config.pipeline should be (None)
  //    client.endpoint should be (Endpoint(dummyServiceEndpoint))
  //    deleteHttpClient("DummyService")
  //  }

  "get an existing httpclient" should "return ActorRef of HttpClientActor" in {
    createHttpClient("DummyService")
    HttpClientManager(system).httpClientMap.get(("DummyService", Default)) should not be None
    val client = HttpClientManager(system).httpClientMap.get(("DummyService", Default)).get
    client.name should be ("DummyService")
    client.env should be (Default)
    client.status should be (Status.UP)
    client.endpoint.config.pipeline should be (None)
    client.endpoint should matchPattern { case Endpoint(`dummyServiceEndpoint`, _) => }
    deleteHttpClient("DummyService")
  }

  "delete an existing httpclient" should "return DeleteHttpClientSuccess" in {
    createHttpClient("DummyService")
    deleteHttpClient("DummyService")
    HttpClientManager(system).httpClientMap shouldBe empty
  }

  "delete a not existing httpclient" should "return HttpClientNotExistException" in {
    val httpClientManager = HttpClientManager(system).httpClientManager
    httpClientManager ! Delete("DummyService")
    val existException = expectMsgType[HttpClientNotExistException]
    existException should matchPattern { case HttpClientNotExistException("DummyService", _) => }
  }

  "delete all existing httpclient" should "return DeleteAllHttpClientSuccess" in {
    createHttpClient("DummyService")
    createHttpClient("http://localhost:8080/test")
    HttpClientManager(system).httpClientMap should have size 2
    val httpClientManager = HttpClientManager(system).httpClientManager
    httpClientManager ! DeleteAll
    expectMsg(DeleteAllSuccess)
    HttpClientManager(system).httpClientMap shouldBe empty
  }

  "get all existing httpclient" should "return TrieMap[(String, Environment), (Client, ActorRef)]" in {
    createHttpClient("DummyService")
    createHttpClient("http://localhost:8080/test")
    HttpClientManager(system).httpClientMap should have size 2
    val httpClientManager = HttpClientManager(system).httpClientManager
    httpClientManager ! GetAll
    type HttpClientMap = TrieMap[(String, Environment), (HttpClientState, ActorRef)]
    val httpClientMap = expectMsgType[HttpClientMap]
    httpClientMap should have size 2
    httpClientMap should contain key ("DummyService", Default)
    httpClientMap should contain key ("http://localhost:8080/test", Default)
    deleteHttpClient("DummyService")
    deleteHttpClient("http://localhost:8080/test")
  }

  "HttpClientActor send UpdateConfig message" should "get the correct response" in {
    val httpClientActorRef = createHttpClient("DummyService")
    httpClientActorRef ! HttpClientActorMessage.UpdateConfig(Configuration().copy(settings =
      Settings(circuitBreakerConfig = CircuitBreakerSettings().copy(maxFailures = 50))))
    expectMsgType[ActorRef]
    val clientMap = HttpClientManager(system).httpClientMap
    clientMap should have size 1
    clientMap(("DummyService", Default)).endpoint.config.settings.circuitBreakerConfig.maxFailures should be (50)
    deleteHttpClient("DummyService")
  }

  "HttpClientActor send UpdateSettings message" should "get the correct response" in {
    val httpClientActorRef = createHttpClient("DummyService")
    httpClientActorRef ! HttpClientActorMessage.UpdateSettings(Settings(circuitBreakerConfig =
      CircuitBreakerSettings().copy(maxFailures = 100)))
    expectMsgType[ActorRef]
    val clientMap = HttpClientManager(system).httpClientMap
    clientMap should have size 1
    clientMap(("DummyService", Default)).endpoint.config.settings.circuitBreakerConfig.maxFailures should be (100)
    deleteHttpClient("DummyService")
  }

  "HttpClientActor send UpdatePipeline message" should "get the correct response" in {
    val httpClientActorRef = createHttpClient("DummyService")
    val pipelineSetting : Option[PipelineSetting] = Some(DummyRequestPipeline)
    httpClientActorRef ! HttpClientActorMessage.UpdatePipeline(pipelineSetting)
    expectMsgType[ActorRef]
    val clientMap = HttpClientManager(system).httpClientMap
    clientMap should have size 1
    clientMap(("DummyService", Default)).endpoint.config.pipeline should be (pipelineSetting)
    deleteHttpClient("DummyService")
  }

  "HttpClientActor with correct endpoint with MarkDown/MarkUp send Get message" should "get the correct response" in {
    val httpClientActorRef = createHttpClient("DummyService")
    httpClientActorRef ! HttpClientActorMessage.MarkDown
    expectMsg(MarkDownSuccess)
    httpClientActorRef ! HttpClientActorMessage.Get("/view")
    val error = expectMsgType[Failure]
    error.cause should be (HttpClientMarkDownException("DummyService"))
    httpClientActorRef ! HttpClientActorMessage.MarkUp
    expectMsg(MarkUpSuccess)
    httpClientActorRef ! HttpClientActorMessage.Get("/view")
    val result = expectMsgType[HttpResponse]
    result.status should be (StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be (fullTeamJson)
    httpClientActorRef ! HttpClientActorMessage.Close
    expectMsg(HttpClientActorMessage.CloseSuccess)
  }

  "HttpClientActor with correct endpoint send Get message" should "get the correct response" in {
    val httpClientActorRef = createHttpClient("DummyService")
    httpClientActorRef ! HttpClientActorMessage.Get("/view")
    val result = expectMsgType[HttpResponse]
    result.status should be (StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be (fullTeamJson)
    httpClientActorRef ! HttpClientActorMessage.Close
    expectMsg(HttpClientActorMessage.CloseSuccess)
  }

  "HttpClientActor with correct endpoint send Get message and " +
      "unmarshal HttpResponse" should "get the correct response" in {
    import HttpClientUnmarshal._
    val httpClientActorRef = createHttpClient("DummyService")
    httpClientActorRef ! HttpClientActorMessage.Get("/view")
    val result = expectMsgType[HttpResponse]
    result.status should be (StatusCodes.OK)
    result.unmarshalTo[Team] should be (Success(fullTeam))
    httpClientActorRef ! HttpClientActorMessage.Close
    expectMsg(HttpClientActorMessage.CloseSuccess)
  }

  "HttpClientActor with correct endpoint send Head message" should "get the correct response" in {
    val httpClientActorRef = createHttpClient("DummyService")
    httpClientActorRef ! HttpClientActorMessage.Head("/view")
    val result = expectMsgType[HttpResponse]
    result.status should be (StatusCodes.OK)
    result.entity shouldBe empty
    httpClientActorRef ! HttpClientActorMessage.Close
    expectMsg(HttpClientActorMessage.CloseSuccess)
  }

  "HttpClientActor with correct endpoint send Options message" should "get the correct response" in {
    val httpClientActorRef = createHttpClient("DummyService")
    httpClientActorRef ! HttpClientActorMessage.Options("/view")
    val result = expectMsgType[HttpResponse]
    result.status should be (StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be (fullTeamJson)
    httpClientActorRef ! HttpClientActorMessage.Close
    expectMsg(HttpClientActorMessage.CloseSuccess)
  }

  "HttpClientActor with correct endpoint send Options message and " +
      "unmarshal HttpResponse" should "get the correct response" in {
    import HttpClientUnmarshal._
    val httpClientActorRef = createHttpClient("DummyService")
    httpClientActorRef ! HttpClientActorMessage.Options("/view")
    val result = expectMsgType[HttpResponse]
    result.status should be (StatusCodes.OK)
    result.unmarshalTo[Team] should be (Success(fullTeam))
    httpClientActorRef ! HttpClientActorMessage.Close
    expectMsg(HttpClientActorMessage.CloseSuccess)
  }

  "HttpClientActor with correct endpoint send Delete message" should "get the correct response" in {
    val httpClientActorRef = createHttpClient("DummyService")
    httpClientActorRef ! HttpClientActorMessage.Delete("/del/4")
    val result = expectMsgType[HttpResponse]
    result.status should be (StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be (fullTeamWithDelJson)
    httpClientActorRef ! HttpClientActorMessage.Close
    expectMsg(HttpClientActorMessage.CloseSuccess)
  }

  "HttpClientActor with correct endpoint send Delete message and " +
      "unmarshal HttpResponse" should "get the correct response" in {
    import HttpClientUnmarshal._
    val httpClientActorRef = createHttpClient("DummyService")
    httpClientActorRef ! HttpClientActorMessage.Delete("/del/4")
    val result = expectMsgType[HttpResponse]
    result.status should be (StatusCodes.OK)
    result.unmarshalTo[Team] should be (Success(fullTeamWithDel))
    httpClientActorRef ! HttpClientActorMessage.Close
    expectMsg(HttpClientActorMessage.CloseSuccess)
  }

  "HttpClientActor with correct endpoint send Post message" should "get the correct response" in {
    val httpClientActorRef = createHttpClient("DummyService")
    httpClientActorRef ! HttpClientActorMessage.Post[Employee]("/add", Some(newTeamMember))
    val result = expectMsgType[HttpResponse]
    result.status should be (StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be (fullTeamWithAddJson)
    httpClientActorRef ! HttpClientActorMessage.Close
    expectMsg(HttpClientActorMessage.CloseSuccess)
  }

  "HttpClientActor with correct endpoint send Post message and " +
      "unmarshal HttpResponse" should "get the correct response" in {
    import HttpClientUnmarshal._
    val httpClientActorRef = createHttpClient("DummyService")
    httpClientActorRef ! HttpClientActorMessage.Post[Employee]("/add", Some(newTeamMember))
    val result = expectMsgType[HttpResponse]
    result.status should be (StatusCodes.OK)
    result.unmarshalTo[Team] should be (Success(fullTeamWithAdd))
    httpClientActorRef ! HttpClientActorMessage.Close
    expectMsg(HttpClientActorMessage.CloseSuccess)
  }

  "HttpClientActor with correct endpoint send Put message" should "get the correct response" in {
    val httpClientActorRef = createHttpClient("DummyService")
    httpClientActorRef ! HttpClientActorMessage.Put[Employee]("/add", Some(newTeamMember))
    val result = expectMsgType[HttpResponse]
    result.status should be (StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be (fullTeamWithAddJson)
    httpClientActorRef ! HttpClientActorMessage.Close
    expectMsg(HttpClientActorMessage.CloseSuccess)
  }

  "HttpClientActor with correct endpoint " +
      "send Put message with spray JSON marshall support" should "get the correct response" in {
    val httpClientActorRef = createHttpClient("DummyService")
    httpClientActorRef ! HttpClientActorMessage.Put[Employee]("/add", Some(newTeamMember))
    val result = expectMsgType[HttpResponse]
    result.status should be (StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be (fullTeamWithAddJson)
    httpClientActorRef ! HttpClientActorMessage.Close
    expectMsg(HttpClientActorMessage.CloseSuccess)
  }

  "HttpClientActor with correct endpoint " +
      "send Put message and unmarshal HttpResponse" should "get the correct response" in {
    import HttpClientUnmarshal._
    implicit val jsonFormat = TeamJsonProtocol.employeeFormat
    val httpClientActorRef = createHttpClient("DummyService")
    httpClientActorRef ! HttpClientActorMessage.Put[Employee](
      "/add", Some(newTeamMember), SprayJsonSupport.sprayJsonMarshaller[Employee])
    val result = expectMsgType[HttpResponse]
    result.status should be (StatusCodes.OK)
    result.unmarshalTo[Team] should be (Success(fullTeamWithAdd))
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

object TeamJsonProtocol extends DefaultJsonProtocol {
  implicit def employeeFormat: RootJsonFormat[Employee] = jsonFormat5(Employee)
}