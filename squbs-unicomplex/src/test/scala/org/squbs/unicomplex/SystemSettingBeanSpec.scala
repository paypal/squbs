/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the AUTHORS file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.squbs.unicomplex

import com.typesafe.config.{ConfigFactory}
import org.scalatest.{Matchers, FlatSpecLike}
import collection.JavaConversions._

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
        |  oneLevelList: ["v0", "v1", "v2"]
        |  listList: [
        |    ["v00", "v01", "v02"],
        |    ["v10", "v11"],
        |    ["v20", "v21"]
        |  ]
        |  listObject: [
        |    {
        |      k1: "v1"
        |    },
        |    {
        |      k2: "v2"
        |    }
        |  ]
        |}
      """.stripMargin)
    val bean = new SystemSettingBean(config)
    val settings = bean.getSystemSetting
    settings.length should be(19)
    settings.find(_.key.equals("root.str")).get.value should be("1")
    settings.find(_.key.equals("root.number")).get.value should be("2")
    settings.find(_.key.equals("root.nil")).get.value should be("null")
    settings.find(_.key.equals("root.bool")).get.value should be("true")
    settings.find(_.key.equals("root.map.k1")).get.value should be("v1")
    settings.find(_.key.equals("root.map.k2.k21")).get.value should be("v21")
    settings.find(_.key.equals("root.map.k2.k22[0]")).get.value should be("v220")
    settings.find(_.key.equals("root.oneLevelList[0]")).get.value should be("v0")
    settings.find(_.key.equals("root.oneLevelList[1]")).get.value should be("v1")
    settings.find(_.key.equals("root.oneLevelList[2]")).get.value should be("v2")
    settings.find(_.key.equals("root.listList[0][0]")).get.value should be("v00")
    settings.find(_.key.equals("root.listList[0][1]")).get.value should be("v01")
    settings.find(_.key.equals("root.listList[0][2]")).get.value should be("v02")
    settings.find(_.key.equals("root.listList[1][0]")).get.value should be("v10")
    settings.find(_.key.equals("root.listList[1][1]")).get.value should be("v11")
    settings.find(_.key.equals("root.listList[2][0]")).get.value should be("v20")
    settings.find(_.key.equals("root.listList[2][1]")).get.value should be("v21")
    settings.find(_.key.equals("root.listObject[0].k1")).get.value should be("v1")
    settings.find(_.key.equals("root.listObject[1].k2")).get.value should be("v2")
  }
}
