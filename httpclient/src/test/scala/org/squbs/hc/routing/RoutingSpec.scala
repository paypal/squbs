package org.squbs.hc.routing

import org.scalatest.{BeforeAndAfterEach, Matchers, FlatSpec}
import org.squbs.hc.{HttpClientFactory, HttpClientException}
import org.squbs.hc.actor.HttpClientManager

/**
 * Created by hakuang on 5/22/2014.
 */
class RoutingSpec extends FlatSpec with Matchers with BeforeAndAfterEach{

  override def afterEach = {
    RoutingRegistry.routingDefinitions.clear
    HttpClientManager.httpClientMap.clear
    HttpClientFactory.httpClientMap.clear
  }

  class LocalhostRouting extends RoutingDefinition {
    override def resolve(svcName: String, env: Option[String]): Option[String] = {
      if (svcName == null && svcName.length <= 0) throw new HttpClientException(700, "Service name cannot be null")
      env match {
        case None => Some("http://localhost:8080/" + svcName)
        case Some(env) if env.toLowerCase == "dev" => Some("http://localhost:8080/" + svcName)
        case Some(env) => throw new HttpClientException(701, "LocalhostRouting cannot support " + env + " environment")
      }
    }

    override def name: String = "localhost"
  }

  "RoutingRegistry" should "contain LocalhostRouting" in {
    RoutingRegistry.register(new LocalhostRouting)
    RoutingRegistry.routingDefinitions.length should be (1)
    RoutingRegistry.routingDefinitions.head.isInstanceOf[LocalhostRouting] should be (true)
  }

  "localhost routing" should "be return to the correct value" in {
    RoutingRegistry.register(new LocalhostRouting)
    RoutingRegistry.route("abcService") should not be (None)
    RoutingRegistry.route("abcService").get.name should be ("localhost")
    RoutingRegistry.route("abcService").get.resolve("abcService") should be (Some("http://localhost:8080/abcService"))
  }

  "localhost routing" should "be throw out HttpClientException if env isn't Dev" in {
    a[HttpClientException] should be thrownBy {
      RoutingRegistry.register(new LocalhostRouting)
      RoutingRegistry.route("abcService", Some("qa"))
    }
  }

  "localhost routing" should "be return to the correct value if env is Dev" in {
    RoutingRegistry.register(new LocalhostRouting)
    RoutingRegistry.route("abcService", Some("dev")) should not be (None)
    RoutingRegistry.route("abcService", Some("dev")).get.name should be ("localhost")
    RoutingRegistry.resolve("abcService", Some("dev")) should be (Some("http://localhost:8080/abcService"))
  }

  "Latter registry RoutingDefinition" should "have high priority" in {
    RoutingRegistry.register(new LocalhostRouting)
    RoutingRegistry.register(new RoutingDefinition {
      override def resolve(svcName: String, env: Option[String]): Option[String] = Some("http://localhost:8080/override")

      override def name: String = "override"
    })
    RoutingRegistry.routingDefinitions.length should be (2)
    RoutingRegistry.routingDefinitions.head.isInstanceOf[LocalhostRouting] should be (false)
    RoutingRegistry.routingDefinitions.head.asInstanceOf[RoutingDefinition].name should be ("override")
    RoutingRegistry.route("abcService") should not be (None)
    RoutingRegistry.route("abcService").get.name should be ("override")
    RoutingRegistry.resolve("abcService") should be (Some("http://localhost:8080/override"))
  }

  "It" should "fallback to the previous RoutingDefinition if latter one cannot be resolve" in {
    RoutingRegistry.register(new LocalhostRouting)
    RoutingRegistry.register(new RoutingDefinition {
      override def resolve(svcName: String, env: Option[String]): Option[String] = {
        svcName match {
          case "unique" => Some("http://www.ebay.com/unique")
          case _ => None
        }
      }

      override def name: String = "unique"
    })
    RoutingRegistry.routingDefinitions.length should be (2)
    RoutingRegistry.route("abcService") should not be (None)
    RoutingRegistry.route("abcService").get.name should be ("localhost")
    RoutingRegistry.route("unique") should not be (None)
    RoutingRegistry.route("unique").get.name should be ("unique")
    RoutingRegistry.resolve("abcService") should be (Some("http://localhost:8080/abcService"))
    RoutingRegistry.resolve("unique") should be (Some("http://www.ebay.com/unique"))
  }

  "unregister RoutingDefinition" should "have the correct behaviour" in {
    RoutingRegistry.register(new RoutingDefinition {
      override def resolve(svcName: String, env: Option[String]): Option[String] = {
        svcName match {
          case "unique" => Some("http://www.ebay.com/unique")
          case _ => None
        }
      }

      override def name: String = "unique"
    })
    RoutingRegistry.register(new LocalhostRouting)

    RoutingRegistry.routingDefinitions.length should be (2)
    RoutingRegistry.routingDefinitions.head.isInstanceOf[LocalhostRouting] should be (true)
    RoutingRegistry.resolve("unique") should be (Some("http://localhost:8080/unique"))
    RoutingRegistry.unregister("localhost")
    RoutingRegistry.routingDefinitions.length should be (1)
    RoutingRegistry.resolve("unique") should be (Some("http://www.ebay.com/unique"))
  }
}
