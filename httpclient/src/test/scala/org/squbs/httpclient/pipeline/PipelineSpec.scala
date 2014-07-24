package org.squbs.httpclient.pipeline

import org.scalatest.{Ignore, BeforeAndAfterAll, Matchers, FlatSpec}
import org.squbs.httpclient.{HttpClientFactory}
import akka.actor.ActorSystem
import akka.io.IO
import spray.can.Http
import scala.concurrent.duration._
import akka.pattern._
import spray.util._
import spray.http._
import org.squbs.httpclient.env.{EnvironmentRegistry}
import org.squbs.httpclient.dummy._
import spray.http.HttpRequest
import spray.http.HttpHeaders.RawHeader
import scala.Some
import org.squbs.httpclient.endpoint.EndpointRegistry

@Ignore
class PipelineSpec extends FlatSpec with DummyService with Matchers with BeforeAndAfterAll with PipelineManager{

  implicit val system = ActorSystem("PipelineSpec")
  import system.dispatcher
  import org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol._

  override def beforeAll = {
    EndpointRegistry.register(DummyServiceEndpointResolver)
    startDummyService(system)
//    DummyServiceMain.main(Array.empty)
  }

  override def afterAll = {
    HttpClientFactory.getOrCreate("DummyService").post[String]("/stop", Some(""))
    HttpClientFactory.httpClientMap.clear
    EnvironmentRegistry.environmentResolvers.clear
    EndpointRegistry.endpointResolvers.clear
    IO(Http).ask(Http.CloseAll)(3.second).await
    system.shutdown()
  }

  "Request Pipeline (invokeToHttpResponse)" should "have the correct behaviour" in {
    val httpClient = HttpClientFactory.getOrCreate("DummyService", pipeline = Some(DummyRequestPipeline))
    val sendReceive = invokeToHttpResponse(httpClient)
    sendReceive.isSuccess should be (true)
    val request = HttpRequest(uri = Uri("http://localhost:9999/view"))
    val response = sendReceive.get(request).await
    response.status should be (StatusCodes.OK)
    response.content.get.headers contains (RawHeader("res-req1-name", "res-req1-value"))
  }

  "Response Pipeline (invokeToHttpResponse)" should "have the correct behaviour" in {
    val httpClient = HttpClientFactory.getOrCreate("DummyService", pipeline = Some(DummyResponsePipeline))
    val sendReceive = invokeToHttpResponse(httpClient)
    sendReceive.isSuccess should be (true)
    val request = HttpRequest(uri = Uri("http://localhost:9999/view"))
    val response = sendReceive.get(request).await
    response.status should be (StatusCodes.OK)
    response.content.get.headers contains (RawHeader("res1-name", "res1-value"))
  }

  "Request-Response Pipeline (invokeToHttpResponse)" should "have the correct behaviour" in {
    val httpClient = HttpClientFactory.getOrCreate("DummyService", pipeline = Some(DummyRequestResponsePipeline))
    val sendReceive = invokeToHttpResponse(httpClient)
    sendReceive.isSuccess should be (true)
    val request = HttpRequest(uri = Uri("http://localhost:9999/view"))
    val response = sendReceive.get(request).await
    response.status should be (StatusCodes.OK)
    response.content.get.headers contains (RawHeader("res-req2-name", "res-req2-value"))
    response.content.get.headers contains (RawHeader("res2-name", "res2-value"))
  }

  "Request Pipeline (invokeToEntity)" should "have the correct behaviour" in {
    val httpClient = HttpClientFactory.getOrCreate("DummyService", pipeline = Some(DummyRequestPipeline))
    val sendReceive = invokeToEntity[Team](httpClient)
    sendReceive.isSuccess should be (true)
    val request = HttpRequest(uri = Uri("http://localhost:9999/view"))
    val response = sendReceive.get(request).await
    response.status should be (StatusCodes.OK)
    response.rawHttpResponse.get.headers contains (RawHeader("res-req1-name", "res-req1-value"))
  }

  "Response Pipeline (invokeToEntity)" should "have the correct behaviour" in {
    val httpClient = HttpClientFactory.getOrCreate("DummyService", pipeline = Some(DummyResponsePipeline))
    val sendReceive = invokeToEntity[Team](httpClient)
    sendReceive.isSuccess should be (true)
    val request = HttpRequest(uri = Uri("http://localhost:9999/view"))
    val response = sendReceive.get(request).await
    response.status should be (StatusCodes.OK)
    response.rawHttpResponse.get.headers contains (RawHeader("res1-name", "res1-value"))
  }

  "Request-Response Pipeline (invokeToEntity)" should "have the correct behaviour" in {
    val httpClient = HttpClientFactory.getOrCreate("DummyService", pipeline = Some(DummyRequestResponsePipeline))
    val sendReceive = invokeToEntity[Team](httpClient)
    sendReceive.isSuccess should be (true)
    val request = HttpRequest(uri = Uri("http://localhost:9999/view"))
    val response = sendReceive.get(request).await
    response.status should be (StatusCodes.OK)
    response.rawHttpResponse.get.headers contains (RawHeader("res-req2-name", "res-req2-value"))
    response.rawHttpResponse.get.headers contains (RawHeader("res2-name", "res2-value"))
  }
}