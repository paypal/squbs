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

    ProxySettings(config) should be(None)

    config = ConfigFactory.parseString(
      """
        |squbs.proxy {
        |
        |
        |}
        |
      """.stripMargin)

    ProxySettings(config) should be(None)
  }


  "bad config" should "work" in {

    var config = ConfigFactory.parseString(
      """
        |squbs.proxy {
        |  aaa = bbb
        |}
        |
      """.stripMargin)

    ProxySettings(config) should be(None)

    config = ConfigFactory.parseString(
      """
        |squbs.proxy {
        |  aaa = {
        |  }
        |}
        |
      """.stripMargin)

    ProxySettings(config) should be(None)

    config = ConfigFactory.parseString(
      """
        |squbs.proxy {
        |  aaa = {
        |     processor = org.squbs.proxy.DummyAny
        |  }
        |}
        |
      """.stripMargin)

    ProxySettings(config) should be(None)

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
    ps should not be (None)

    val proxySettings = ps.get
    proxySettings.default should not be (None)

    proxySettings.proxies.size should be(8)

    val proxy1 = proxySettings.find("MyProxy1")
    proxy1 should be(proxySettings.find("bbb"))
    proxy1.get.settings should not be (None)
    proxy1.get.processorFactory shouldBe a[DummyServiceProxyProcessorForActor]

    val proxy2 = proxySettings.find("MyProxy2")
    proxy2 should be(proxySettings.find("aaa"))
    proxy2 should be(proxySettings.find("fff"))
    proxy2.get.settings should be (None)
    proxy2.get.processorFactory shouldBe a[DummyServiceProxyProcessorForRoute]

    val default = proxySettings.find("default")
    default should be(proxySettings.find("ccc"))
    default should be(proxySettings.find("ddd"))
    default should be(proxySettings.default)
    default.get.name should be("default")
    default.get.settings should be(None)
    default.get.processorFactory shouldBe a[DummyPipedServiceProxyProcessorFactoryForActor]
  }

}

class DummyAny

class DummyProcessor extends ServiceProxyProcessorFactory {
  def create(settings: Option[Config])(implicit context: ActorContext): ServiceProxyProcessor = {
    return null
  }
}
