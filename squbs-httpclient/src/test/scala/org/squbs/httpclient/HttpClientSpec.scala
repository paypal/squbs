/*
 *  Copyright 2017 PayPal
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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.client.RequestBuilding._
import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import org.apache.pekko.http.scaladsl.settings.ConnectionPoolSettings
import org.apache.pekko.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.testkit.TestKit
import org.json4s.{DefaultFormats, MappingException, jackson}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.OptionValues._
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.httpclient.dummy._
import org.squbs.marshallers.json.TestData._
import org.squbs.marshallers.json._
import org.squbs.resolver.ResolverRegistry
import org.squbs.testkit.Timeouts._

import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

class HttpClientSpec extends TestKit(ActorSystem("HttpClientSpec")) with AsyncFlatSpecLike
  with DummyService with Matchers with BeforeAndAfterAll {

  private [httpclient] val port = Await.result(startService, awaitMax)
  val baseUrl = s"http://localhost:$port"
  ResolverRegistry(system).register[HttpEndpoint](new DummyServiceResolver(baseUrl))
  private [httpclient] val clientFlow = ClientFlow[Int]("DummyService")

  def doRequest(request: HttpRequest): Future[Try[HttpResponse]] = {
    Source.single(request -> 42)
      .via(clientFlow)
      .runWith(Sink.head)
      .map { case (t, _) => t }
  }

  def doNonRegisteredRequest(request: HttpRequest): Future[Try[HttpResponse]] = {
    val clientFlow = ClientFlow[Int](baseUrl)
    Source.single(request -> 42)
      .via(clientFlow)
      .runWith(Sink.head)
      .map { case (t, _) => t }
  }

  override def afterAll(): Unit = {
    system.terminate()
  }

  "ClientFlow GET case class" should "get the correct response" in {
    for {
      tryResponse <- doRequest(Get("/view"))
      entity <- tryResponse.get.entity.toStrict(awaitMax)
    } yield {
      tryResponse shouldBe a [Success[_]]
      tryResponse.get.status shouldBe StatusCodes.OK
      entity.data should not be empty
      entity.data.utf8String shouldBe fullTeamJson
    }
  }

  "ClientFlow GET simple Scala class" should "get the correct response" in {
    for {
      tryResponse <- doRequest(Get("/view1"))
      entity <- tryResponse.get.entity.toStrict(awaitMax)
    } yield {
      tryResponse shouldBe a [Success[_]]
      tryResponse.get.status shouldBe StatusCodes.OK
      entity.data should not be empty
      entity.data.utf8String shouldBe fullTeamJson
    }
  }

  "ClientFlow GET from custom marshaller" should "get the correct response" in {
    for {
      tryResponse <- doRequest(Get("/view2"))
      entity <- tryResponse.get.entity.toStrict(awaitMax)
    } yield {
      tryResponse shouldBe a [Success[_]]
      tryResponse.get.status shouldBe StatusCodes.OK
      entity.data should not be empty
      entity.data.utf8String shouldBe fullTeamJson
    }
  }

  "ClientFlow GET java bean using case class" should "get the correct response" in {
    for {
      tryResponse <- doRequest(Get("/view3"))
      entity <- tryResponse.get.entity.toStrict(awaitMax)
    } yield {
      tryResponse shouldBe a [Success[_]]
      tryResponse.get.status shouldBe StatusCodes.OK
      entity.data should not be empty
      entity.data.utf8String shouldBe fullTeamJson
    }
  }


  "ClientFlow GET java bean" should "get the correct response" in {
    for {
      tryResponse <- doRequest(Get("/viewj"))
      entity <- tryResponse.get.entity.toStrict(awaitMax)
    } yield {
      tryResponse shouldBe a [Success[_]]
      tryResponse.get.status shouldBe StatusCodes.OK
      entity.data should not be empty
      entity.data.utf8String shouldBe fullTeamJson
    }
  }

  "ClientFlow GET passing raw header" should "get the correct response" in {
    for {
      tryResponse <- doRequest(Get("/view").addHeader(RawHeader("req1-name", "test123456")))
      entity <- tryResponse.get.entity.toStrict(awaitMax)
    } yield {
      tryResponse shouldBe a [Success[_]]
      val response = tryResponse.get
      response.status shouldBe StatusCodes.OK
      response.headers should contain (RawHeader("res-req1-name", "res-test123456"))
      entity.data should not be empty
      entity.data.utf8String shouldBe fullTeamJson
    }
  }

  "ClientFlow empty POST request correct Endpoint passing raw header" should "get the correct response" in {
    for {
      tryResponse <- doRequest(Post("/view").addHeader(RawHeader("req1-name", "test123456")))
      entity <- tryResponse.get.entity.toStrict(awaitMax)
    } yield {
      tryResponse shouldBe a [Success[_]]
      val response = tryResponse.get
      response.status shouldBe StatusCodes.OK
      response.headers should contain (RawHeader("res-req1-name", "res-test123456"))
      entity.data should not be empty
      entity.data.utf8String shouldBe fullTeamJson
    }
  }
//  TODO: This used to work. Do we need it?
//  "ClientFlow GET request with correct Endpoint" should "prepend slash to the uri" in {
//    for {
//      tryResponse <- doRequest(Get("view"))
//      entity <- tryResponse.get.entity.toStrict(awaitMax)
//    } yield {
//      tryResponse shouldBe a [Success[_]]
//      tryResponse.get.status shouldBe StatusCodes.OK
//      entity.data should not be empty
//      entity.data.utf8String shouldBe fullTeamJson
//    }
//  }

  "ClientFlow GET request with correct Endpoint" should "pass correct query" in {
    for {
      tryResponse <- doRequest(Get("/viewrange?range=new"))
      entity <- tryResponse.get.entity.toStrict(awaitMax)
    } yield {
      tryResponse shouldBe a [Success[_]]
      tryResponse.get.status shouldBe StatusCodes.OK
      entity.data should not be empty
      entity.data.utf8String shouldBe newTeamMemberJson
    }
  }

  "ClientFlow GET unmarshal" should "get the correct response" in {
    import com.github.pjfanning.pekkohttpjson4s.Json4sSupport._

    implicit val formats = DefaultFormats
    implicit val serialization = jackson.Serialization

    val responseFuture: Future[Team] =
      Source.single(Get("/view") -> 42)
        .via(clientFlow)
        .mapAsync(1) {
          case (Success(response), _) => Unmarshal(response).to[Team]
          case (Failure(e), _) => Future.failed(e)
        }
        .runWith(Sink.head)

    responseFuture map { _ shouldBe fullTeam}
  }

  /*"ClientFlow GET unmarshal object with JavaBean" should "get the correct response" in {
    import org.squbs.marshallers.json.XLangJsonSupport._
    for {
      tryResponse <- doRequest(Get("/viewj"))
      team <- Unmarshal(tryResponse.get).to[TeamWithPrivateMembers]
    } yield {
      tryResponse shouldBe a [Success[_]]
      tryResponse.get.status shouldBe StatusCodes.OK
      team shouldBe fullTeamWithPrivateMembers

      // TODO: Missing the following case, not sure still applicable with new API
      // import JsonProtocol.ClassSupport.classToFromResponseUnmarshaller
      // result.unmarshalTo(classOf[TeamWithPrivateMembers]) should be (Success(fullTeamBean))
    }
  }*/

  "ClientFlow GET unmarshal object to JavaBean with case class" should "get the correct response" in {
    import org.squbs.marshallers.json.XLangJsonSupport._
    for {
      tryResponse <- doRequest(Get("/view3"))
      team <- Unmarshal(tryResponse.get).to[TeamBeanWithCaseClassMember]
    } yield {
      tryResponse shouldBe a [Success[_]]
      tryResponse.get.status shouldBe StatusCodes.OK
      team shouldBe fullTeamWithCaseClassMember

      // TODO: Missing the following case, not sure still applicable with new API
      // import JsonProtocol.ClassSupport.classToFromResponseUnmarshaller
      // result.unmarshalTo(classOf[TeamBeanWithCaseClassMember]) should be (Success(fullTeam3))
    }
  }

  "ClientFlow GET unmarshal object to simple Scala class" should "get the correct response" in {
    import org.squbs.marshallers.json.XLangJsonSupport._
    for {
      tryResponse <- doRequest(Get("/view1"))
      team <- Unmarshal(tryResponse.get).to[TeamNonCaseClass]
    } yield {
      tryResponse shouldBe a [Success[_]]
      tryResponse.get.status shouldBe StatusCodes.OK
      team shouldBe fullTeamNonCaseClass

      // TODO: Missing the following case, not sure still applicable with new API
      // import JsonProtocol.ClassSupport.classToFromResponseUnmarshaller
      // import org.squbs.httpclient.pipeline.HttpClientUnmarshal._
      // result.unmarshalTo(classOf[Team1]) should be (Success(fullTeam1))
    }
  }

// TODO: I don't think we have auto-deserialize anywhere just yet.
// "HttpClient with correct Endpoint calling get" should "get the correct response" in {
//    import Json4sJacksonNoTypeHintsProtocol.json4sUnmarshaller
//    val response = HttpClientFactory.get("DummyService").get[Team]("/view")
//    val result = Await.result(response, awaitMax)
//    result should be (fullTeam)
//  }

  "HttpClient deserialization call resulting in NO_CONTENT" should "get the correct exception" in {
    import com.github.pjfanning.pekkohttpjson4s.Json4sSupport._

    implicit val formats = DefaultFormats
    implicit val serialization = jackson.Serialization

    val teamF =
      for {
        tryResponse <- doRequest(Get("/emptyresponse"))
      } yield {
        tryResponse shouldBe a [Success[_]]
        tryResponse.get.status shouldBe StatusCodes.NoContent
        Unmarshal(tryResponse.get).to[Team]
      }

    teamF map { _.value.value shouldBe Failure(Unmarshaller.NoContentException) }
  }

  "ClientFlow HEAD" should "get the correct response" in {
    for {
      tryResponse <- doRequest(Head("/view"))
      entity <- tryResponse.get.entity.toStrict(awaitMax)
    } yield {
      tryResponse shouldBe a [Success[_]]
      tryResponse.get.status shouldBe StatusCodes.OK
      entity.data shouldBe empty
    }
  }

  "ClientFlow OPTIONS" should "get the correct response" in {
    for {
      tryResponse <- doRequest(Options("/view"))
      entity <- tryResponse.get.entity.toStrict(awaitMax)
    } yield {
      tryResponse shouldBe a [Success[_]]
      tryResponse.get.status shouldBe StatusCodes.OK
      entity.data should not be empty
    }
  }

  "ClientFlow OPTIONS unmarshal" should "get the correct response" in {
    import com.github.pjfanning.pekkohttpjson4s.Json4sSupport._

    implicit val formats = DefaultFormats
    implicit val serialization = jackson.Serialization

    val responseFuture: Future[Team] =
      Source.single(Options("/view") -> 42)
        .via(clientFlow)
        .mapAsync(1) {
          case (Success(response), _) => Unmarshal(response).to[Team]
          case (Failure(e), _) => Future.failed(e)
        }
        .runWith(Sink.head)

    responseFuture map { _ shouldBe fullTeam }
  }

  "ClientFlow DELETE" should "get the correct response" in {
    for {
      tryResponse <- doRequest(Delete("/del/4"))
      entity <- tryResponse.get.entity.toStrict(awaitMax)
    } yield {
      tryResponse shouldBe a [Success[_]]
      tryResponse.get.status shouldBe StatusCodes.OK
      entity.data should not be empty
      entity.data.utf8String shouldBe fullTeamWithDelJson
    }
  }

  "ClientFlow DELETE unmarshal" should "get the correct response" in {
    import com.github.pjfanning.pekkohttpjson4s.Json4sSupport._

    implicit val formats = DefaultFormats
    implicit val serialization = jackson.Serialization

    val responseFuture: Future[Team] =
      Source.single(Delete("/del/4") -> 42)
        .via(clientFlow)
        .mapAsync(1) {
          case (Success(response), _) => Unmarshal(response).to[Team]
          case (Failure(e), _) => Future.failed(e)
        }
        .runWith(Sink.head)

    responseFuture map { _ shouldBe fullTeamWithDel }
  }

  "ClientFlow POST" should "get the correct response" in {
    import com.github.pjfanning.pekkohttpjson4s.Json4sSupport._

    implicit val formats = DefaultFormats
    implicit val serialization = jackson.Serialization

    for {
      tryResponse <- doRequest(Post("/add", newTeamMember))
      entity <- tryResponse.get.entity.toStrict(awaitMax)
    } yield {
      tryResponse shouldBe a [Success[_]]
      val response = tryResponse.get
      response.status shouldBe StatusCodes.OK
      entity.data should not be empty
      entity.data.utf8String shouldBe fullTeamWithAddJson
    }
  }

  "ClientFlow POST unmarshal" should "get the correct response" in {
    import com.github.pjfanning.pekkohttpjson4s.Json4sSupport._

    implicit val formats = DefaultFormats
    implicit val serialization = jackson.Serialization

    for {
      tryResponse <- doRequest(Post("/add", newTeamMember))
      result <- Unmarshal(tryResponse.get).to[Team]
    } yield {
      tryResponse shouldBe a [Success[_]]
      val response = tryResponse.get
      response.status shouldBe StatusCodes.OK
      result shouldBe fullTeamWithAdd
    }
  }

/*  "ClientFlow POST unmarshal JavaBean" should "get the correct response" in {
    import org.squbs.marshallers.json.XLangJsonSupport._
    for {
      tryResponse <- doRequest(Post("/addj", newTeamMemberBean))
      result <- Unmarshal(tryResponse.get).to[TeamWithPrivateMembers]
    } yield {
      tryResponse shouldBe a [Success[_]]
      val response = tryResponse.get
      response.status shouldBe StatusCodes.OK
      result shouldBe fullTeamPrivateMembersWithAdd
    }
  }*/

  "ClientFlow PUT" should "get the correct response" in {
    import com.github.pjfanning.pekkohttpjson4s.Json4sSupport._

    implicit val formats = DefaultFormats
    implicit val serialization = jackson.Serialization

    for {
      tryResponse <- doRequest(Put("/add", newTeamMember))
      entity <- tryResponse.get.entity.toStrict(awaitMax)
    } yield {
      tryResponse shouldBe a [Success[_]]
      val response = tryResponse.get
      response.status shouldBe StatusCodes.OK
      entity.data should not be empty
      entity.data.utf8String shouldBe fullTeamWithAddJson
    }
  }

  "ClientFlow PUT unmarshal" should "get the correct response" in {
    import com.github.pjfanning.pekkohttpjson4s.Json4sSupport._

    implicit val formats = DefaultFormats
    implicit val serialization = jackson.Serialization

    for {
      tryResponse <- doRequest(Put("/add", newTeamMember))
      result <- Unmarshal(tryResponse.get).to[Team]
    } yield {
      tryResponse shouldBe a [Success[_]]
      val response = tryResponse.get
      response.status shouldBe StatusCodes.OK
      result shouldBe fullTeamWithAdd
    }
  }

//  TODO: This is a bug. We should be able to use the URL directly.
//  "ClientFlow" should "use endpoint without registering" in {
//    for {
//      tryResponse <- doNonregisteredRequest(Get("/view"))
//      entity <- tryResponse.get.entity.toStrict(awaitMax)
//    } yield {
//      tryResponse shouldBe a [Success[_]]
//      tryResponse.get.status shouldBe StatusCodes.OK
//      entity.data should not be empty
//      entity.data.utf8String shouldBe fullTeamJson
//    }
//  }

  "ClientFlow GET wrong unmarshal" should "give a failed future" in {
    import com.github.pjfanning.pekkohttpjson4s.Json4sSupport._

    implicit val formats = DefaultFormats
    implicit val serialization = jackson.Serialization

    val stringF =
      for {
        tryResponse <- doRequest(Get("/view"))
      } yield {
        tryResponse shouldBe a [Success[_]]
        val response = tryResponse.get
        response.status shouldBe StatusCodes.OK
        Unmarshal(tryResponse.get).to[String]
      }

    stringF map { future =>
      val tryFailed = future.value.value
      tryFailed shouldBe a [Failure[_]]
      tryFailed.failed.get shouldBe a [MappingException]
    }
  }

  "ClientFlow GET non-existing resource and unmarshal" should "give a failed future" in {
    import com.github.pjfanning.pekkohttpjson4s.Json4sSupport._

    implicit val formats = DefaultFormats
    implicit val serialization = jackson.Serialization

    val teamF =
      for {
        tryResponse <- doRequest(Get("/notExisting"))
      } yield {
        tryResponse shouldBe a [Success[_]]
        val response = tryResponse.get
        response.status shouldBe StatusCodes.NotFound
        Unmarshal(tryResponse.get).to[Team]
      }

    teamF map { future =>
      val tryFailed = future.value.value
      tryFailed shouldBe a [Failure[_]]
      tryFailed.failed.get shouldBe a [Unmarshaller.UnsupportedContentTypeException]
    }
  }

  "ClientFlow of non-existing endpoint" should "give the right failure" in {
    a [HttpClientEndpointNotExistException] should be thrownBy ClientFlow[Int]("NotExistingService")
  }

  "ClientFlow with config" should "get the correct behavior" in {
    val overrides = "pekko.http.host-connection-pool.max-retries = 11"
    val settings = ConnectionPoolSettings(overrides)
    val clientFlow = ClientFlow[Int]("DummyService", settings = Some(settings))
    settings.maxRetries shouldBe 11
    // TODO: I have no way to know the settings in the client flow. Need access to JMX bean.
  }
  // ORIGINAL CODE FOR THIS VALIDATION
//    "HttpClient update configuration" should "get the correct behaviour" in {
//      val httpClient = HttpClientFactory.get("DummyService")
//      val newConfig = Configuration(settings = Settings(hostSettings =
//        Configuration.defaultHostSettings.copy(maxRetries = 11)))
//      val updatedHttpClient = httpClient.withConfig(newConfig)
//      Await.ready(updatedHttpClient.readyFuture, awaitMax)
//      EndpointRegistry(system).resolve("DummyService") should be (Some(Endpoint(dummyServiceEndpoint, Configuration()(system))))
//      val clientState = HttpClientManager(system).httpClientMap.get((httpClient.name, httpClient.env))
//      clientState.value.endpoint should be (Endpoint(dummyServiceEndpoint, newConfig))
//    }

/*

  TODO: This should be covered in the ClientFlowPipelineSpec
    "HttpClient update pipeline" should "get the correct behaviour" in {
      import Configuration._
      val httpClient = HttpClientFactory.get("DummyService")
      val pipeline = Some(DummyRequestPipeline)
      val pipelineSetting : Option[PipelineSetting] = pipeline
      val updatedHttpClient = httpClient.withPipeline(pipeline)
      Await.ready(updatedHttpClient.readyFuture, awaitMax)
      EndpointRegistry(system).resolve("DummyService") should be (Some(Endpoint(dummyServiceEndpoint)))
      val clientState = HttpClientManager(system).httpClientMap.get((httpClient.name, httpClient.env))
      clientState.value.endpoint.config.pipeline should be (pipelineSetting)
    }

  TODO: This should be covered in the ClientFlowPipelineSpec
    "HttpClient update pipeline setting" should "get the correct behaviour" in {
      import Configuration._
      val httpClient = HttpClientFactory.get("DummyService")
      val pipelineSetting : Option[PipelineSetting] = Some(DummyRequestPipeline)
      val updatedHttpClient = httpClient.withPipelineSetting(pipelineSetting)
      Await.ready(updatedHttpClient.readyFuture, awaitMax)
      EndpointRegistry(system).resolve("DummyService") should be (Some(Endpoint(dummyServiceEndpoint)))
      val clientState = HttpClientManager(system).httpClientMap.get((httpClient.name, httpClient.env))
      clientState.value.endpoint.config.pipeline should be (pipelineSetting)
    }

 TODO: Circuit Breaker Tests to be handled separately
    "HttpClient update circuit breaker settings" should "actually set the circuit breaker settings" in {
      val httpClient = HttpClientFactory.get("DummyService")
      val cbSettings = CircuitBreakerSettings(callTimeout = 3 seconds)
      val updatedHttpClient = httpClient.withCircuitBreakerSettings(cbSettings)
      Await.ready(updatedHttpClient.readyFuture, awaitMax)
      EndpointRegistry(system).resolve("DummyService") should be (Some(Endpoint(dummyServiceEndpoint)))
      val clientState = HttpClientManager(system).httpClientMap.get((httpClient.name, httpClient.env))
      clientState.value.endpoint.config.settings.circuitBreakerConfig should be (cbSettings)
    }

    "HttpClient update fallback response" should "actually set the circuit breaker settings" in {
      val httpClient = HttpClientFactory.get("DummyService")
      val fallback = HttpResponse(entity = """{ "defaultResponse" : "Some default" }""")
      val updatedHttpClient = httpClient.withFallbackResponse(Some(fallback))
      Await.ready(updatedHttpClient.readyFuture, awaitMax)
      EndpointRegistry(system).resolve("DummyService") should be (Some(Endpoint(dummyServiceEndpoint)))
      val clientState = HttpClientManager(system).httpClientMap.get((httpClient.name, httpClient.env))
      clientState.value.endpoint.config.settings.circuitBreakerConfig.fallbackHttpResponse should be (Some(fallback))
    }

    "HttpClient with the correct endpoint sleep 10s" should "re-establish the connection and get response" in {
      Thread.sleep(10000)
      val response: Future[HttpResponse] = HttpClientFactory.get("DummyService").raw.get("/view")
      val result = Await.result(response, awaitMax)
      result.status should be (StatusCodes.OK)
      result.entity should not be empty
      result.entity.data should not be empty
      result.entity.data.asString should be (fullTeamJson)
    }


    TODO: Do we need this one back?
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

    "HttpClient with fallback HttpResponse" should "get correct fallback logic" in {
      val fallbackHttpResponse = HttpResponse()
      val httpClient = HttpClientFactory.get("DummyService").withFallback(Some(fallbackHttpResponse))
      httpClient.endpoint.config.circuitBreakerConfig.fallbackHttpResponse should be (Some(fallbackHttpResponse))
    }

    TODO: Do we need markup/markdown?
    "MarkDown/MarkUp HttpClient" should "have the correct behaviour" in {
      implicit val ec = system.dispatcher
      val httpClient = HttpClientFactory.get("DummyService")
      Await.ready(httpClient.markDown, awaitMax)
      val response = httpClient.raw.get("/view")
      val thrown = the [HttpClientMarkDownException] thrownBy Await.result(response, awaitMax)
      thrown.getMessage shouldBe "HttpClient:(DummyService,Default) has been marked down!"

      Await.ready(httpClient.markUp, awaitMax)
      val updatedResponse = httpClient.raw.get("/view")
      val updatedResult = Await.result(updatedResponse, awaitMax)
      updatedResult.status should be (StatusCodes.OK)
      updatedResult.entity should not be empty
      updatedResult.entity.data should not be empty
      updatedResult.entity.data.asString should be (fullTeamJson)
    }
    */
}
