package org.squbs.proxy

import com.typesafe.config.ConfigException.Missing
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpecLike, Matchers}

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


    the[Missing] thrownBy {
      ProxySettings(config)
    } should have message "No configuration setting found for key 'processorFactory'"

    config = ConfigFactory.parseString(
      """
        |squbs.proxy {
        |  aaa = {
        |     processorFactory = org.squbs.proxy.DummyAny
        |  }
        |}
        |
      """.stripMargin)

    ProxySettings(config).default shouldBe None
    ProxySettings(config).proxies shouldBe Map("aaa" -> ProxySetup("aaa", "org.squbs.proxy.DummyAny", None))

    config = ConfigFactory.parseString(
      """
        |squbs.proxy {
        |  MyProxy1 {
        |    aliases = [aaa,bbb]
        |    processorFactory = org.squbs.f1
        |  }
        |  MyProxy2 {
        |    aliases = [aaa,ccc]
        |    processorFactory = org.squbs.f1
        |  }
        |}
      """.stripMargin)

    (the[IllegalArgumentException] thrownBy {
      ProxySettings(config)
    }).getMessage should include ("Proxy name is already used by proxy: MyProxy")
  }

  "legacy config" should "work" in {

    val config = ConfigFactory.parseString(
      """
        |squbs.proxy {
        |  MyProxy1 {
        |    aliases = [aaa,bbb]
        |    processorFactory = org.squbs.proxy.serviceproxyactor.DummyServiceProxyProcessorForActor
        |    settings = {
        |    }
        |  }
        |  MyProxy2 {
        |    aliases = [eee,fff]
        |    processorFactory = org.squbs.proxy.serviceproxyroute.DummyServiceProxyProcessorForRoute
        |  }
        |  default-proxy {
        |    aliases = [ccc,ddd]
        |    processorFactory = org.squbs.proxy.pipedserviceproxyactor.DummyPipedServiceProxyProcessorFactoryForActor
        |  }
        |}
      """.stripMargin)

    val ps = ProxySettings(config)

    ps.default should not be None
    ps.proxies.size should be(9)

    val proxy1 = ps.find("MyProxy1")
    proxy1 shouldBe ps.find("bbb")
    proxy1 shouldBe ps.find("aaa")
    proxy1.get.settings should not be None
    proxy1.get.factoryClazz shouldBe "org.squbs.proxy.serviceproxyactor.DummyServiceProxyProcessorForActor"

    val proxy2 = ps.find("MyProxy2")
    proxy2 should be(ps.find("eee"))
    proxy2 should be(ps.find("fff"))
    proxy2.get.settings shouldBe None
    proxy2.get.factoryClazz shouldBe "org.squbs.proxy.serviceproxyroute.DummyServiceProxyProcessorForRoute"

    val default = ps.find("default-proxy")
    default shouldBe ps.find("ccc")
    default shouldBe ps.find("ddd")
    default shouldBe ps.default
    default.get.name shouldBe "default-proxy"
    default.get.settings shouldBe None
    default.get.factoryClazz shouldBe "org.squbs.proxy.pipedserviceproxyactor.DummyPipedServiceProxyProcessorFactoryForActor"
  }


  "new config" should "work" in {

    val config = ConfigFactory.parseString(
      """
        |MyProxy1 {
        |    type = squbs.proxy
        |    aliases = [aaa,bbb]
        |    processorFactory = org.squbs.proxy.serviceproxyactor.DummyServiceProxyProcessorForActor
        |    settings = {
        |    }
        |}
        |
        |MyProxy2 {
        |    type = squbs.proxy
        |    aliases = [eee,fff]
        |    processorFactory = org.squbs.proxy.serviceproxyroute.DummyServiceProxyProcessorForRoute
        |}
        |
        |default-proxy {
        |    type = squbs.proxy
        |    aliases = [ccc,ddd]
        |    processorFactory = org.squbs.proxy.pipedserviceproxyactor.DummyPipedServiceProxyProcessorFactoryForActor
        |}

      """.stripMargin)

    val ps = ProxySettings(config)

    ps.default should not be None
    ps.proxies.size should be(9)

    val proxy1 = ps.find("MyProxy1")
    proxy1 shouldBe ps.find("bbb")
    proxy1 shouldBe ps.find("aaa")
    proxy1.get.settings should not be None
    proxy1.get.factoryClazz shouldBe "org.squbs.proxy.serviceproxyactor.DummyServiceProxyProcessorForActor"

    val proxy2 = ps.find("MyProxy2")
    proxy2 should be(ps.find("eee"))
    proxy2 should be(ps.find("fff"))
    proxy2.get.settings shouldBe None
    proxy2.get.factoryClazz shouldBe "org.squbs.proxy.serviceproxyroute.DummyServiceProxyProcessorForRoute"

    val default = ps.find("default-proxy")
    default shouldBe ps.find("ccc")
    default shouldBe ps.find("ddd")
    default shouldBe ps.default
    default.get.name shouldBe "default-proxy"
    default.get.settings shouldBe None
    default.get.factoryClazz shouldBe "org.squbs.proxy.pipedserviceproxyactor.DummyPipedServiceProxyProcessorFactoryForActor"
  }


  "merged config" should "work" in {

    val config = ConfigFactory.parseString(
      """
        |squbs.proxy {
        |  MyProxy1 {
        |    aliases = [aaa,bbb]
        |    processorFactory = org.squbs.proxy.serviceproxyactor.DummyServiceProxyProcessorForActor
        |    settings = {
        |    }
        |  }
        |  default-proxy {
        |    aliases = [ccc,ddd]
        |    processorFactory = org.squbs.proxy.pipedserviceproxyactor.DummyPipedServiceProxyProcessorFactoryForActor
        |  }
        |}
        |
        |MyProxy2 {
        |    type = squbs.proxy
        |    aliases = [eee,fff]
        |    processorFactory = org.squbs.proxy.serviceproxyroute.DummyServiceProxyProcessorForRoute
        |}
      """.stripMargin)

    val ps = ProxySettings(config)

    ps.default should not be None
    ps.proxies.size should be(9)

    val proxy1 = ps.find("MyProxy1")
    proxy1 shouldBe ps.find("bbb")
    proxy1 shouldBe ps.find("aaa")
    proxy1.get.settings should not be None
    proxy1.get.factoryClazz shouldBe "org.squbs.proxy.serviceproxyactor.DummyServiceProxyProcessorForActor"

    val proxy2 = ps.find("MyProxy2")
    proxy2 should be(ps.find("eee"))
    proxy2 should be(ps.find("fff"))
    proxy2.get.settings shouldBe None
    proxy2.get.factoryClazz shouldBe "org.squbs.proxy.serviceproxyroute.DummyServiceProxyProcessorForRoute"

    val default = ps.find("default-proxy")
    default shouldBe ps.find("ccc")
    default shouldBe ps.find("ddd")
    default shouldBe ps.default
    default.get.name shouldBe "default-proxy"
    default.get.settings shouldBe None
    default.get.factoryClazz shouldBe "org.squbs.proxy.pipedserviceproxyactor.DummyPipedServiceProxyProcessorFactoryForActor"
  }
}
