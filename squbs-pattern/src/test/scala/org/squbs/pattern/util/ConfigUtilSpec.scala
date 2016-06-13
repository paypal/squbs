/*
 *  Copyright 2015 PayPal
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.squbs.pattern.util

import com.typesafe.config.{Config, ConfigException, ConfigFactory, ConfigMemorySize}
import org.scalatest.{FunSpecLike, Matchers}

import scala.concurrent.duration._

class ConfigUtilSpec extends FunSpecLike with Matchers {

  val testConfig =
    """
      |testConfig {
      |  str = foo
      |  str-list = [ foo, bar ]
      |  int-num = 10
      |  bool-val = true
      |  conf = {
      |    bool-val2 = false
      |    num2 = 20
      |  }
      |  conf-list = [
      |    {
      |      bool-val3 = true
      |      int-val3 = 30
      |    }
      |    {
      |      bool val4 = false
      |      int-val4 = 40
      |    }
      |  ]
      |  timeout = 20s
      |  mem-size = 4m
      |}
    """.stripMargin

  val config = ConfigFactory.parseString(testConfig)

  import ConfigUtil._

  describe ("ConfigUtil") {

    it ("should read some existing string config") {
      config.getOptionalString("testConfig.str") should be (Some("foo"))
    }

    it ("should read some existing string config by \"getOption\"") {
      config.getOption[String]("testConfig.str") should be (Some("foo"))
    }

    it ("should get None for non-existing string config") {
      config.getOptionalString("str") should be (None)
    }

    it ("should get None for non-existing string config by \"getOption\"") {
      config.getOption[String]("str") should be (None)
    }

    it ("should read some existing string list config") {
      config.getOptionalStringList("testConfig.str-list") should be (Some(Seq("foo", "bar")))
    }

    it ("should read some existing string list config by \"getOption\"") {
      config.getOption[Seq[String]]("testConfig.str-list") should be (Some(Seq("foo", "bar")))
    }

    it ("should get None for non-existing string list config") {
      config.getOptionalStringList("str-list") should be (None)
    }

    it ("should get None for non-existing string list config by \"getOption\"") {
      config.getOption[Seq[String]]("str-list") should be (None)
    }

    it ("should read some existing int config") {
      config.getOptionalInt("testConfig.int-num") should be (Some(10))
    }

    it ("should read some existing int config by \"getOption\"") {
      config.getOption[Int]("testConfig.int-num") should be (Some(10))
    }

    it ("should get None for non-existing int config") {
      config.getOptionalInt("int-num") should be (None)
    }

    it ("should get None for non-existing int config by \"getOption\"") {
      config.getOption[Int]("int-num") should be (None)
    }

    it ("should read some existing boolean config") {
      config.getOptionalBoolean("testConfig.bool-val") should be (Some(true))
    }

    it ("should read some existing boolean config by \"getOption\"") {
      config.getOption[Boolean]("testConfig.bool-val") should be (Some(true))
    }

    it ("should get None for non-existing boolean config") {
      config.getOptionalBoolean("bool-val") should be (None)
    }

    it ("should get None for non-existing boolean config by \"getOption\"") {
      config.getOption[Boolean]("bool-val") should be (None)
    }

    it ("should read some existing config") {
      config.getOptionalConfig("testConfig.conf") flatMap { _.getOptionalInt("num2")} should be (Some(20))
    }

    it ("should read some existing config by \"getOption\"") {
      config.getOption[Config]("testConfig.conf") flatMap { _.getOptionalInt("num2")} should be (Some(20))
    }

    it ("should get None for non-existing config") {
      config.getOptionalConfig("conf") should be (None)
    }

    it ("should get None for non-existing config by \"getOption\"") {
      config.getOption[Config]("conf") should be (None)
    }

    it ("should read some existing config list") {
      config.getOptionalConfigList("testConfig.conf-list").get should have size 2
    }

    it ("should read some existing config list by \"getOption\"") {
      config.getOption[Seq[Config]]("testConfig.conf-list").get should have size 2
    }

    it ("should get None for non-existing config list") {
      config.getOptionalConfigList("conf-list") should be (None)
    }

    it ("should get None for non-existing config list \"getOption\"") {
      config.getOption[Seq[Config]]("conf-list") should be (None)
    }

		it ("should get duration for existing time") {
			config.getOptionalDuration("testConfig.timeout").get shouldEqual Duration.create(20, SECONDS)
		}

    it ("should get duration for existing time by \"getOption\"") {
      config.getOption[FiniteDuration]("testConfig.timeout").get shouldEqual Duration.create(20, SECONDS)
    }

		it ("should get None for non-existing duration") {
			config.getOptionalDuration("non-timeout") shouldBe None
		}

    it ("should get None for non-existing duration by \"getOption\"") {
      config.getOption[FiniteDuration]("non-timeout") shouldBe None
    }

    it ("should get a proper memory size") {
      config.getOptionalMemorySize("testConfig.mem-size") map (_.toBytes) shouldBe Some(4L * 1024 * 1024)
    }

    it ("should get a proper memory size by \"getOption\"") {
      config.getOption[ConfigMemorySize]("testConfig.mem-size") map (_.toBytes) shouldBe Some(4L * 1024 * 1024)
    }

    it ("should get None for non-existing memory size") {
      config.getOptionalMemorySize("mem-size") shouldBe None
    }

    it ("should get None for non-existing memory size by \"getOption\"") {
      config.getOption[ConfigMemorySize]("mem-size") shouldBe None
    }

    it ("should get default value for non-existing config string") {
      config.get("str", "default string") shouldBe "default string"
    }

    it ("should get config value for existing config string") {
      config.get[String]("testConfig.str", "default string") shouldBe "foo"
    }

    it ("should throw ConfigMissing exception for non existing config string") {
      intercept[ConfigException.Missing](
        config.get[String]("str")
      )
    }

    it ("should throw IllegalArgumentException for unexpected type of existing config value") {
      intercept[IllegalArgumentException](
        config.get[Unit]("testConfig.str")
      )
    }

    it ("should throw WrongType exception for incorrect type of existing config value") {
      intercept[ConfigException.WrongType](
        config.get[Int]("testConfig.str")
      )
    }

    it ("should get provide at least one IPv$ address for any host") {
      ipv4 should fullyMatch regex """\d+\.\d+\.\d+\.\d+"""
      println(ipv4)
    }
  }
}
