package org.squbs.httpclient

import org.scalatest._
import org.squbs.httpclient.endpoint.{EndpointResolver, EndpointRegistry}
import akka.actor.ActorSystem
import scala.concurrent.duration._
import akka.io.IO
import spray.can.Http
import akka.pattern._
import spray.util._
import spray.can.client.{ClientConnectionSettings, HostConnectorSettings}
import org.squbs.httpclient.env._
import org.squbs.httpclient.endpoint.Endpoint
import spray.can.Http.ClientConnectionType.Proxied
import scala.Some
import org.squbs.httpclient.dummy.{DummyService, DummyProdEnvironmentResolver, DummyServiceEndpointResolver, DummyRequestResponsePipeline}
import DummyService._

/**
 * Created by hakuang on 6/10/2014.
 */
class HttpClientJMXSpec extends FlatSpec with HttpClientTestKit with Matchers with BeforeAndAfterEach with BeforeAndAfterAll{

  private implicit val system = ActorSystem("HttpClientJMXSpec")

  override def beforeEach = {
    EndpointRegistry.register(new EndpointResolver {
      override def resolve(svcName: String, env: Environment = Default): Option[Endpoint] = Some(Endpoint("http://www.ebay.com"))
      override def name: String = "hello"
    })
  }

  override def afterEach = {
    clearHttpClient
  }

  override def afterAll() {
    shutdownActorSystem
  }

  "HttpClient with svcName" should "show up the correct value of HttpClientBean" in {
    HttpClientFactory.getOrCreate("hello1")
    HttpClientFactory.getOrCreate("hello2")
    HttpClientBean.getHttpClientInfo.size should be (2)
    findHttpClientBean(HttpClientBean.getHttpClientInfo, "hello1") should be (HttpClientInfo("hello1", "default", "http://www.ebay.com", "UP", "AutoProxied", 4, 5, 0, 20000, 10000, "", ""))
    findHttpClientBean(HttpClientBean.getHttpClientInfo, "hello2") should be (HttpClientInfo("hello2", "default", "http://www.ebay.com", "UP", "AutoProxied", 4, 5, 0, 20000, 10000, "", ""))
  }

  "HttpClient with pipeline" should "show up the correct value of HttpClientBean" in {
    HttpClientFactory.getOrCreate("hello3", pipeline = Some(DummyRequestResponsePipeline))
    HttpClientFactory.getOrCreate("hello4")
    HttpClientBean.getHttpClientInfo.size should be (2)
    findHttpClientBean(HttpClientBean.getHttpClientInfo, "hello3") should be (HttpClientInfo("hello3", "default", "http://www.ebay.com", "UP", "AutoProxied", 4, 5, 0, 20000, 10000, "org.squbs.httpclient.pipeline.impl.RequestAddHeaderHandler$$anonfun$processRequest$1","org.squbs.httpclient.pipeline.impl.ResponseAddHeaderHandler$$anonfun$processResponse$1"))
    findHttpClientBean(HttpClientBean.getHttpClientInfo, "hello4") should be (HttpClientInfo("hello4", "default", "http://www.ebay.com", "UP", "AutoProxied", 4, 5, 0, 20000, 10000, "", ""))
  }

  "HttpClient with configuration" should "show up the correct value of HttpClientBean" in {
    val httpClient = HttpClientFactory.getOrCreate("hello5")
    httpClient.updateConfig(Configuration(hostSettings = HostConnectorSettings(10 ,10, 10, true, 10 seconds, ClientConnectionSettings(system)), connectionType = Proxied("www.ebay.com", 80)))
    HttpClientFactory.getOrCreate("hello6").markDown
    HttpClientBean.getHttpClientInfo.size should be (2)
    findHttpClientBean(HttpClientBean.getHttpClientInfo, "hello5") should be (HttpClientInfo("hello5", "default", "http://www.ebay.com", "UP", "www.ebay.com:80", 10, 10, 10, 20000, 10000, "", ""))
    findHttpClientBean(HttpClientBean.getHttpClientInfo, "hello6") should be (HttpClientInfo("hello6", "default", "http://www.ebay.com", "DOWN", "www.ebay.com:80", 10, 10, 10, 20000, 10000, "", ""))
  }

  "HttpClient Endpoint Resolver Info" should "show up the correct value of EndpointResolverBean" in {
    EndpointRegistry.register(DummyServiceEndpointResolver)
    EndpointResolverBean.getHttpClientEndpointResolverInfo.size should be (2)
    EndpointResolverBean.getHttpClientEndpointResolverInfo.get(0).position should be (0)
    EndpointRegistry.resolve("DummyService") should be (Some(Endpoint(dummyServiceEndpoint)))
    EndpointResolverBean.getHttpClientEndpointResolverInfo.get(0).resolver should be ("org.squbs.httpclient.dummy.DummyServiceEndpointResolver$")
  }

  "HttpClient Environment Resolver Info" should "show up the correct value of EnvironmentResolverBean" in {
    EnvironmentResolverBean.getHttpClientEnvironmentResolverInfo.size should be (0)
    EnvironmentRegistry.register(DummyProdEnvironmentResolver)
    EnvironmentResolverBean.getHttpClientEnvironmentResolverInfo.size should be (1)
    EnvironmentResolverBean.getHttpClientEnvironmentResolverInfo.get(0).position should be (0)
    EnvironmentRegistry.resolve("abc") should be (PROD)
    EnvironmentResolverBean.getHttpClientEnvironmentResolverInfo.get(0).resolver should be ("org.squbs.httpclient.dummy.DummyProdEnvironmentResolver$")
  }

  def findHttpClientBean(beans: java.util.List[HttpClientInfo], name: String): HttpClientInfo = {
    import scala.collection.JavaConversions._
    beans.toList.find(_.name == name).get
  }
}