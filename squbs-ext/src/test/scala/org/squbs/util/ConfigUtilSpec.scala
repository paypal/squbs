/*
 *  Copyright 2017 PayPal
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

package org.squbs.util

import com.typesafe.config.{Config, ConfigException, ConfigFactory, ConfigMemorySize}
import org.scalatest.funspec.AnyFunSpecLike
import org.scalatest.matchers.should.Matchers

import java.util.regex.PatternSyntaxException
import scala.concurrent.duration._
import scala.util.Success
import scala.util.matching.Regex

class ConfigUtilSpec extends AnyFunSpecLike with Matchers {

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
      |  timeout-inf = Inf
      |  timeout-list-with-inf = [ 10ms, 5m, Inf, 2h ]
      |  mem-size = 4m
      |  correct-regex = "[0-9]"
      |  incorrect-regex = "("
      |}
    """.stripMargin

  val config = ConfigFactory.parseString(testConfig)

  import ConfigUtil._

  describe ("ConfigUtil") {

    it ("should read some existing string config") {
      config.getOptionalString("testConfig.str") should be (Some("foo"))
    }

    it("should read some existing string config by \"getTry\"") {
      config.getTry[String]("testConfig.str") should be(Success("foo"))
    }
    
    it ("should get None for non-existing string config") {
      config.getOptionalString("str") should be (None)
    }

    it("should get Failure(ConfigException.Missing) for non-existing string config by \"getTry\"") {
      config.getTry[String]("str").failed.get shouldBe a [ConfigException.Missing]
    }

    it ("should read some existing string list config") {
      config.getOptionalStringList("testConfig.str-list") should be (Some(Seq("foo", "bar")))
    }

    it("should read some existing string list config by \"getTry\"") {
      config.getTry[Seq[String]]("testConfig.str-list") should be(Success(Seq("foo", "bar")))
    }
    
    it ("should get None for non-existing string list config") {
      config.getOptionalStringList("str-list") should be (None)
    }

    it("should get Failure(ConfigException.Missing) for non-existing string list config by \"getTry\"") {
      config.getTry[Seq[String]]("str-list").failed.get shouldBe a [ConfigException.Missing]
    }

    it ("should read some existing int config") {
      config.getOptionalInt("testConfig.int-num") should be (Some(10))
    }

    it("should read some existing int config by \"getTry\"") {
      config.getTry[Int]("testConfig.int-num") should be(Success(10))
    }
    
    it ("should get None for non-existing int config") {
      config.getOptionalInt("int-num") should be (None)
    }

    it("should get Failure(ConfigException.Missing) for non-existing int config by \"getTry\"") {
      config.getTry[Int]("int-num").failed.get shouldBe a [ConfigException.Missing]
    }
    
    it ("should read some existing boolean config") {
      config.getOptionalBoolean("testConfig.bool-val") should be (Some(true))
    }

    it("should read some existing boolean config by \"getTry\"") {
      config.getTry[Boolean]("testConfig.bool-val") should be(Success(true))
    }

    it ("should get None for non-existing boolean config") {
      config.getOptionalBoolean("bool-val") should be (None)
    }

    it("should get Failure(ConfigException.Missing) for non-existing boolean config by \"getTry\"") {
      config.getTry[Boolean]("bool-val").failed.get shouldBe a [ConfigException.Missing]
    }

    it ("should read some existing config") {
      config.getOptionalConfig("testConfig.conf") flatMap { _.getOptionalInt("num2")} should be (Some(20))
    }

    it("should read some existing config by \"getTry\"") {
      config.getTry[Config]("testConfig.conf") map { _.getOptionalInt("num2") } should be(Success(Some(20)))
    }

    it("should get None for non-existing config ") {
      config.getOptionalConfig("conf") should be (None)
    }

    it("should get Failure(ConfigException.Missing) for non-existing config by \"getTry\"") {
      config.getTry[Config]("conf").failed.get shouldBe a [ConfigException.Missing]
    }

    it ("should read some existing config list") {
      config.getOptionalConfigList("testConfig.conf-list").get should have size 2
    }

    it("should read some existing config list by \"getTry\"") {
      config.getTry[Seq[Config]]("testConfig.conf-list").get should have size 2
    }

    it ("should get None for non-existing config list") {
      config.getOptionalConfigList("conf-list") should be (None)
    }

    it("should get Failure(ConfigException.Missing) for non-existing config list \"getTry\"") {
      config.getTry[Seq[Config]]("conf-list").failed.get shouldBe a [ConfigException.Missing]
    }

    it("should get duration for existing time") {
      config.getOptionalDuration("testConfig.timeout").get shouldEqual Duration.create(20, SECONDS)
    }

    it("should get None for non-existing duration") {
      config.getOptionalDuration("non-timeout") shouldBe None
    }

    it("should get duration for existing finite duration by \"getTry\"") {
      config.getTry[Duration]("testConfig.timeout").get shouldEqual Duration.create(20, SECONDS)
    }

    it("should get Failure(ConfigException.Missing) for non-existing duration by \"getTry\"") {
      config.getTry[Duration]("non-timeout").failed.get shouldBe a [ConfigException.Missing]
    }

    it("should get duration for infinite duration by \"getTry\"") {
      config.getTry[Duration]("testConfig.timeout-inf") shouldBe Success(Duration.Inf)
    }

    it("should get duration list with some infinite elements by \"getTry\"") {
      config.getTry[Seq[Duration]]("testConfig.timeout-list-with-inf") shouldBe
        Success(Seq(10.millis, 5.minutes, Duration.Inf, 2.hours))
    }

    it("should get finite duration for existing finite duration by \"getTry\"") {
      config.getTry[FiniteDuration]("testConfig.timeout").get shouldEqual Duration.create(20, SECONDS)
    }

    it("should get Failure(ConfigException.Missing) for non-existing finite duration by \"getTry\"") {
      config.getTry[FiniteDuration]("non-timeout").failed.get shouldBe a [ConfigException.Missing]
    }

    it("should get Failure(ConfigException.WrongType) for infinite duration by \"getTry\"") {
      the [ConfigException.WrongType] thrownBy
        config.getTry[FiniteDuration]("testConfig.timeout-inf").get should have message
      s"${config.origin.description}: Path: testConfig.timeout-inf, value Inf is not the correct type"
    }

    it ("should get a proper Regex for existing value") {
      config.getOptionalPattern("testConfig.correct-regex").get.pattern.pattern() should be ("""[0-9]""")
    }

    it ("should get a proper Regex for existing value by \"getTry\"") {
      config.getTry[Regex]("testConfig.correct-regex").get.pattern.pattern() should be ("""[0-9]""")
    }

    it ("should get None for non-existing regex value") {
      config.getOptionalPattern("correct-regex") shouldBe None
    }

    it ("should get Failure(ConfigException.Missing) for non-existing regex value by \"getOption\"") {
      config.getTry[Regex]("correct-regex").failed.get shouldBe a [ConfigException.Missing]
    }

    it ("should get None for existing incorrect Regex value") {
      config.getOptionalPattern("testConfig.incorrect-regex") shouldBe None
    }

    it ("should get Failure(PatternSyntaxException) for existing incorrect Regex value by \"getOption\"") {
      config.getTry[Regex]("testConfig.incorrect-regex").failed.get shouldBe a [PatternSyntaxException]
    }
    
    it ("should get a proper memory size") {
      config.getOptionalMemorySize("testConfig.mem-size") map (_.toBytes) shouldBe Some(4L * 1024 * 1024)
    }

    it ("should get a proper memory size by \"getTry\"") {
      config.getTry[ConfigMemorySize]("testConfig.mem-size") map (_.toBytes) shouldBe Success(4L * 1024 * 1024)
    }
    
    it ("should get None for non-existing memory size") {
      config.getOptionalMemorySize("mem-size") shouldBe None
    }

    it("should get Failure(ConfigException.Missing) for non-existing memory size by \"getTry\"") {
      config.getTry[ConfigMemorySize]("mem-size").failed.get shouldBe a [ConfigException.Missing]
    }

    it("should get Some value for existing config value by \"getOption\"") {
      config.getOption[String]("testConfig.str") shouldBe Some("foo")
    }

    it("should get None for non-existing config value by \"getOption\"") {
      config.getOption[String]("str") shouldBe None
    }

    it("should get None for config value with incorrect type by \"getOption\"") {
      config.getOption[Int]("testConfig.str") shouldBe None
    }

    it("should get None for config value with failed class cast by \"getOption\"") {
      config.getOption[FiniteDuration]("testConfig.timeout-inf") shouldBe None
    }

    it("should fail to compile for calling \"getOption\" with unexpected type") {
      "config.getOption[Unit](\"testConfig.str\")" shouldNot compile
    }

    it("should get default value for non-existing config string") {
      config.get[String]("str", "default string") shouldBe "default string"
    }

    it("should get config value for existing config string") {
      config.get[String]("testConfig.str", "default string") shouldBe "foo"
    }

    it("should throw ConfigException.Missing exception for non existing config string") {
      intercept[ConfigException.Missing](
        config.get[String]("str")
      )
    }

    it("unexpected type of existing config value should not compile or type check") {
      "config.get[Unit](\"testConfig.str\")" shouldNot typeCheck
    }

    it("should throw ConfigException.WrongType exception for incorrect type of existing config value") {
      intercept[ConfigException.WrongType](
        config.get[Int]("testConfig.str")
      )
    }

    it("should get Failure(ConfigException.WrongType) exception for incorrect type of existing config value") {
      config.getTry[Int]("testConfig.str").failed.get shouldBe a [ConfigException.WrongType]
    }

    it ("should get provide at least one IPv4 address for any host") {
      ipv4 should fullyMatch regex """\d+\.\d+\.\d+\.\d+"""
      println(ipv4)
    }
  }
}
