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

package org.squbs.httpclient.japi

import java.util.Optional

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.scalatest.OptionValues._
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import org.squbs.httpclient.Configuration._
import org.squbs.httpclient._
import org.squbs.httpclient.dummy.DummyService._
import org.squbs.httpclient.dummy._
import org.squbs.httpclient.endpoint.{Endpoint, EndpointRegistry}
import org.squbs.httpclient.json.JsonProtocol.ClassSupport
import org.squbs.httpclient.json.{JacksonProtocol, Json4sJacksonNoTypeHintsProtocol, JsonProtocol}
import org.squbs.pipeline.{PipelineSetting, SimplePipelineConfig}
import org.squbs.testkit.Timeouts._
import spray.http.HttpHeaders.RawHeader
import spray.http.{HttpResponse, HttpHeader, StatusCodes}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Success

class HttpClientJSpec extends TestKit(ActorSystem("HttpClientJSpec")) with FlatSpecLike
with DummyService with HttpClientTestKit with Matchers with BeforeAndAfterAll {

  import scala.compat.java8.OptionConverters._

  override def beforeAll() {
    Json4sJacksonNoTypeHintsProtocol.registerSerializer(EmployeeBeanSerializer)
    val mapper = new ObjectMapper().setVisibility(PropertyAccessor.FIELD, Visibility.ANY)
      .registerModule(DefaultScalaModule)
    JacksonProtocol.registerMapper(classOf[TeamBean], mapper)
    EndpointRegistry(system).register(new DummyServiceEndpointResolver()(system))
    startDummyService(system)
  }

  override def afterAll() {
    clearHttpClient()
    shutdownActorSystem()
  }

  "ConfigurationFactory" should "create the right base configuration" in {
    ConfigurationFactory.create(system) should be(Configuration())
  }

  "EndpointFactory" should "work" in {
    EndpointFactory.create("/test", system) should be(Endpoint("/test"))
  }

  "HttpClient with correct Endpoint calling raw.get" should "get the correct response" in {
    //val response = HttpClientFactory.get("DummyService").raw.get("/view")
    val response = HttpClientJ.rawGet("DummyService", system, "/view")
    val result = Await.result(response, awaitMax)
    result.status should be(StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be(fullTeamJson)
  }

  "HttpClient with correct Endpoint calling raw.get with normal class" should "get the correct response" in {
    //val response = HttpClientFactory.get("DummyService").raw.get("/view1")
    val response = HttpClientJ.rawGet("DummyService", system, "/view1")
    val result = Await.result(response, awaitMax)
    result.status should be(StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be(fullTeamJson)
  }

  "HttpClient with correct Endpoint calling raw.get with custom serializer" should "get the correct response" in {
    //val response = HttpClientFactory.get("DummyService").raw.get("/view2")
    val response = HttpClientJ.rawGet("DummyService", system, "/view2")
    val result = Await.result(response, awaitMax)
    result.status should be(StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be(fullTeamJson)
  }


  "HttpClient with correct Endpoint calling raw.get with java bean using case class" should "get the correct " +
    "response" in {
    //val response = HttpClientFactory.get("DummyService").raw.get("/view3")
    val response = HttpClientJ.rawGet("DummyService", system, "/view3")
    val result = Await.result(response, awaitMax)
    result.status should be(StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be(fullTeamJson)
  }


  "HttpClient with correct Endpoint calling raw.get with java bean" should "get the correct response" in {
    val response = HttpClientJ.rawGet("DummyService", system, "/viewj")
    val result = Await.result(response, awaitMax)
    result.status should be(StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be(fullTeamJson)
    println(result.entity.data.asString)

  }

  "HttpClient with correct Endpoint calling raw.get and pass requestSettings" should "get the correct response" in {
    val reqSettings = RequestSettings(List[HttpHeader](RawHeader("req1-name", "test123456")), awaitMax)
    //val response = HttpClientFactory.get("DummyService").raw.get("/view", reqSettings)
    val response = HttpClientJ.rawGet("DummyService", system, "/view", reqSettings)
    val result = Await.result(response, awaitMax)
    result.status should be(StatusCodes.OK)
    result.headers should contain(RawHeader("res-req1-name", "res-test123456"))
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be(fullTeamJson)
  }

  "HttpClient with correct Endpoint calling raw.post and pass requestSettings" should "get the correct response" in {
    val reqSettings = RequestSettings(List[HttpHeader](RawHeader("req1-name", "test123456")), awaitMax)
    //val response = HttpClientFactory.get("DummyService").raw.post[Employee]("/view", None, reqSettings)
    val response = HttpClientJ.rawPost("DummyService", system, "/view", Optional.empty[Employee], reqSettings)
    val result = Await.result(response, awaitMax)
    result.status should be(StatusCodes.OK)
    result.headers should contain(RawHeader("res-req1-name", "res-test123456"))
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be(fullTeamJson)
  }

  "HttpClient with correct Endpoint calling raw.post with marshaller and pass requestSettings" should "get the " +
    "correct response" in {
    val reqSettings = RequestSettings(List[HttpHeader](RawHeader("req1-name", "test123456")), awaitMax)
    //val response = HttpClientFactory.get("DummyService").raw.post[Employee]("/view", None, reqSettings)
    import JsonProtocol.ClassSupport._
    val response =
      HttpClientJ.rawPost("DummyService", system, "/view", Optional.empty[Employee], reqSettings, classOf[Employee])
    val result = Await.result(response, awaitMax)
    result.status should be(StatusCodes.OK)
    result.headers should contain(RawHeader("res-req1-name", "res-test123456"))
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be(fullTeamJson)
  }

  "HttpClient with correct Endpoint calling raw.get" should "prepend slash to the uri" in {
    //val response = HttpClientFactory.get("DummyService").raw.get("view")
    val response = HttpClientJ.rawGet("DummyService", system, "view")
    val result = Await.result(response, awaitMax)
    result.status should be(StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be(fullTeamJson)
  }

  "HttpClient with correct Endpoint calling raw.get and unmarshall object" should "get the correct response" in {
    //val response = HttpClientFactory.get("DummyService").raw.get("/view")
    val response = HttpClientJ.rawGet("DummyService", system, "/view")
    val result = Await.result(response, awaitMax)
    import org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol.json4sUnmarshaller
    import org.squbs.httpclient.pipeline.HttpClientUnmarshal._
    result.unmarshalTo[Team] should be(Success(fullTeam))
  }

  "HttpClient with correct Endpoint calling raw.get and unmarshall object with java bean" should "get the correct " +
    "response" in {
    //val response = HttpClientFactory.get("DummyService").raw.get("/viewj")
    val response = HttpClientJ.rawGet("DummyService", system, "/viewj")
    val result = Await.result(response, awaitMax)
    import JsonProtocol.ManifestSupport.manifestToUnmarshaller
    import org.squbs.httpclient.pipeline.HttpClientUnmarshal._
    result.unmarshalTo[TeamBean] should be(Success(fullTeamBean))

    import JsonProtocol.ClassSupport.classToFromResponseUnmarshaller
    result.unmarshalTo(classOf[TeamBean]) should be(Success(fullTeamBean))
  }

  "HttpClient with correct Endpoint calling raw.get and unmarshall object with java bean using case " +
    "class" should "get the correct response" in {
    val response = HttpClientJ.rawGet("DummyService", system, "/view3")
    val result = Await.result(response, awaitMax)
    import JsonProtocol.TypeTagSupport.typeTagToUnmarshaller
    import org.squbs.httpclient.pipeline.HttpClientUnmarshal._
    result.unmarshalTo[TeamBeanWithCaseClassMember] should be(Success(fullTeam3))

    import JsonProtocol.ClassSupport.classToFromResponseUnmarshaller
    result.unmarshalTo(classOf[TeamBeanWithCaseClassMember]) should be(Success(fullTeam3))
  }

  "HttpClient with correct Endpoint calling raw.get and unmarshall object with normal scala class" should "get the " +
    "correct response" in {
    //val response = HttpClientFactory.get("DummyService").raw.get("/view1")
    val response = HttpClientJ.rawGet("DummyService", system, "/view1")
    val result = Await.result(response, awaitMax)
    import JsonProtocol.ClassSupport.classToFromResponseUnmarshaller
    import org.squbs.httpclient.pipeline.HttpClientUnmarshal._
    result.unmarshalTo(classOf[Team1]) should be(Success(fullTeam1))

    import JsonProtocol.TypeTagSupport.typeTagToUnmarshaller
    result.unmarshalTo[Team1] should be(Success(fullTeam1))

  }


  "HttpClient with correct Endpoint calling get" should "get the correct response" in {
    //val response = HttpClientFactory.get("DummyService").get[Team]("/view")
    val response = HttpClientJ.get("DummyService", system, "/view", classOf[Team])
    val result = Await.result(response, awaitMax)
    result should be(fullTeam)
  }

  "HttpClient with correct Endpoint calling get with setting" should "get the correct response" in {

    val reqSettings = RequestSettings(List[HttpHeader](RawHeader("req1-name", "test123456")), awaitMax)
    val response = HttpClientJ.get("DummyService", system, "/view", classOf[Team], reqSettings)
    val result = Await.result(response, awaitMax)
    result should be(fullTeam)
  }

  "HttpClient with correct Endpoint calling get with unmarshaller" should "get the correct response" in {
    val unmarshaller = ClassSupport.classToFromResponseUnmarshaller(classOf[Team])
    val response = HttpClientJ.get("DummyService", system, "/view", unmarshaller)
    val result = Await.result(response, awaitMax)
    result should be(fullTeam)
  }

  "HttpClient with correct Endpoint calling get with unmarshaller and setting" should "get the correct response" in {
    val unmarshaller = ClassSupport.classToFromResponseUnmarshaller(classOf[Team])
    val response = HttpClientJ.get("DummyService", system, "/view", unmarshaller, RequestSettings())
    val result = Await.result(response, awaitMax)
    result should be(fullTeam)
  }

  "HttpClient with correct Endpoint calling raw.head" should "get the correct response" in {
    //val response = HttpClientFactory.get("DummyService").raw.head("/view")
    val response = HttpClientJ.rawHead("DummyService", system, "/view")
    val result = Await.result(response, awaitMax)
    result.status should be(StatusCodes.OK)
    result.entity shouldBe empty
  }

  "HttpClient with correct Endpoint calling raw.head with setting" should "get the correct response" in {
    //val response = HttpClientFactory.get("DummyService").raw.head("/view")
    val response = HttpClientJ.rawHead("DummyService", system, "/view", RequestSettings())
    val result = Await.result(response, awaitMax)
    result.status should be(StatusCodes.OK)
    result.entity shouldBe empty
  }

  "HttpClient with correct Endpoint calling raw.options" should "get the correct response" in {
    //val response = HttpClientFactory.get("DummyService").raw.options("/view")
    val response = HttpClientJ.rawOptions("DummyService", system, "/view")
    val result = Await.result(response, awaitMax)
    result.status should be(StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
  }

  "HttpClient with correct Endpoint calling raw.options with setting" should "get the correct response" in {
    //val response = HttpClientFactory.get("DummyService").raw.options("/view")
    val response = HttpClientJ.rawOptions("DummyService", system, "/view", RequestSettings())
    val result = Await.result(response, awaitMax)
    result.status should be(StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
  }

  "HttpClient with correct Endpoint calling options" should "get the correct response" in {
    //val response = HttpClientFactory.get("DummyService").options[Team]("/view")
    val response = HttpClientJ.options("DummyService", system, "/view", classOf[Team])
    val result = Await.result(response, awaitMax)
    result should be(fullTeam)
  }

  "HttpClient with correct Endpoint calling options with settings" should "get the correct response" in {
    //val response = HttpClientFactory.get("DummyService").options[Team]("/view")
    val response = HttpClientJ.options("DummyService", system, "/view", classOf[Team], RequestSettings())
    val result = Await.result(response, awaitMax)
    result should be(fullTeam)
  }

  "HttpClient with correct Endpoint calling options with marshaller" should "get the correct response" in {
    val unmarshaller = ClassSupport.classToFromResponseUnmarshaller(classOf[Team])
    val response = HttpClientJ.options("DummyService", system, "/view", unmarshaller)
    val result = Await.result(response, awaitMax)
    result should be(fullTeam)
  }

  "HttpClient with correct Endpoint calling options with marshaller and settings" should "get the correct response" in {
    val unmarshaller = ClassSupport.classToFromResponseUnmarshaller(classOf[Team])
    val response = HttpClientJ.options("DummyService", system, "/view", unmarshaller, RequestSettings())
    val result = Await.result(response, awaitMax)
    result should be(fullTeam)
  }

  "HttpClient with correct Endpoint calling raw.options and unmarshall object" should "get the correct response" in {
    //val response = HttpClientFactory.get("DummyService").raw.options("/view")
    val response = HttpClientJ.rawOptions("DummyService", system, "/view")
    val result = Await.result(response, awaitMax)
    import org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol.json4sUnmarshaller
    import org.squbs.httpclient.pipeline.HttpClientUnmarshal._
    result.unmarshalTo[Team] should be(Success(fullTeam))
  }

  "HttpClient with correct Endpoint calling raw.delete" should "get the correct response" in {
    //val response = HttpClientFactory.get("DummyService").raw.delete("/del/4")
    val response = HttpClientJ.rawDelete("DummyService", system, "/del/4")
    val result = Await.result(response, awaitMax)
    result.status should be(StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be(fullTeamWithDelJson)
  }

  "HttpClient with correct Endpoint calling raw.delete with setting" should "get the correct response" in {
    //val response = HttpClientFactory.get("DummyService").raw.delete("/del/4")
    val response = HttpClientJ.rawDelete("DummyService", system, "/del/4", RequestSettings())
    val result = Await.result(response, awaitMax)
    result.status should be(StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be(fullTeamWithDelJson)
  }

  "HttpClient with correct Endpoint calling raw.delete and unmarshall object" should "get the correct response" in {
    //val response = HttpClientFactory.get("DummyService").raw.delete("/del/4")
    val response = HttpClientJ.rawDelete("DummyService", system, "/del/4")
    val result = Await.result(response, awaitMax)
    import org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol.json4sUnmarshaller
    import org.squbs.httpclient.pipeline.HttpClientUnmarshal._
    result.unmarshalTo[Team] should be(Success(fullTeamWithDel))
  }

  "HttpClient with correct Endpoint calling delete" should "get the correct response" in {
    val response = HttpClientJ.delete("DummyService", system, "/del/4", classOf[Team])
    val result = Await.result(response, awaitMax)
    result should be(fullTeamWithDel)
  }

  "HttpClient with correct Endpoint calling delete with settings" should "get the correct response" in {
    val response = HttpClientJ.delete("DummyService", system, "/del/4", classOf[Team], RequestSettings())
    val result = Await.result(response, awaitMax)
    result should be(fullTeamWithDel)
  }

  "HttpClient with correct Endpoint calling delete with unmarshaller" should "get the correct response" in {
    val unmarshaller = ClassSupport.classToFromResponseUnmarshaller(classOf[Team])
    val response = HttpClientJ.delete("DummyService", system, "/del/4", unmarshaller)
    val result = Await.result(response, awaitMax)
    result should be(fullTeamWithDel)
  }

  "HttpClient with correct Endpoint calling delete with unmarshaller and settings" should "get the correct " +
    "response" in {
    val unmarshaller = ClassSupport.classToFromResponseUnmarshaller(classOf[Team])
    val response = HttpClientJ.delete("DummyService", system, "/del/4", unmarshaller, RequestSettings())
    val result = Await.result(response, awaitMax)
    result should be(fullTeamWithDel)
  }

  "HttpClient with correct Endpoint calling raw.post" should "get the correct response" in {
    val response = HttpClientJ.rawPost("DummyService", system, "/add", Some(newTeamMember).asJava)
    val result = Await.result(response, awaitMax)
    result.status should be(StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be(fullTeamWithAddJson)
  }

  "HttpClient with correct Endpoint calling raw.post with marshaller" should "get the correct response" in {
    import JsonProtocol.ClassSupport._
    val response = HttpClientJ.rawPost("DummyService", system, "/add", Some(newTeamMember).asJava, classOf[Employee])
    val result = Await.result(response, awaitMax)
    result.status should be(StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be(fullTeamWithAddJson)
  }

  "HttpClient with correct Endpoint calling raw.post and unmarshall object" should "get the correct response" in {
    val response = HttpClientJ.rawPost("DummyService", system, "/add", Some(newTeamMember).asJava)
    val result = Await.result(response, awaitMax)
    import org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol.json4sUnmarshaller
    import org.squbs.httpclient.pipeline.HttpClientUnmarshal._
    result.unmarshalTo[Team] should be(Success(fullTeamWithAdd))
  }

  "HttpClient with correct Endpoint calling raw.post and unmarshall object for java bean" should "get the correct " +
    "response" in {

    val response = HttpClientJ.rawPost("DummyService", system, "/addj", Some(newTeamMemberBean).asJava)
    val result = Await.result(response, awaitMax)

    import JsonProtocol.ManifestSupport.manifestToUnmarshaller
    import org.squbs.httpclient.pipeline.HttpClientUnmarshal._
    result.unmarshalTo[TeamBean] should be(Success(fullTeamBeanWithAdd))
  }

  "HttpClient with correct Endpoint calling post" should "get the correct response" in {
    val response = HttpClientJ.post("DummyService", system, "/add", Some(newTeamMember).asJava, classOf[Team])
    val result = Await.result(response, awaitMax)
    result should be(fullTeamWithAdd)
  }

  "HttpClient with correct Endpoint calling post with settings" should "get the correct response" in {
    //val response = HttpClientFactory.get("DummyService").post[Employee, Team]("/add", Some(newTeamMember))
    val response =
      HttpClientJ.post("DummyService", system, "/add", Some(newTeamMember).asJava, classOf[Team], RequestSettings())
    val result = Await.result(response, awaitMax)
    result should be(fullTeamWithAdd)
  }

  "HttpClient with correct Endpoint calling post for java bean" should "get the correct response" in {
    val response = HttpClientJ.post("DummyService", system, "/addj", Some(newTeamMemberBean).asJava, classOf[TeamBean])
    val result = Await.result(response, awaitMax)
    result should be(fullTeamBeanWithAdd)
  }

  "HttpClient with correct Endpoint calling post for java bean with explicit marshaller/unmarshaller" should "get " +
    "the correct response" in {
    import JsonProtocol.ClassSupport._
    val response = HttpClientJ.post("DummyService", system, "/addj", Some(newTeamMemberBean).asJava,
      classOf[EmployeeBean], classOf[TeamBean])
    val result = Await.result(response, awaitMax)
    result should be(fullTeamBeanWithAdd)
  }

  "HttpClient with correct Endpoint calling post for java bean with explicit marshaller/unmarshaller and " +
    "settings" should "get the correct response" in {
    import JsonProtocol.ClassSupport._
    val response = HttpClientJ.post("DummyService", system, "/addj", Some(newTeamMemberBean).asJava,
      classOf[EmployeeBean], classOf[TeamBean], RequestSettings())
    val result = Await.result(response, awaitMax)
    result should be(fullTeamBeanWithAdd)
  }

  "HttpClient with correct Endpoint calling raw.put" should "get the correct response" in {
    //val response = HttpClientFactory.get("DummyService").raw.put[Employee]("/add", Some(newTeamMember))
    val response = HttpClientJ.rawPut("DummyService", system, "/add", Some(newTeamMember).asJava)
    val result = Await.result(response, awaitMax)
    result.status should be(StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be(fullTeamWithAddJson)
  }

  "HttpClient with correct Endpoint calling raw.put with marshaller" should "get the correct response" in {
    import JsonProtocol.ClassSupport._
    val response = HttpClientJ.rawPut("DummyService", system, "/add", Some(newTeamMember).asJava, classOf[Employee])
    val result = Await.result(response, awaitMax)
    result.status should be(StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be(fullTeamWithAddJson)
  }

  "HttpClient with correct Endpoint calling raw.put and setting" should "get the correct response" in {
    //val response = HttpClientFactory.get("DummyService").raw.put[Employee]("/add", Some(newTeamMember))
    val response = HttpClientJ.rawPut("DummyService", system, "/add", Some(newTeamMember).asJava, RequestSettings())
    val result = Await.result(response, awaitMax)
    result.status should be(StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be(fullTeamWithAddJson)
  }

  "HttpClient with correct Endpoint calling raw.put with marshaller and setting" should "get the correct response" in {
    import JsonProtocol.ClassSupport._
    val response = HttpClientJ.rawPut("DummyService", system, "/add", Some(newTeamMember).asJava, RequestSettings(),
      classOf[Employee])
    val result = Await.result(response, awaitMax)
    result.status should be(StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be(fullTeamWithAddJson)
  }

  "HttpClient with correct Endpoint calling raw.put and unmarshall object" should "get the correct response" in {
    //val response = HttpClientFactory.get("DummyService").raw.put[Employee]("/add", Some(newTeamMember))
    val response = HttpClientJ.rawPut("DummyService", system, "/add", Some(newTeamMember).asJava)
    val result = Await.result(response, awaitMax)
    import org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol.json4sUnmarshaller
    import org.squbs.httpclient.pipeline.HttpClientUnmarshal._
    result.unmarshalTo[Team] should be(Success(fullTeamWithAdd))
  }

  "HttpClient with correct Endpoint calling put" should "get the correct response" in {
    val response = HttpClientJ.put("DummyService", system, "/add", Some(newTeamMember).asJava, classOf[Team])
    val result = Await.result(response, awaitMax)
    result should be(fullTeamWithAdd)
  }

  "HttpClient with correct Endpoint calling put with settings" should "get the correct response" in {
    val response = HttpClientJ.put("DummyService", system, "/add", Some(newTeamMember).asJava,
      classOf[Team], RequestSettings())
    val result = Await.result(response, awaitMax)
    result should be(fullTeamWithAdd)
  }

  "HttpClient with correct Endpoint calling put for java bean with explicit marshaller/unmarshaller" should "get " +
    "the correct response" in {
    import JsonProtocol.ClassSupport._
    val response = HttpClientJ.put("DummyService", system, "/addj", Some(newTeamMemberBean).asJava,
      classOf[EmployeeBean], classOf[TeamBean])
    val result = Await.result(response, awaitMax)
    result should be(fullTeamBeanWithAdd)
  }

  "HttpClient with correct Endpoint calling put for java bean with explicit marshaller/unmarshaller and " +
    "settings" should "get the correct response" in {
    import JsonProtocol.ClassSupport._
    val response = HttpClientJ.put("DummyService", system, "/addj", Some(newTeamMemberBean).asJava,
      classOf[EmployeeBean], classOf[TeamBean], RequestSettings())
    val result = Await.result(response, awaitMax)
    result should be(fullTeamBeanWithAdd)
  }

  "HttpClient could use endpoint as service name directly without registering endpoint resolvers for " +
    "third party service call" should "get the correct response" in {
    val response = HttpClientJ.rawGet(dummyServiceEndpoint.toString(), system, "/view")
    val result = Await.result(response, awaitMax)
    result.status should be(StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be(fullTeamJson)
  }

  "HttpClient update configuration" should "get the correct behaviour" in {
    val httpClient = HttpClientFactory.get("DummyService")
    val newConfig = Configuration(settings = Settings(hostSettings =
      Configuration.defaultHostSettings.copy(maxRetries = 11)))
    val updatedHttpClient = httpClient.withConfig(newConfig)
    Await.ready(updatedHttpClient.readyFuture, awaitMax)
    EndpointRegistry(system).resolve("DummyService") should be(Some(Endpoint(dummyServiceEndpoint)))
    val clientState = HttpClientManager(system).httpClientMap.get((httpClient.delegate.name, httpClient.delegate.env))
    clientState shouldBe defined
    clientState.value.endpoint should be (Endpoint(dummyServiceEndpoint, newConfig))
  }

  "HttpClient update settings" should "get the correct behaviour" in {
    val httpClient = HttpClientFactory.get("DummyService")
    val settings = Settings(hostSettings = Configuration.defaultHostSettings.copy(maxRetries = 20))
    val updatedHttpClient = httpClient.withSettings(settings)
    Await.ready(updatedHttpClient.readyFuture, awaitMax)
    EndpointRegistry(system).resolve("DummyService") should be(Some(Endpoint(dummyServiceEndpoint)))
    val clientState = HttpClientManager(system).httpClientMap.get((httpClient.delegate.name, httpClient.delegate.env))
    clientState.value.endpoint.config.settings should be (settings)
  }

  "HttpClient update pipeline" should "get the correct behaviour" in {
    val httpClient = HttpClientFactory.get("DummyService")
    val pipeline: Option[SimplePipelineConfig] = Some(DummyRequestPipeline)
    val pipelineSetting: Option[PipelineSetting] = pipeline
    val updatedHttpClient = httpClient.withPipeline(pipeline.asJava)
    Await.ready(updatedHttpClient.readyFuture, awaitMax)
    EndpointRegistry(system).resolve("DummyService") should be(Some(Endpoint(dummyServiceEndpoint)))
    val clientState = HttpClientManager(system).httpClientMap.get((httpClient.delegate.name, httpClient.delegate.env))
    clientState.value.endpoint.config.pipeline should be (pipelineSetting)
  }

  "HttpClient update pipeline setting" should "get the correct behaviour" in {
    val httpClient = HttpClientFactory.get("DummyService")
    val pipelineSetting: Option[PipelineSetting] = Some(DummyRequestPipeline)
    val updatedHttpClient = httpClient.withPipelineSetting(pipelineSetting.asJava)
    Await.ready(updatedHttpClient.readyFuture, awaitMax)
    EndpointRegistry(system).resolve("DummyService") should be(Some(Endpoint(dummyServiceEndpoint)))
    val clientState = HttpClientManager(system).httpClientMap.get((httpClient.delegate.name, httpClient.delegate.env))
    clientState.value.endpoint.config.pipeline should be (pipelineSetting)
  }

  "HttpClient update circuit breaker settings" should "actually set the circuit breaker settings" in {
    val httpClient = HttpClientFactory.get("DummyService")
    val cbSettings = CircuitBreakerSettings(callTimeout = 3 seconds)
    val updatedHttpClient = httpClient.withCircuitBreakerSettings(cbSettings)
    Await.ready(updatedHttpClient.readyFuture, awaitMax)
    EndpointRegistry(system).resolve("DummyService") should be(Some(Endpoint(dummyServiceEndpoint)))
    val clientState = HttpClientManager(system).httpClientMap.get((httpClient.delegate.name, httpClient.delegate.env))
    clientState.value.endpoint.config.settings.circuitBreakerConfig should be (cbSettings)
  }

  "HttpClient update fallback response" should "actually set the circuit breaker settings" in {
    val httpClient = HttpClientFactory.get("DummyService")
    val fallback = HttpResponse(entity = """{ "defaultResponse" : "Some default" }""")
    val updatedHttpClient = httpClient.withFallbackResponse(Optional.of(fallback))
    Await.ready(updatedHttpClient.readyFuture, awaitMax)
    EndpointRegistry(system).resolve("DummyService") should be(Some(Endpoint(dummyServiceEndpoint)))
    val clientState = HttpClientManager(system).httpClientMap.get((httpClient.delegate.name, httpClient.delegate.env))
    clientState.value.endpoint.config.settings.circuitBreakerConfig.fallbackHttpResponse should be (Option(fallback))
  }

  "HttpClient with the correct endpoint sleep 10s" should "restablish the connection and get response" in {
    Thread.sleep(10000)
    //val response: Future[HttpResponse] = HttpClientFactory.get("DummyService").raw.get("/view")
    val response = HttpClientJ.rawGet("DummyService", system, "/view")
    val result = Await.result(response, awaitMax)
    result.status should be(StatusCodes.OK)
    result.entity should not be empty
    result.entity.data should not be empty
    result.entity.data.asString should be(fullTeamJson)
  }

  "HttpClient with the correct endpoint and wrong unmarshal value" should "throw out PipelineException and failed" in {
    //val response: Future[HttpResponse] = HttpClientFactory.get("DummyService").raw.get("/view")
    val response = HttpClientJ.rawGet("DummyService", system, "/view")
    val result = Await.result(response, awaitMax)
    result.status should be(StatusCodes.OK)
    import org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol.json4sUnmarshaller
    import org.squbs.httpclient.pipeline.HttpClientUnmarshal._
    result.unmarshalTo[String] shouldBe 'failure
  }

  "HttpClient with correct endpoint calling raw.get with not existing uri and unmarshall value" should "throw " +
    "UnsuccessfulResponseException and failed" in {
    //val response: Future[HttpResponse] = HttpClientFactory.get("DummyService").raw.get("/notExisting")
    val response = HttpClientJ.rawGet("DummyService", system, "/notExisting")
    val result = Await.result(response, awaitMax)
    result.status should be(StatusCodes.NotFound)
    import org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol.json4sUnmarshaller
    import org.squbs.httpclient.pipeline.HttpClientUnmarshal._
    result.unmarshalTo[Team] shouldBe 'failure
  }

  "HttpClient with correct endpoint calling raw.get with not existing uri" should "get StatusCodes.NotFound" in {
    //val response: Future[HttpResponse] = HttpClientFactory.get("DummyService").raw.get("/notExisting")
    val response = HttpClientJ.rawGet("DummyService", system, "/notExisting")
    val result = Await.result(response, awaitMax)
    result.status should be(StatusCodes.NotFound)
  }

  "HttpClient with not existing endpoint" should "throw out HttpClientEndpointNotExistException" in {
    a[HttpClientEndpointNotExistException] should be thrownBy {
      //HttpClientFactory.get("NotExistingService").raw.get("/notExisting")
      HttpClientJ.rawGet("NotExistingService", system, "/notExisting")
    }
  }

  "HttpClient buildRequestUri" should "have the correct behaviour" in {
    HttpClientPathBuilder.buildRequestUri("/") should be("/")
    HttpClientPathBuilder.buildRequestUri("/abc") should be("/abc")
    HttpClientPathBuilder.buildRequestUri("/abc/") should be("/abc/")
    HttpClientPathBuilder.buildRequestUri("/abc/", Map("n1" -> "v1")) should be("/abc?n1=v1")
    val map1 = Map("d" -> 1.23d, "f" -> 2.3f, "l" -> List[String]("a", "b", "c"))
    HttpClientPathBuilder.buildRequestUri("/abc/", map1) should include("d=1.23")
    HttpClientPathBuilder.buildRequestUri("/abc/", map1) should include("f=2.3") //should be ("/abc?d=1.23&f=2.3")
    val map2 = Map("d" -> 1.23d, "f" -> 2.3f, "b" -> true, "c" -> 'a', "l" -> 12345L,
        "i" -> 100, "s" -> "Hello", "by" -> "1".toByte, "sh" -> "2".toShort)
    HttpClientPathBuilder.buildRequestUri("/abc/", map2) should include("s=Hello")
    HttpClientPathBuilder.buildRequestUri("/abc/", map2) should include("f=2.3")
    HttpClientPathBuilder.buildRequestUri("/abc/", map2) should include("i=100")
    HttpClientPathBuilder.buildRequestUri("/abc/", map2) should include("b=true")
    HttpClientPathBuilder.buildRequestUri("/abc/", map2) should include("by=1")
    HttpClientPathBuilder.buildRequestUri("/abc/", map2) should include("c=a")
    HttpClientPathBuilder.buildRequestUri("/abc/", map2) should include("d=1.23")
    HttpClientPathBuilder.buildRequestUri("/abc/", map2) should include("sh=2")
    HttpClientPathBuilder.buildRequestUri("/abc/", Map("n1" -> "v1", "n2" -> "v2")) should include("n1=v1")
    HttpClientPathBuilder.buildRequestUri("/abc/", Map("n1" -> "v1", "n2" -> "v2")) should not include "n1=v2"
    HttpClientPathBuilder.buildRequestUri("/abc/", Map("n1" -> "v1&", "n2" -> "v2%")) should include("v1%26")
    HttpClientPathBuilder.buildRequestUri("/abc/", Map("n1" -> "v1&", "n2" -> "v2%")) should include("n2=v2%25")
  }
}