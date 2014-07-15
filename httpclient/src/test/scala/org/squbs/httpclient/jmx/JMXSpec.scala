package org.squbs.httpclient.jmx

import org.scalatest._
import org.squbs.httpclient.{HttpClientFactory}
import org.squbs.httpclient.endpoint.{EndpointResolver, EndpointRegistry}
import akka.actor.ActorSystem
import org.squbs.httpclient.pipeline.Pipeline
import spray.client.pipelining._
import scala.concurrent.duration._
import org.squbs.httpclient.config.ServiceConfiguration
import org.squbs.httpclient.config.Configuration
import org.squbs.httpclient.pipeline.impl.RequestAddHeaderHandler
import org.squbs.httpclient.pipeline.impl.ResponseAddHeaderHandler
import spray.http.HttpHeaders.RawHeader
import spray.can.Http.ClientConnectionType.Proxied
import scala.Some
import org.squbs.httpclient.config.HostConfiguration
import akka.io.IO
import spray.can.Http
import akka.pattern._
import spray.util._
import org.squbs.httpclient.actor.HttpClientManager

/**
 * Created by hakuang on 6/10/2014.
 */
class JMXSpec extends FlatSpec with Matchers with BeforeAndAfterEach with BeforeAndAfterAll{

  private implicit val system = ActorSystem("JMXSpec")
  private val httpClientBean = new HttpClientBean

  override def beforeEach = {
    EndpointRegistry.register(new EndpointResolver {
      override def resolve(svcName: String, env: Option[String]): Option[String] = Some("http://www.ebay.com")
      override def name: String = "hello"
    })
  }

  override def afterEach = {
    EndpointRegistry.routingDefinitions.clear
    HttpClientManager.httpClientMap.clear
    HttpClientFactory.httpClientMap.clear
  }

  override def afterAll() {
    IO(Http).ask(Http.CloseAll)(1.second).await
    system.shutdown()
  }

  "HttpClient with svcName" should "show up the correct value of HttpClientBean" in {
    HttpClientFactory.getOrCreate("hello1")
    HttpClientFactory.getOrCreate("hello2")
    httpClientBean.getHttpClient.size should be (2)
    findHttpClientBean(httpClientBean.getHttpClient, "hello1") should be (HttpClientInfo("hello1", "http://www.ebay.com", "UP", 0, 1000, 1000, "AutoProxied", "", ""))
    findHttpClientBean(httpClientBean.getHttpClient, "hello2") should be (HttpClientInfo("hello2", "http://www.ebay.com", "UP", 0, 1000, 1000, "AutoProxied", "", ""))
  }

  "HttpClient with pipeline" should "show up the correct value of HttpClientBean" in {
    HttpClientFactory.getOrCreate("hello3", pipeline = Some(RequestResponsePipeline))
    HttpClientFactory.getOrCreate("hello4")
    httpClientBean.getHttpClient.size should be (2)
    findHttpClientBean(httpClientBean.getHttpClient, "hello3") should be (HttpClientInfo("hello3", "http://www.ebay.com", "UP", 0, 1000, 1000, "AutoProxied", "org.squbs.httpclient.pipeline.impl.RequestAddHeaderHandler$$anonfun$processRequest$1","org.squbs.httpclient.pipeline.impl.ResponseAddHeaderHandler$$anonfun$processResponse$1"))
    findHttpClientBean(httpClientBean.getHttpClient, "hello4") should be (HttpClientInfo("hello4", "http://www.ebay.com", "UP", 0, 1000, 1000, "AutoProxied", "",""))
  }

  "HttpClient with configuration" should "show up the correct value of HttpClientBean" in {
    HttpClientFactory.getOrCreate("hello5", config = Some(Configuration(ServiceConfiguration(10, 10 seconds, 10 seconds), HostConfiguration(connectionType = Proxied("www.ebay.com", 80)))))
    HttpClientFactory.getOrCreate("hello6").markDown
    httpClientBean.getHttpClient.size should be (2)
    findHttpClientBean(httpClientBean.getHttpClient, "hello5") should be (HttpClientInfo("hello5", "http://www.ebay.com", "UP", 10, 10000, 10000, "www.ebay.com:80", "", ""))
    findHttpClientBean(httpClientBean.getHttpClient, "hello6") should be (HttpClientInfo("hello6", "http://www.ebay.com", "DOWN", 0, 1000, 1000, "AutoProxied", "", ""))
  }

  def findHttpClientBean(beans: java.util.List[HttpClientInfo], name: String): HttpClientInfo = {
    import scala.collection.JavaConversions._
    beans.toList.find(_.serviceName == name).get
  }
}

object RequestResponsePipeline extends Pipeline {
  override def requestPipelines: Seq[RequestTransformer] = Seq[RequestTransformer](RequestAddHeaderHandler(RawHeader("request1-name", "request1-value")).processRequest)
  override def responsePipelines: Seq[ResponseTransformer] = Seq[ResponseTransformer](ResponseAddHeaderHandler(RawHeader("response1-name", "response1-value")).processResponse)
}
