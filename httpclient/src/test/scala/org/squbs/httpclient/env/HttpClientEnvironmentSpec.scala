package org.squbs.httpclient.env

import org.squbs.httpclient.endpoint.EndpointRegistry
import org.squbs.httpclient.{HttpClientTestKit, HttpClientFactory}
import org.squbs.httpclient.dummy.{DummyProdEnvironmentResolver, DummyPriorityEnvironmentResolver}
import org.scalatest._

/**
 * Created by hakuang on 7/22/2014.
 */
class HttpClientEnvironmentSpec extends FlatSpec with HttpClientTestKit with Matchers with BeforeAndAfterEach{

  override def beforeEach = {
    EnvironmentRegistry.register(DummyProdEnvironmentResolver)
  }

  override def afterEach = {
    clearHttpClient
  }

  "EnvironmentResolverRegistry" should "contain DummyProdEnvironmentResolver" in {
    EnvironmentRegistry.environmentResolvers.length should be (1)
    EnvironmentRegistry.environmentResolvers.head should be (DummyProdEnvironmentResolver)
  }

  "DummyProdEnvironmentResolver" should "return to the correct value" in {
    EnvironmentRegistry.resolve("abc") should be (PROD)
  }

  "Latter registry EnvironmentResolver" should "have high priority" in {
    EnvironmentRegistry.register(DummyPriorityEnvironmentResolver)
    EnvironmentRegistry.resolve("abc") should be (QA)
    EnvironmentRegistry.unregister("DummyPriorityEnvironmentResolver")
  }
  
  it should "fallback to the previous EnvironmentResolver" in {
    EnvironmentRegistry.register(DummyPriorityEnvironmentResolver)
    EnvironmentRegistry.resolve("test") should be (PROD)
    EnvironmentRegistry.unregister("DummyPriorityEnvironmentResolver")
  }

  "unregister EnvironmentResolver" should "have the correct behaviour" in {
    EnvironmentRegistry.register(DummyPriorityEnvironmentResolver)
    EnvironmentRegistry.resolve("abc") should be (QA)
    EnvironmentRegistry.unregister("DummyPriorityEnvironmentResolver")
    EnvironmentRegistry.resolve("abc") should be (PROD)
  }

}
