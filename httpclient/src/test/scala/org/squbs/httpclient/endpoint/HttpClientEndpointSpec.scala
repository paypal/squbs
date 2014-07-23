package org.squbs.httpclient.endpoint

import org.scalatest.{BeforeAndAfterEach, Matchers, FlatSpec}
import org.squbs.httpclient.{HttpClientFactory, HttpClientException}
import org.squbs.httpclient.env.{QA, DEV, Environment, Default}

/**
 * Created by hakuang on 5/22/2014.
 */
class HttpClientEndpointSpec extends FlatSpec with Matchers with BeforeAndAfterEach{

  override def afterEach = {
    EndpointRegistry.endpointResolvers.clear
    HttpClientFactory.httpClientMap.clear
  }

  class LocalhostRouting extends EndpointResolver {
    override def resolve(svcName: String, env: Environment = Default): Option[Endpoint] = {
      if (svcName == null && svcName.length <= 0) throw new HttpClientException(700, "Service name cannot be null")
      env match {
        case Default | DEV => Some(Endpoint("http://localhost:8080/" + svcName))
        case _   => throw new HttpClientException(701, "LocalhostRouting cannot support " + env + " environment")
      }
    }

    override def name: String = "localhost"
  }

  "RoutingRegistry" should "contain LocalhostRouting" in {
    EndpointRegistry.register(new LocalhostRouting)
    EndpointRegistry.endpointResolvers.length should be (1)
    EndpointRegistry.endpointResolvers.head.isInstanceOf[LocalhostRouting] should be (true)
  }

  "localhost routing" should "be return to the correct value" in {
    EndpointRegistry.register(new LocalhostRouting)
    EndpointRegistry.route("abcService") should not be (None)
    EndpointRegistry.route("abcService").get.name should be ("localhost")
    EndpointRegistry.route("abcService").get.resolve("abcService") should be (Some(Endpoint("http://localhost:8080/abcService")))
  }

  "localhost routing" should "be throw out HttpClientException if env isn't Dev" in {
    a[HttpClientException] should be thrownBy {
      EndpointRegistry.register(new LocalhostRouting)
      EndpointRegistry.route("abcService", QA)
    }
  }

  "localhost routing" should "be return to the correct value if env is Dev" in {
    EndpointRegistry.register(new LocalhostRouting)
    EndpointRegistry.route("abcService", DEV) should not be (None)
    EndpointRegistry.route("abcService", DEV).get.name should be ("localhost")
    EndpointRegistry.resolve("abcService", DEV) should be (Some(Endpoint("http://localhost:8080/abcService")))
  }

  "Latter registry RoutingDefinition" should "have high priority" in {
    EndpointRegistry.register(new LocalhostRouting)
    EndpointRegistry.register(new EndpointResolver {
      override def resolve(svcName: String, env: Environment = Default): Option[Endpoint] = Some(Endpoint("http://localhost:8080/override"))

      override def name: String = "override"
    })
    EndpointRegistry.endpointResolvers.length should be (2)
    EndpointRegistry.endpointResolvers.head.isInstanceOf[LocalhostRouting] should be (false)
    EndpointRegistry.endpointResolvers.head.name should be ("override")
    EndpointRegistry.route("abcService") should not be (None)
    EndpointRegistry.route("abcService").get.name should be ("override")
    EndpointRegistry.resolve("abcService") should be (Some(Endpoint("http://localhost:8080/override")))
  }

  "It" should "fallback to the previous RoutingDefinition if latter one cannot be resolve" in {
    EndpointRegistry.register(new LocalhostRouting)
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
    EndpointRegistry.route("abcService").get.name should be ("localhost")
    EndpointRegistry.route("unique") should not be (None)
    EndpointRegistry.route("unique").get.name should be ("unique")
    EndpointRegistry.resolve("abcService") should be (Some(Endpoint("http://localhost:8080/abcService")))
    EndpointRegistry.resolve("unique") should be (Some(Endpoint("http://www.ebay.com/unique")))
  }

  "unregister RoutingDefinition" should "have the correct behaviour" in {
    EndpointRegistry.register(new EndpointResolver {
      override def resolve(svcName: String, env: Environment = Default): Option[Endpoint] = {
        svcName match {
          case "unique" => Some(Endpoint("http://www.ebay.com/unique"))
          case _ => None
        }
      }

      override def name: String = "unique"
    })
    EndpointRegistry.register(new LocalhostRouting)

    EndpointRegistry.endpointResolvers.length should be (2)
    EndpointRegistry.endpointResolvers.head.isInstanceOf[LocalhostRouting] should be (true)
    EndpointRegistry.resolve("unique") should be (Some(Endpoint("http://localhost:8080/unique")))
    EndpointRegistry.unregister("localhost")
    EndpointRegistry.endpointResolvers.length should be (1)
    EndpointRegistry.resolve("unique") should be (Some(Endpoint("http://www.ebay.com/unique")))
  }
}
