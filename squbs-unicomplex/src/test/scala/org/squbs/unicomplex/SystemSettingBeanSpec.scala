package org.squbs.unicomplex

import com.typesafe.config.{ConfigFactory}
import org.scalatest.{Matchers, FlatSpecLike}
import collection.JavaConversions._

/**
 * Created by miawang on 11/18/14.
 */
class SystemSettingBeanSpec extends FlatSpecLike with Matchers{

  "SystemSettingBean" should "read config correctly" in {
    val config = ConfigFactory.parseString(
      """
        |root {
        |  str: "1"
        |  number: 2
        |  bool: true
        |  nil: null
        |  map: {
        |    k1: "v1"
        |    k2: {
        |      k21: "v21"
        |      k22: [
        |        "v220"
        |      ]
        |    }
        |  }
        |  list: [
        |    "lv0",
        |    ["lv10", "lv11"]
        |    {
        |      l2k: "lv2k"
        |    }
        |  ]
        |}
      """.stripMargin)
    val bean = new SystemSettingBean(config)
    val settings = bean.getSystemSetting
    settings.length should be(11)
    settings.find(_.key.equals("root.str")).get.value should be("1")
    settings.find(_.key.equals("root.number")).get.value should be("2")
    settings.find(_.key.equals("root.nil")).get.value should be("null")
    settings.find(_.key.equals("root.bool")).get.value should be("true")
    settings.find(_.key.equals("root.map.k1")).get.value should be("v1")
    settings.find(_.key.equals("root.map.k2.k21")).get.value should be("v21")
    settings.find(_.key.equals("root.map.k2.k22[0]")).get.value should be("v220")
    settings.find(_.key.equals("root.list[0]")).get.value should be("lv0")
    settings.find(_.key.equals("root.list[1][0]")).get.value should be("lv10")
    settings.find(_.key.equals("root.list[1][1]")).get.value should be("lv11")
    settings.find(_.key.equals("root.list[2].l2k")).get.value should be("lv2k")
  }
}
