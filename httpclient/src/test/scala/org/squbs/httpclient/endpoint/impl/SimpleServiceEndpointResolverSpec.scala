package org.squbs.httpclient.endpoint.impl

import org.scalatest.{BeforeAndAfterAll, Matchers, FlatSpec}
import org.squbs.httpclient.endpoint.{Endpoint, EndpointRegistry}
import org.squbs.httpclient.{HttpClientTestKit, Configuration}
import javax.net.ssl.SSLContext

/**
 * Created by hakuang on 7/23/2014.
 */
class SimpleServiceEndpointResolverSpec extends FlatSpec with HttpClientTestKit with Matchers with BeforeAndAfterAll{

  override def afterAll = {
    clearHttpClient
  }

  "SimpleServiceEndpintResolver" should "have the correct behaviour" in {
    val simpleResolver = SimpleServiceEndpointResolver("simple", Map[String, Configuration](
      "http://localhost:8080" -> Configuration(),
      "https://localhost:8443" -> Configuration(sslContext = Some(SSLContext.getDefault))
    ))
    EndpointRegistry.register(simpleResolver)
    EndpointRegistry.resolve("http://localhost:8080") should be (Some(Endpoint("http://localhost:8080")))
    EndpointRegistry.resolve("https://localhost:8443") should be (Some(Endpoint("https://localhost:8443", Configuration(sslContext = Some(SSLContext.getDefault)))))
    EndpointRegistry.resolve("notExisting") should be (None)
    EndpointRegistry.unregister(simpleResolver.name)
  }
}
