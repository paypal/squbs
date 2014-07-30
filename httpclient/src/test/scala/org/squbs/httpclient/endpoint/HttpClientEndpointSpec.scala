package org.squbs.httpclient.endpoint

import org.scalatest.{BeforeAndAfterEach, Matchers, FlatSpec}
import org.squbs.httpclient.{HttpClientTestKit, HttpClientFactory, HttpClientException}
import org.squbs.httpclient.env._
import scala.Some
import org.squbs.httpclient.dummy.{DummyLocalhostResolver}

/**
 * Created by hakuang on 5/22/2014.
 */
class HttpClientEndpointSpec extends FlatSpec with HttpClientTestKit with Matchers with BeforeAndAfterEach{

  override def afterEach = {
    clearHttpClient
  }

  "EndpointRegistry" should "contain DummyLocalhostResolver" in {
    EndpointRegistry.register(DummyLocalhostResolver)
    EndpointRegistry.endpointResolvers.length should be (1)
    EndpointRegistry.endpointResolvers.head should be (DummyLocalhostResolver)
  }

  "EndpointRegistry register twice of the same resolver" should "contain once" in {
    EndpointRegistry.register(DummyLocalhostResolver)
    EndpointRegistry.register(DummyLocalhostResolver)
    EndpointRegistry.endpointResolvers.length should be (1)
    EndpointRegistry.endpointResolvers.head should be (DummyLocalhostResolver)
  }

  "EndpointRegistry unregister not existing resolver" should "be ignored" in {
    EndpointRegistry.register(DummyLocalhostResolver)
    EndpointRegistry.unregister("NotExistingResolver")
    EndpointRegistry.endpointResolvers.length should be (1)
    EndpointRegistry.endpointResolvers.head should be (DummyLocalhostResolver)
  }

  "DummyLocalhostResolver" should "be return to the correct value" in {
    EndpointRegistry.register(DummyLocalhostResolver)
    EndpointRegistry.route("abcService") should not be (None)
    EndpointRegistry.route("abcService").get.name should be ("DummyLocalhostResolver")
    EndpointRegistry.route("abcService").get.resolve("abcService") should be (Some(Endpoint("http://localhost:8080/abcService")))
  }

  "DummyLocalhostResolver" should "be throw out HttpClientException if env isn't Dev" in {
    a[HttpClientException] should be thrownBy {
      EndpointRegistry.register(DummyLocalhostResolver)
      EndpointRegistry.route("abcService", QA)
    }
  }

  "DummyLocalhostResolver" should "be return to the correct value if env is Dev" in {
    EndpointRegistry.register(DummyLocalhostResolver)
    EndpointRegistry.route("abcService", DEV) should not be (None)
    EndpointRegistry.route("abcService", DEV).get.name should be ("DummyLocalhostResolver")
    EndpointRegistry.resolve("abcService", DEV) should be (Some(Endpoint("http://localhost:8080/abcService")))
  }

  "Latter registry EndpointResolver" should "have high priority" in {
    EndpointRegistry.register(DummyLocalhostResolver)
    EndpointRegistry.register(new EndpointResolver {
      override def resolve(svcName: String, env: Environment = Default): Option[Endpoint] = Some(Endpoint("http://localhost:8080/override"))

      override def name: String = "override"
    })
    EndpointRegistry.endpointResolvers.length should be (2)
    EndpointRegistry.endpointResolvers.head should not be (DummyLocalhostResolver)
    EndpointRegistry.endpointResolvers.head.name should be ("override")
    EndpointRegistry.route("abcService") should not be (None)
    EndpointRegistry.route("abcService").get.name should be ("override")
    EndpointRegistry.resolve("abcService") should be (Some(Endpoint("http://localhost:8080/override")))
  }

  "It" should "fallback to the previous EndpointResolver if latter one cannot be resolve" in {
    EndpointRegistry.register(DummyLocalhostResolver)
    EndpointRegistry.register(new EndpointResolver {
      override def resolve(svcName: String, env: Environment = Default): Option[Endpoint] = {
        svcName match {
          case "unique" => Some(Endpoint("http://www.ebay.com/unique"))
          case _ => None
        }
      }

      override def name: String = "unique"
    })
    EndpointRegistry.endpointResolvers.length should be (2)
    EndpointRegistry.route("abcService") should not be (None)
    EndpointRegistry.route("abcService").get.name should be ("DummyLocalhostResolver")
    EndpointRegistry.route("unique") should not be (None)
    EndpointRegistry.route("unique").get.name should be ("unique")
    EndpointRegistry.resolve("abcService") should be (Some(Endpoint("http://localhost:8080/abcService")))
    EndpointRegistry.resolve("unique") should be (Some(Endpoint("http://www.ebay.com/unique")))
  }

  "unregister EndpointResolver" should "have the correct behaviour" in {
    EndpointRegistry.register(new EndpointResolver {
      override def resolve(svcName: String, env: Environment = Default): Option[Endpoint] = {
        svcName match {
          case "unique" => Some(Endpoint("http://www.ebay.com/unique"))
          case _ => None
        }
      }

      override def name: String = "unique"
    })
    EndpointRegistry.register(DummyLocalhostResolver)

    EndpointRegistry.endpointResolvers.length should be (2)
    EndpointRegistry.endpointResolvers.head should be (DummyLocalhostResolver)
    EndpointRegistry.resolve("unique") should be (Some(Endpoint("http://localhost:8080/unique")))
    EndpointRegistry.unregister("DummyLocalhostResolver")
    EndpointRegistry.endpointResolvers.length should be (1)
    EndpointRegistry.resolve("unique") should be (Some(Endpoint("http://www.ebay.com/unique")))
  }
}
