package org.squbs.httpclient.actor

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{ActorRef, ActorSystem}
import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll, Matchers, FlatSpecLike}
import scala.collection.concurrent.TrieMap
import org.squbs.httpclient._
import org.squbs.httpclient.endpoint.{EndpointRegistry, EndpointResolver}
import akka.pattern._
import scala.concurrent.duration._
import spray.util._
import spray.http.{StatusCodes}
import akka.io.IO
import spray.can.Http
import org.squbs.httpclient.actor.HttpClientManagerMessage._
import org.squbs.httpclient.actor.HttpClientActorMessage._
import org.squbs.httpclient.config.ServiceConfiguration
import org.squbs.httpclient.actor.HttpClientManagerMessage.CreateHttpClient
import org.squbs.httpclient.GoogleApiResult
import org.squbs.httpclient.actor.HttpClientActorMessage.Update
import org.squbs.httpclient.actor.HttpClientManagerMessage.DeleteHttpClient
import org.squbs.httpclient.Location
import scala.Some
import org.squbs.httpclient.config.Configuration
import org.squbs.httpclient.Elevation
import org.squbs.httpclient.HttpClientNotExistException
import org.squbs.httpclient.HttpResponseWrapper
import org.squbs.httpclient.HttpClientExistException
import org.squbs.httpclient.config.HostConfiguration

/**
 * Created by hakuang on 6/26/2014.
 */
class HttpClientManagerSpec extends TestKit(ActorSystem("HttpClientManagerSpec")) with FlatSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll with BeforeAndAfterEach{

  override def afterAll = {
    IO(Http).ask(Http.CloseAll)(1.second).await
    system.shutdown
  }

  override def afterEach = {
    EndpointRegistry.routingDefinitions.clear
    HttpClientManager.httpClientMap.clear
    HttpClientFactory.httpClientMap.clear
  }

  "httpClientMap" should "be emtpy before creating any httpclient" in {
    val httpClientManager = HttpClientManager(system).httpClientManager
    httpClientManager ! GetAllHttpClient
    type HttpClientMap = TrieMap[(String, Option[String]), (Client, ActorRef)]
    expectMsgType[HttpClientMap].isEmpty should be (true)
  }

  "create a not existing http client" should "be return ActorRef of HttpClientActor" in {
    val httpClientManager = HttpClientManager(system).httpClientManager
    EndpointRegistry.register(new GoogleEndpointResolver)
    httpClientManager ! CreateHttpClient("googlemap")
    expectMsgType[ActorRef]
    //TODO verify the internal status of httpClientMap
//    httpClient.name should be ("googlemap")
//    httpClient.endpoint should be ("http://maps.googleapis.com/maps")
//    httpClient.status should be (Status.UP)
//    httpClient.config should be (None)
//    httpClient.pipeline should be (None)
  }

  "create an existing http client" should "return UpdateHttpClientFailureMsg(HttpClientExistException)" in {
    val httpClientManager = HttpClientManager(system).httpClientManager
    createGoogleMapHttpClient(httpClientManager, "googlemap")
    httpClientManager ! CreateHttpClient("googlemap")
    val existException = expectMsgType[HttpClientExistException]
    existException should be (HttpClientExistException("googlemap"))
  }

  def createGoogleMapHttpClient(httpClientManager: ActorRef, name: String) = {
    val httpClientManager = HttpClientManager(system).httpClientManager
    EndpointRegistry.register(new GoogleEndpointResolver)
    httpClientManager ! CreateHttpClient(name)
    expectMsgType[ActorRef]
  }

  "update an existing http client" should "be able to update" in {
    val httpClientManager = HttpClientManager(system).httpClientManager
    val httpClientActor = createGoogleMapHttpClient(httpClientManager, "googlemap")
    httpClientActor ! Update(config = Some(Configuration(svcConfig = ServiceConfiguration(maxRetryCount = 10), hostConfig = HostConfiguration())))
    expectMsg(UpdateHttpClientSuccess)
    //TODO Verify the internal state of httpClientMap
//    httpClient.name should be ("googlemap")
//    httpClient.endpoint should be ("http://maps.googleapis.com/maps")
//    httpClient.status should be (Status.UP)
//    httpClient.config should be (Some(Configuration(svcConfig = ServiceConfiguration(maxRetryCount = 10), hostConfig = HostConfiguration())))
//    httpClient.pipeline should be (None)
  }

  "delete an existing http client" should "return DeleteHttpClientSuccessMsg(IHttpClient)" in {
    val httpClientManager = HttpClientManager(system).httpClientManager
    createGoogleMapHttpClient(httpClientManager, "googlemap")
    httpClientManager ! DeleteHttpClient("googlemap")
    expectMsg(DeleteHttpClientSuccess)
    //TODO Verify the internal state of httpClientMap
//    httpClient.name should be ("googlemap")
//    httpClient.endpoint should be ("http://maps.googleapis.com/maps")
//    httpClient.status should be (Status.UP)
//    httpClient.config should be (None)
//    httpClient.pipeline should be (None)
//    httpClientManager ! GetHttpClient("googlemap")
//    expectMsgType[GetHttpClientFailureMsg].e should be (HttpClientNotExistException("googlemap"))
  }

  "delete not a existing http client" should "return DeleteHttpClientFailureMsg(HttpClientNotExistException)" in {
    val httpClientManager = HttpClientManager(system).httpClientManager
    httpClientManager ! DeleteHttpClient("googlemap")
    expectMsgType[HttpClientNotExistException] should be (HttpClientNotExistException("googlemap"))
  }

  "delete all existing http client" should "return DeleteAllHttpClientSuccessMsg" in {
    val httpClientManager = HttpClientManager(system).httpClientManager
    createGoogleMapHttpClient(httpClientManager, "googlemap")
    createGoogleMapHttpClient(httpClientManager, "googlemap2")
    httpClientManager ! DeleteAllHttpClient
    type HttpClientMap = TrieMap[(String, Option[String]), (Client, ActorRef)]
    expectMsg(DeleteAllHttpClientSuccess)
    httpClientManager ! GetAllHttpClient
    expectMsgType[HttpClientMap].isEmpty should be (true)
  }

  "MarkDown/MarkUP http client if exists" should "return the correct behaviour" in {
    val httpClientManager = HttpClientManager(system).httpClientManager
    val httpClientActor = createGoogleMapHttpClient(httpClientManager, "googlemap")
    httpClientActor ! MarkDown
    expectMsg(MarkDownHttpClientSuccess)
    //TODO Verify the Internal State of httpClientMap
    httpClientActor ! MarkUp
    expectMsg(MarkUpHttpClientSuccess)
    //TODO Verify the Internal State of httpClientMap
  }

  "http client Get Call" should "return the expect HttpResponseWrapper" in {
    import org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol._
    val httpClientManager = HttpClientManager(system).httpClientManager
    val httpClientActor = createGoogleMapHttpClient(httpClientManager, "googlemap")
    httpClientActor ! Get("/api/elevation/json?locations=27.988056,86.925278&sensor=false")
    val response = expectMsgType[HttpResponseWrapper]
    response.status should be (StatusCodes.OK)
    val Right(res) = response.content
    HttpClientManager.unmarshalWithWrapper[GoogleApiResult[Elevation]](res).content should be (Right(GoogleApiResult[Elevation]("OK", List(Elevation(Location(27.988056,86.925278),8815.7158203125)))))
  }
}

class GoogleEndpointResolver extends EndpointResolver {
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