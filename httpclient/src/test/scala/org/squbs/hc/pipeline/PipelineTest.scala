package org.squbs.hc.pipeline

import org.scalatest.{BeforeAndAfterAll, Matchers, FlatSpec}
import spray.client.pipelining._
import org.squbs.hc.HttpClient
import org.squbs.hc.routing.{RoutingRegistry, RoutingDefinition}
import akka.actor.ActorSystem
import akka.io.IO
import spray.can.Http
import scala.concurrent.duration._
import akka.pattern._
import spray.util._
import spray.http._
import spray.http.HttpRequest
import org.squbs.hc.pipeline.impl.RequestAddHeaderHandler
import org.squbs.hc.pipeline.impl.ResponseAddHeaderHandler
import spray.http.HttpHeaders.RawHeader
import scala.Some

class PipelineTest extends FlatSpec with Matchers with BeforeAndAfterAll{

  private implicit val system = ActorSystem("PipelineTest")
  import system.dispatcher
  import org.squbs.hc.json.Json4sJacksonNoTypeHintsProtocol._

  override def beforeAll() {
    HttpClient.clear
    RoutingRegistry.clear
    RoutingRegistry.register(new GoogleRoutingDefinition())
  }

  override def afterAll() {
    IO(Http).ask(Http.CloseAll)(1.second).await
    system.shutdown()
  }

  "Request Pipeline (invokeToHttpResponse)" should "have the correct behaviour" in {
    val httpClient = HttpClient.create("googlemap", pipeline = Some(RequestPipeline))
    val sendReceive = PipelineManager.invokeToHttpResponse(httpClient)
    sendReceive.isSuccess should be (true)
    val reqeust = HttpRequest(uri = Uri("http://maps.googleapis.com/maps/api/elevation/json?locations=27.988056,86.925278&sensor=false"))
    val response = sendReceive.get(reqeust).await
    response.status should be (StatusCodes.OK)
  }

  "Response Pipeline (invokeToHttpResponse)" should "have the correct behaviour" in {
    val httpClient = HttpClient.create("googlemap", pipeline = Some(ResponsePipeline))
    val sendReceive = PipelineManager.invokeToHttpResponse(httpClient)
    sendReceive.isSuccess should be (true)
    val reqeust = HttpRequest(uri = Uri("http://maps.googleapis.com/maps/api/elevation/json?locations=27.988056,86.925278&sensor=false"))
    val response = sendReceive.get(reqeust).await
    response.status should be (StatusCodes.OK)
    response.content.get.headers contains (RawHeader("response-name", "response-value"))
  }

  "Request-Response Pipeline (invokeToHttpResponse)" should "have the correct behaviour" in {
    val httpClient = HttpClient.create("googlemap", pipeline = Some(RequestResponsePipeline))
    val sendReceive = PipelineManager.invokeToHttpResponse(httpClient)
    sendReceive.isSuccess should be (true)
    val reqeust = HttpRequest(uri = Uri("http://maps.googleapis.com/maps/api/elevation/json?locations=27.988056,86.925278&sensor=false"))
    val response = sendReceive.get(reqeust).await
    response.status should be (StatusCodes.OK)
    response.content.get.headers contains (RawHeader("response1-name", "response1-value"))
  }

  "Request Pipeline (invokeToEntity)" should "have the correct behaviour" in {
    val httpClient = HttpClient.create("googlemap", pipeline = Some(RequestPipeline))
    val sendReceive = PipelineManager.invokeToEntity[GoogleApiResult[Elevation]](httpClient)
    sendReceive.isSuccess should be (true)
    val reqeust = HttpRequest(uri = Uri("http://maps.googleapis.com/maps/api/elevation/json?locations=27.988056,86.925278&sensor=false"))
    val response = sendReceive.get(reqeust).await
    response.status should be (StatusCodes.OK)
  }

  "Response Pipeline (invokeToEntity)" should "have the correct behaviour" in {
    val httpClient = HttpClient.create("googlemap", pipeline = Some(ResponsePipeline))
    val sendReceive = PipelineManager.invokeToEntity[GoogleApiResult[Elevation]](httpClient)
    sendReceive.isSuccess should be (true)
    val reqeust = HttpRequest(uri = Uri("http://maps.googleapis.com/maps/api/elevation/json?locations=27.988056,86.925278&sensor=false"))
    val response = sendReceive.get(reqeust).await
    response.status should be (StatusCodes.OK)
    response.rawHttpResponse.get.headers contains (RawHeader("response-name", "response-value"))
  }

  "Request-Response Pipeline (invokeToEntity)" should "have the correct behaviour" in {
    val httpClient = HttpClient.create("googlemap", pipeline = Some(RequestResponsePipeline))
    val sendReceive = PipelineManager.invokeToEntity[GoogleApiResult[Elevation]](httpClient)
    sendReceive.isSuccess should be (true)
    val reqeust = HttpRequest(uri = Uri("http://maps.googleapis.com/maps/api/elevation/json?locations=27.988056,86.925278&sensor=false"))
    val response = sendReceive.get(reqeust).await
    response.status should be (StatusCodes.OK)
    response.rawHttpResponse.get.headers contains (RawHeader("response1-name", "response1-value"))
  }
}

object RequestPipeline extends PipelineDefinition {
  override def requestPipelines: Seq[RequestTransformer] = Seq[RequestTransformer](RequestAddHeaderHandler(RawHeader("request-name", "request-value")).processRequest)
  override def responsePipelines: Seq[ResponseTransformer] = Seq.empty[ResponseTransformer]
}

object ResponsePipeline extends PipelineDefinition {
  override def requestPipelines: Seq[RequestTransformer] = Seq.empty[RequestTransformer]
  override def responsePipelines: Seq[ResponseTransformer] = Seq[ResponseTransformer](ResponseAddHeaderHandler(RawHeader("response-name", "response-value")).processResponse)
}

object RequestResponsePipeline extends PipelineDefinition {
  override def requestPipelines: Seq[RequestTransformer] = Seq[RequestTransformer](RequestAddHeaderHandler(RawHeader("request1-name", "request1-value")).processRequest)
  override def responsePipelines: Seq[ResponseTransformer] = Seq[ResponseTransformer](ResponseAddHeaderHandler(RawHeader("response1-name", "response1-value")).processResponse)
}

class GoogleRoutingDefinition extends RoutingDefinition {
  override def resolve(svcName: String, env: Option[String]): Option[String] = {
    if (svcName == name)
      Some("http://maps.googleapis.com/maps")
    else
      None
  }

  override def name: String = "googlemap"
}

case class Elevation(location: Location, elevation: Double)
case class Location(lat: Double, lng: Double)
case class GoogleApiResult[T](status: String, results: List[T])