package org.squbs.proxy

import org.scalatest.{Matchers, FlatSpecLike}
import com.typesafe.config.{Config, ConfigFactory}
import org.squbs.unicomplex.ProxySettings
import org.squbs.proxy.pipedserviceproxyactor.DummyPipedServiceProxyProcessorFactoryForActor
import org.squbs.proxy.serviceproxyactor.DummyServiceProxyProcessorForActor
import org.squbs.proxy.serviceproxyroute.DummyServiceProxyProcessorForRoute
import akka.actor.ActorContext

/**
 * Created by lma on 15-2-2.
 */
class ProxySettingsTest extends FlatSpecLike with Matchers {

  "empty config" should "work" in {

    var config = ConfigFactory.parseString(
      """
        |
        |
      """.stripMargin)

    ProxySettings(config).default shouldBe None

    config = ConfigFactory.parseString(
      """
        |squbs.proxy {
        |
        |
        |}
        |
      """.stripMargin)

    ProxySettings(config).default shouldBe None
  }


  "bad config" should "work" in {

    var config = ConfigFactory.parseString(
      """
        |squbs.proxy {
        |  aaa = bbb
        |}
        |
      """.stripMargin)

    ProxySettings(config).default shouldBe None

    config = ConfigFactory.parseString(
      """
        |squbs.proxy {
        |  aaa = {
        |  }
        |}
        |
      """.stripMargin)

    ProxySettings(config).default shouldBe None

    config = ConfigFactory.parseString(
      """
        |squbs.proxy {
        |  aaa = {
        |     processor = org.squbs.proxy.DummyAny
        |  }
        |}
        |
      """.stripMargin)

    ProxySettings(config).default shouldBe None
	  ProxySettings(config).proxies shouldBe Map.empty
  }

  "config" should "work" in {

    val config = ConfigFactory.parseString(
      """
        |squbs.proxy {
        |  MyProxy1 {
        |    aliases = [aaa,bbb]
        |    processor = org.squbs.proxy.serviceproxyactor.DummyServiceProxyProcessorForActor
        |    settings = {
        |    }
        |  }
        |  MyProxy2 {
        |    aliases = [aaa,fff]
        |    processor = org.squbs.proxy.serviceproxyroute.DummyServiceProxyProcessorForRoute
        |  }
        |  default {
        |    aliases = [ccc,ddd]
        |    processor = org.squbs.proxy.pipedserviceproxyactor.DummyPipedServiceProxyProcessorFactoryForActor
        |  }
        |}
      """.stripMargin)

    val ps = ProxySettings(config)

    ps.default should not be None
	  ps.proxies.size should be(8)

    val proxy1 = ps.find("MyProxy1")
    proxy1 shouldBe ps.find("bbb")
    proxy1.get.settings should not be None
    proxy1.get.processorFactory shouldBe a[DummyServiceProxyProcessorForActor]

    val proxy2 = ps.find("MyProxy2")
    proxy2 should be(ps.find("aaa"))
    proxy2 should be(ps.find("fff"))
    proxy2.get.settings shouldBe None
    proxy2.get.processorFactory shouldBe a[DummyServiceProxyProcessorForRoute]

    val default = ps.find("default")
    default shouldBe ps.find("ccc")
    default shouldBe ps.find("ddd")
    default shouldBe ps.default
    default.get.name shouldBe "default"
    default.get.settings shouldBe None
    default.get.processorFactory shouldBe a[DummyPipedServiceProxyProcessorFactoryForActor]
  }
}

class DummyAny

class DummyProcessor extends ServiceProxyProcessorFactory {
  def create(settings: Option[Config])(implicit context: ActorContext): ServiceProxyProcessor = {
    return null
  }
}
