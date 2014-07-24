package org.squbs.httpclient

import org.scalatest.{FlatSpec, Matchers, BeforeAndAfterAll}
import akka.actor.ActorSystem
import org.squbs.httpclient.endpoint.{EndpointRegistry}
import org.squbs.httpclient.dummy._
import org.squbs.httpclient.env.EnvironmentRegistry
import akka.io.IO
import spray.can.Http
import akka.pattern._
import scala.concurrent.duration._
import spray.util._
import scala.concurrent.Await
import spray.http.StatusCodes
import org.squbs.httpclient.config.Configuration
import org.squbs.httpclient.endpoint.Endpoint
import org.squbs.httpclient.dummy.Employee
import scala.Some
import spray.httpx.PipelineException

/**
 * Created by hakuang on 7/22/2014.
 */
class HttpClientSpec extends FlatSpec with DummyService with Matchers with BeforeAndAfterAll{

  implicit val system = ActorSystem("HttpClientSpec")
  import system.dispatcher
  import org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol._


  override def beforeAll {
    EndpointRegistry.register(DummyServiceEndpointResolver)
    startDummyService(system)
  }

  override def afterAll {
//    HttpClientFactory.getOrCreate("DummyService").post[String]("/stop", Some(""))
    EndpointRegistry.endpointResolvers.clear
    EnvironmentRegistry.environmentResolvers.clear
    HttpClientFactory.httpClientMap.clear
    IO(Http).ask(Http.CloseAll)(30.second).await
    system.shutdown()
  }

  "HttpClient with correct Endpoint calling get" should "get the correct response" in {
    val response = HttpClientFactory.getOrCreate("DummyService").get("/view")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.content.get.entity.nonEmpty should be (true)
    result.content.get.entity.data.nonEmpty should be (true)
    result.content.get.entity.data.asString should be (fullTeamJson)
  }

  "HttpClient with correct Endpoint calling getEntity" should "get the correct response" in {
    val response = HttpClientFactory.getOrCreate("DummyService").getEntity[Team]("/view")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.content should be (Right(fullTeam))
    result.rawHttpResponse.get.entity.data.nonEmpty should be (true)
  }

  "HttpClient with correct Endpoint calling head" should "get the correct response" in {
    val response = HttpClientFactory.getOrCreate("DummyService").head("/view")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.content.get.entity.nonEmpty should be (false)
  }

  "HttpClient with correct Endpoint calling headEntity" should "get the correct response" in {
    a[PipelineException] should be thrownBy {
      val response = HttpClientFactory.getOrCreate("DummyService").headEntity[Team]("/view")
      Await.result(response, 3 seconds)
    }
  }

  "HttpClient with correct Endpoint calling options" should "get the correct response" in {
    val response = HttpClientFactory.getOrCreate("DummyService").options("/view")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.content.get.entity.nonEmpty should be (true)
    result.content.get.entity.data.nonEmpty should be (true)
  }

  "HttpClient with correct Endpoint calling optionsEntity" should "get the correct response" in {
    val response = HttpClientFactory.getOrCreate("DummyService").optionsEntity[Team]("/view")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.content should be (Right(fullTeam))
    result.rawHttpResponse.get.entity.data.nonEmpty should be (true)
  }

  "HttpClient with correct Endpoint calling delete" should "get the correct response" in {
    val response = HttpClientFactory.getOrCreate("DummyService").delete("/del/4")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.content.get.entity.nonEmpty should be (true)
    result.content.get.entity.data.nonEmpty should be (true)
    result.content.get.entity.data.asString should be (fullTeamWithDelJson)
  }

  "HttpClient with correct Endpoint calling deleteEntity" should "get the correct response" in {
    val response = HttpClientFactory.getOrCreate("DummyService").deleteEntity[Team]("/del/4")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.content should be (Right(fullTeamWithDel))
    result.rawHttpResponse.get.entity.data.nonEmpty should be (true)
  }

  "HttpClient with correct Endpoint calling post" should "get the correct response" in {
    val response = HttpClientFactory.getOrCreate("DummyService").post[Employee]("/add", Some(newTeamMember))
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.content.get.entity.nonEmpty should be (true)
    result.content.get.entity.data.nonEmpty should be (true)
    result.content.get.entity.data.asString should be (fullTeamWithAddJson)
  }

  "HttpClient with correct Endpoint calling postEnitty" should "get the correct response" in {
    val response = HttpClientFactory.getOrCreate("DummyService").postEntity[Employee, Team]("/add", Some(newTeamMember))
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.status should be (StatusCodes.OK)
    result.content should be (Right(fullTeamWithAdd))
    result.rawHttpResponse.get.entity.data.nonEmpty should be (true)
  }

  "HttpClient with correct Endpoint calling put" should "get the correct response" in {
    val response = HttpClientFactory.getOrCreate("DummyService").put[Employee]("/add", Some(newTeamMember))
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.content.get.entity.nonEmpty should be (true)
    result.content.get.entity.data.nonEmpty should be (true)
    result.content.get.entity.data.asString should be (fullTeamWithAddJson)
  }

  "HttpClient with correct Endpoint calling putEnitty" should "get the correct response" in {
    val response = HttpClientFactory.getOrCreate("DummyService").putEntity[Employee, Team]("/add", Some(newTeamMember))
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.status should be (StatusCodes.OK)
    result.content should be (Right(fullTeamWithAdd))
    result.rawHttpResponse.get.entity.data.nonEmpty should be (true)
  }

  "HttpClient could be use endpoint as service name directly without registry endpoint resolvers, major target for third party service call" should "get the correct response" in {
    val response = HttpClientFactory.getOrCreate("http://localhost:9999").get("/view")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.content.get.entity.nonEmpty should be (true)
    result.content.get.entity.data.nonEmpty should be (true)
    result.content.get.entity.data.asString should be (fullTeamJson)
  }

  "HttpClient update configuration" should "get the correct behaviour" in {
    val httpClient = HttpClientFactory.getOrCreate("DummyService")
    val newConfig = Configuration(hostSettings = Configuration.defaultHostSettings.copy(maxRetries = 11))
    httpClient.updateConfig(newConfig)
    EndpointRegistry.resolve("DummyService") should be (Some(Endpoint("http://localhost:9999", newConfig)))
    httpClient.updateConfig(Configuration())
    EndpointRegistry.resolve("DummyService") should be (Some(Endpoint("http://localhost:9999")))
  }

  "HttpClient update pipeline" should "get the correct behaviour" in {
    val httpClient = HttpClientFactory.getOrCreate("DummyService")
    httpClient.updatePipeline(Some(DummyRequestPipeline)).pipeline should be (Some(DummyRequestPipeline))
    httpClient.updatePipeline(None).pipeline should be (None)
  }

  "HttpClient with the correct endpoint sleep 10s" should "restablish the connection and get response" in {
    Thread.sleep(10000)
    val response = HttpClientFactory.getOrCreate("DummyService").get("/view")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.content.get.entity.nonEmpty should be (true)
    result.content.get.entity.data.nonEmpty should be (true)
    result.content.get.entity.data.asString should be (fullTeamJson)
  }

  "HttpClient with correct endpoint calling get with not existing uri" should "get StatusCodes.NotFound" in {
    val response = HttpClientFactory.getOrCreate("DummyService").get("/notExisting")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.NotFound)
  }

  "HttpClient with not existing endpoint" should "get StatusCodes.NotFound" in {
    val response = HttpClientFactory.getOrCreate("NotExistingService").get("/notExisting")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.NotFound)
  }

  "MarkDown/MarkUp HttpClient" should "have the correct behaviour" in {
    val httpClient = HttpClientFactory.getOrCreate("DummyService")
    httpClient.markDown
    val response = httpClient.get("/view")
    val result = Await.result(response, 3 seconds)
    result.status should be (HttpClientException.httpClientMarkDownError)
    result.content.isLeft should be (true)
    result.content should be (Left(HttpClientMarkDownException("DummyService")))
    httpClient.markUp
    val updatedResponse = httpClient.get("/view")
    val updatedResult = Await.result(updatedResponse, 3 seconds)
    updatedResult.status should be (StatusCodes.OK)
    updatedResult.content.get.entity.nonEmpty should be (true)
    updatedResult.content.get.entity.data.nonEmpty should be (true)
    updatedResult.content.get.entity.data.asString should be (fullTeamJson)
  }
}
