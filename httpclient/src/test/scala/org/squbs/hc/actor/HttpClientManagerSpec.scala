package org.squbs.hc.actor

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{ActorRef, ActorSystem}
import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll, Matchers, FlatSpecLike}
import org.squbs.hc.actor.HttpClientMessage._
import scala.collection.concurrent.TrieMap
import org.squbs.hc._
import org.squbs.hc.routing.{RoutingRegistry, RoutingDefinition}
import akka.pattern._
import scala.concurrent.duration._
import spray.util._
import org.squbs.hc.actor.HttpClientMessage.CreateHttpClientSuccessMsg
import org.squbs.hc.actor.HttpClientMessage.CreateHttpClientMsg
import org.squbs.hc.actor.HttpClientMessage.GetAllHttpClientSuccessMsg
import org.squbs.hc.actor.HttpClientMessage.CreateHttpClientFailureMsg
import org.squbs.hc.HttpClientExistException
import scala.Some
import org.squbs.hc.actor.HttpClientMessage.UpdateHttpClientFailureMsg
import org.squbs.hc.actor.HttpClientMessage.UpdateHttpClientMsg
import org.squbs.hc.actor.HttpClientMessage.UpdateHttpClientSuccessMsg
import spray.http.{StatusCodes, HttpMethods}
import akka.io.IO
import spray.can.Http

/**
 * Created by hakuang on 6/26/2014.
 */
class HttpClientManagerSpec extends TestKit(ActorSystem("HttpClientManagerSpec")) with FlatSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll with BeforeAndAfterEach{

  override def afterAll = {
    IO(Http).ask(Http.CloseAll)(1.second).await
    system.shutdown
  }

  override def afterEach = {
    RoutingRegistry.routingDefinitions.clear
    HttpClientManager.httpClientMap.clear
    HttpClientFactory.httpClientMap.clear
//    val httpClientManager = HttpClientManager(system).httpClientManager
//    implicit val timeout: Timeout = Timeout(2 seconds)
//    httpClientManager ? DeleteAllHttpClientMsg
  }

  "httpClientMap" should "be emtpy before creating any httpclient" in {
    val httpClientManager = HttpClientManager(system).httpClientManager
    httpClientManager ! GetAllHttpClientMsg
    type HttpClientMap[String, IHttpClient] = TrieMap[String, IHttpClient]
    expectMsgType[GetAllHttpClientSuccessMsg[HttpClientMap]].map.isEmpty should be (true)
  }

  "create a not existing http client" should "be return CreateHttpClientSuccessMsg(IHttpClient) which is just created" in {
    val httpClientManager = HttpClientManager(system).httpClientManager
    RoutingRegistry.register(new GoogleRoutingDefinition)
    httpClientManager ! CreateHttpClientMsg("googlemap")
    val httpClient = expectMsgType[CreateHttpClientSuccessMsg[IHttpClient]].hc
    httpClient.name should be ("googlemap")
    httpClient.endpoint should be ("http://maps.googleapis.com/maps")
    httpClient.status should be (HttpClientStatus.UP)
    httpClient.config should be (None)
    httpClient.pipelineDefinition should be (None)
  }

  "create an existing http client" should "return UpdateHttpClientFailureMsg(HttpClientExistException)" in {
    val httpClientManager = HttpClientManager(system).httpClientManager
    createGoogleMapHttpClient(httpClientManager, "googlemap")
    httpClientManager ! CreateHttpClientMsg("googlemap")
    val existException = expectMsgType[CreateHttpClientFailureMsg].e
    existException should be (HttpClientExistException("googlemap"))
  }

  def createGoogleMapHttpClient(httpClientManager: ActorRef, names: String*) = {
    val httpClientManager = HttpClientManager(system).httpClientManager
    RoutingRegistry.register(new GoogleRoutingDefinition)
    for (name <- names) {
      httpClientManager ! CreateHttpClientMsg(name)
      expectMsgType[CreateHttpClientSuccessMsg[IHttpClient]].hc
    }
  }

  "update an existing http client" should "be able to update" in {
    val httpClientManager = HttpClientManager(system).httpClientManager
    createGoogleMapHttpClient(httpClientManager, "googlemap")
    httpClientManager ! UpdateHttpClientMsg("googlemap", Some("dev"))
    val httpClient = expectMsgType[UpdateHttpClientSuccessMsg[IHttpClient]].hc
    httpClient.name should be ("googlemap")
    httpClient.endpoint should be ("http://localhost/maps")
    httpClient.status should be (HttpClientStatus.UP)
    httpClient.config should be (None)
    httpClient.pipelineDefinition should be (None)
  }

  "update not an existing http client" should "return UpdateHttpClientFailureMsg(HttpClientNotExistException)" in {
    val httpClientManager = HttpClientManager(system).httpClientManager
    httpClientManager ! UpdateHttpClientMsg("googlemap", Some("dev"))
    expectMsgType[UpdateHttpClientFailureMsg].e should be (HttpClientNotExistException("googlemap"))
  }

  "delete an existing http client" should "return DeleteHttpClientSuccessMsg(IHttpClient)" in {
    val httpClientManager = HttpClientManager(system).httpClientManager
    createGoogleMapHttpClient(httpClientManager, "googlemap")
    httpClientManager ! DeleteHttpClientMsg("googlemap")
    val httpClient = expectMsgType[DeleteHttpClientSuccessMsg[IHttpClient]].hc
    httpClient.name should be ("googlemap")
    httpClient.endpoint should be ("http://maps.googleapis.com/maps")
    httpClient.status should be (HttpClientStatus.UP)
    httpClient.config should be (None)
    httpClient.pipelineDefinition should be (None)
    httpClientManager ! GetHttpClientMsg("googlemap")
    expectMsgType[GetHttpClientFailureMsg].e should be (HttpClientNotExistException("googlemap"))
  }

  "delete not a existing http client" should "return DeleteHttpClientFailureMsg(HttpClientNotExistException)" in {
    val httpClientManager = HttpClientManager(system).httpClientManager
    httpClientManager ! DeleteHttpClientMsg("googlemap")
    expectMsgType[DeleteHttpClientFailureMsg].e should be (HttpClientNotExistException("googlemap"))
  }

  "delete all existing http client" should "return DeleteAllHttpClientSuccessMsg" in {
    val httpClientManager = HttpClientManager(system).httpClientManager
    createGoogleMapHttpClient(httpClientManager, "googlemap", "googlemap2")
    httpClientManager ! DeleteAllHttpClientMsg
    type HttpClientMap[String, IHttpClient] = TrieMap[String, IHttpClient]
    expectMsgType[DeleteAllHttpClientSuccessMsg[HttpClientMap]].map.isEmpty should be (true)
    httpClientManager ! GetAllHttpClientMsg
    expectMsgType[GetAllHttpClientSuccessMsg[HttpClientMap]].map.isEmpty should be (true)
  }

  "MarkDown/MarkUP http client if exists" should "return the correct behaviour" in {
    val httpClientManager = HttpClientManager(system).httpClientManager
    createGoogleMapHttpClient(httpClientManager, "googlemap")
    httpClientManager ! MarkDownHttpClientMsg("googlemap")
    val httpClient1 = expectMsgType[MarkDownHttpClientSuccessMsg[IHttpClient]].hc
    httpClient1.status should be (HttpClientStatus.DOWN)
    httpClientManager ! MarkUpHttpClientMsg("googlemap")
    val httpClient2 = expectMsgType[MarkUpHttpClientSuccessMsg[IHttpClient]].hc
    httpClient2.status should be (HttpClientStatus.UP)
  }

  "MarkDown/MarkUP http client if not exists" should "return the MarkDownHttpClientFailureMsg(HttpClientNotExistException)/MarkUpHttpClientFailureMsg(HttpClientNotExistException)" in {
    val httpClientManager = HttpClientManager(system).httpClientManager
    httpClientManager ! MarkDownHttpClientMsg("googlemap")
    expectMsgType[MarkDownHttpClientFailureMsg].e should be (HttpClientNotExistException("googlemap"))
    httpClientManager ! MarkUpHttpClientMsg("googlemap")
    expectMsgType[MarkUpHttpClientFailureMsg].e should be (HttpClientNotExistException("googlemap"))
  }

  "http client Get Call" should "return the expect HttpResponseWrapper" in {
    import org.squbs.hc.json.Json4sJacksonNoTypeHintsProtocol._
    val httpClientManager = HttpClientManager(system).httpClientManager
    createGoogleMapHttpClient(httpClientManager, "googlemap")
    httpClientManager ! HttpClientGetCallMsg("googlemap", HttpMethods.GET, "/api/elevation/json?locations=27.988056,86.925278&sensor=false")
    val response = expectMsgType[HttpResponseWrapper]
    response.status should be (StatusCodes.OK)
    val Right(res) = response.content
    HttpClientManager.unmarshalWithWrapper[GoogleApiResult[Elevation]](res).content should be (Right(GoogleApiResult[Elevation]("OK", List(Elevation(Location(27.988056,86.925278),8815.7158203125)))))
  }
}

class GoogleRoutingDefinition extends RoutingDefinition {
  override def resolve(svcName: String, env: Option[String]): Option[String] = {
    if (svcName == name || svcName == "googlemap2")
      env match {
        case None => Some("http://maps.googleapis.com/maps")
        case Some(env) if env.toUpperCase == "DEV" => Some("http://localhost/maps")
        case _ => Some("http://qa.maps.googleapis.com/maps")
      }

    else
      None
  }

  override def name: String = "googlemap"
}