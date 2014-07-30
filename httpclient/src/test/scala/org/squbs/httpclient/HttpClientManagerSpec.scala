package org.squbs.httpclient

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{ActorRef, ActorSystem}
import org.scalatest.{BeforeAndAfterAll, Matchers, FlatSpecLike}
import org.squbs.httpclient.endpoint.{Endpoint, EndpointRegistry}
import org.squbs.httpclient.dummy.{DummyService, DummyServiceEndpointResolver}
import org.squbs.httpclient.HttpClientManagerMessage._
import scala.collection.concurrent.TrieMap
import org.squbs.httpclient.env.{Default, Environment}
import org.squbs.httpclient.dummy.DummyService._
import org.squbs.httpclient.HttpClientManagerMessage.CreateHttpClient
import org.squbs.httpclient.HttpClientManagerMessage.DeleteHttpClient
import scala.Some

/**
 * Created by hakuang on 7/29/2014.
 */
class HttpClientManagerSpec extends TestKit(ActorSystem("HttpClientManagerSpec")) with FlatSpecLike with HttpClientTestKit with Matchers with ImplicitSender with BeforeAndAfterAll with DummyService{

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
    httpClientManager ! GetAllHttpClient
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
    client.pipeline should be (None)
    client.endpoint should be (Some(Endpoint(dummyServiceEndpoint)))
    deleteHttpClient("DummyService")
  }

  "create an existing httpclient" should "return HttpClientExistException" in {
    createHttpClient("DummyService")
    val httpClientManager = HttpClientManager(system).httpClientManager
    httpClientManager ! CreateHttpClient("DummyService")
    val existException = expectMsgType[HttpClientExistException]
    existException should be (HttpClientExistException("DummyService"))
    deleteHttpClient("DummyService")
  }

  "delete an existing httpclient" should "return DeleteHttpClientSuccess" in {
    createHttpClient("DummyService")
    deleteHttpClient("DummyService")
    HttpClientManager.httpClientMap should be (TrieMap.empty)
  }

  "delete not a existing httpclient" should "return HttpClientNotExistException" in {
    val httpClientManager = HttpClientManager(system).httpClientManager
    httpClientManager ! DeleteHttpClient("DummyService")
    val existException = expectMsgType[HttpClientNotExistException]
    existException should be (HttpClientNotExistException("DummyService"))
  }

  "delete all existing httpclient" should "return DeleteAllHttpClientSuccess" in {
    createHttpClient("DummyService")
    createHttpClient("http://localhost:8080/test")
    HttpClientManager.httpClientMap.size should be (2)
    val httpClientManager = HttpClientManager(system).httpClientManager
    httpClientManager ! DeleteAllHttpClient
    expectMsg(DeleteAllHttpClientSuccess)
    HttpClientManager.httpClientMap should be (TrieMap.empty)
  }

  "get an existing httpclient" should "return ActorRef of HttpClientActor" in {
    createHttpClient("DummyService")
    val httpClientManager = HttpClientManager(system).httpClientManager
    httpClientManager ! GetHttpClient("DummyService")
    expectMsgType[ActorRef]
    deleteHttpClient("DummyService")
  }

  def createHttpClient(name: String) = {
    val httpClientManager = HttpClientManager(system).httpClientManager
    httpClientManager ! CreateHttpClient(name)
    expectMsgType[ActorRef]
  }

  def deleteHttpClient(name: String, env: Environment = Default) = {
    val httpClientManager = HttpClientManager(system).httpClientManager
    httpClientManager ! DeleteHttpClient(name, env)
    expectMsg(DeleteHttpClientSuccess)
  }
}
