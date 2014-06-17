package org.squbs.hc.routing

import org.scalatest.{BeforeAndAfterEach, BeforeAndAfter, Matchers, FlatSpec}
import org.squbs.hc.HttpClientException

/**
 * Created by hakuang on 5/22/2014.
 */
class RoutingTest extends FlatSpec with Matchers with BeforeAndAfterEach{

  override def beforeEach() {
    RoutingRegistry.clear
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
    RoutingRegistry.get.length should be (1)
    RoutingRegistry.get.head.isInstanceOf[LocalhostRouting] should be (true)
  }

  "localhost routing" should "be return to the correct value" in {
    RoutingRegistry.register(new LocalhostRouting)
    RoutingRegistry.routing("abcService") should not be (None)
    RoutingRegistry.routing("abcService").get.name should be ("localhost")
    RoutingRegistry.routing("abcService").get.resolve("abcService") should be (Some("http://localhost:8080/abcService"))
  }

  "localhost routing" should "be throw out HttpClientException if env isn't Dev" in {
    a[HttpClientException] should be thrownBy {
      RoutingRegistry.register(new LocalhostRouting)
      RoutingRegistry.routing("abcService", Some("qa"))
    }
  }

  "localhost routing" should "be return to the correct value if env is Dev" in {
    RoutingRegistry.register(new LocalhostRouting)
    RoutingRegistry.routing("abcService", Some("dev")) should not be (None)
    RoutingRegistry.routing("abcService", Some("dev")).get.name should be ("localhost")
    RoutingRegistry.resolve("abcService", Some("dev")) should be (Some("http://localhost:8080/abcService"))
  }

  "Latter registry RoutingDefinition" should "have high priority" in {
    RoutingRegistry.register(new LocalhostRouting)
    RoutingRegistry.register(new RoutingDefinition {
      override def resolve(svcName: String, env: Option[String]): Option[String] = Some("http://localhost:8080/override")

      override def name: String = "override"
    })
    RoutingRegistry.get.length should be (2)
    RoutingRegistry.get.head.isInstanceOf[LocalhostRouting] should be (false)
    RoutingRegistry.get.head.asInstanceOf[RoutingDefinition].name should be ("override")
    RoutingRegistry.routing("abcService") should not be (None)
    RoutingRegistry.routing("abcService").get.name should be ("override")
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
    RoutingRegistry.get.length should be (2)
    RoutingRegistry.routing("abcService") should not be (None)
    RoutingRegistry.routing("abcService").get.name should be ("localhost")
    RoutingRegistry.routing("unique") should not be (None)
    RoutingRegistry.routing("unique").get.name should be ("unique")
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

    RoutingRegistry.get.length should be (2)
    RoutingRegistry.get.head.isInstanceOf[LocalhostRouting] should be (true)
    RoutingRegistry.resolve("unique") should be (Some("http://localhost:8080/unique"))
    RoutingRegistry.unregister("localhost")
    RoutingRegistry.get.length should be (1)
    RoutingRegistry.resolve("unique") should be (Some("http://www.ebay.com/unique"))
  }
}
