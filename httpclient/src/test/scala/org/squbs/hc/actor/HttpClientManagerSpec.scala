package org.squbs.hc.actor

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{ActorRef, ActorSystem}
import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll, Matchers, FlatSpecLike}
import org.squbs.hc.actor.HttpClientMessage.{UpdateHttpClientMsg, CreateHttpClientMsg, GetAllHttpClientMsg}
import scala.collection.concurrent.TrieMap
import org.squbs.hc.{HttpClientExistException, HttpClientStatus, IHttpClient}
import org.squbs.hc.routing.{RoutingRegistry, RoutingDefinition}

/**
 * Created by hakuang on 6/26/2014.
 */
class HttpClientManagerSpec extends TestKit(ActorSystem("httpclientmanagerspec")) with FlatSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll with BeforeAndAfterEach{

  override def afterAll = {
    system.shutdown
  }

  override def beforeEach = {
    RoutingRegistry.clear

  }

  "httpClientMap" should "be emtpy before creating any httpclient" in {
    val httpClientManager = HttpClientManager(system).httpClientManager
    httpClientManager ! GetAllHttpClientMsg
    expectMsgType[TrieMap[String, IHttpClient]].isEmpty should be (true)
  }

  "create a not existing http client" should "be return IHttpClient which is just created" in {
    val httpClientManager = HttpClientManager(system).httpClientManager
    RoutingRegistry.register(new GoogleRoutingDefinition)
    httpClientManager ! CreateHttpClientMsg("googlemap")
    val httpClient = expectMsgType[IHttpClient]
    httpClient.name should be ("googlemap")
    httpClient.endpoint should be ("http://localhost/maps")
    httpClient.status should be (HttpClientStatus.UP)
    httpClient.config should be (None)
    httpClient.pipelineDefinition should be (None)
  }

  "create a existing http client" should "be return HttpClientExistException" in {
    val httpClientManager = HttpClientManager(system).httpClientManager
    createGoogleMapHttpClient(httpClientManager)
    httpClientManager ! CreateHttpClientMsg("googlemap")
    val existException = expectMsgType[HttpClientExistException]
    existException.getMessage should be ("HttpClient:googlemap has been registry!")
  }

  def createGoogleMapHttpClient(httpClientManager: ActorRef) = {
    val httpClientManager = HttpClientManager(system).httpClientManager
    RoutingRegistry.register(new GoogleRoutingDefinition)
    httpClientManager ! CreateHttpClientMsg("googlemap")
    val httpClient = expectMsgType[IHttpClient]
    httpClient.name should be ("googlemap")
    httpClient.endpoint should be ("http://localhost/maps")
    httpClient.status should be (HttpClientStatus.UP)
    httpClient.config should be (None)
    httpClient.pipelineDefinition should be (None)
  }

  "update a existing http client" should "be able to update" in {
    val httpClientManager = HttpClientManager(system).httpClientManager
    createGoogleMapHttpClient(httpClientManager)
    httpClientManager ! UpdateHttpClientMsg("googlemap", Some("prod"))
    val httpClient = expectMsgType[IHttpClient]
    httpClient.name should be ("googlemap")
    httpClient.endpoint should be ("http://maps.googleapis.com/maps")
    httpClient.status should be (HttpClientStatus.UP)
    httpClient.config should be (None)
    httpClient.pipelineDefinition should be (None)
  }
}

class GoogleRoutingDefinition extends RoutingDefinition {
  override def resolve(svcName: String, env: Option[String]): Option[String] = {
    if (svcName == name)
      env match {
        case None => Some("http://localhost/maps")
        case Some(env) if env.toUpperCase == "PROD" => Some("http://maps.googleapis.com/maps")
        case _ => Some("http://qa.maps.googleapis.com/maps")
      }

    else
      None
  }

  override def name: String = "googlemap"
}

