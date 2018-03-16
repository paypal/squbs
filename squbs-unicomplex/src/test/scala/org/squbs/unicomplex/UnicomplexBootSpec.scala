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

package org.squbs.unicomplex

import org.scalatest.{Matchers, FunSpecLike}
import org.squbs.unicomplex.UnicomplexBoot._
import com.typesafe.config.{ConfigException, ConfigFactory}
import java.io.{File, PrintWriter}

import scala.util.Try
import scala.collection.JavaConverters._

class UnicomplexBootSpec extends FunSpecLike with Matchers {

  describe ("The UnicomplexBootstrap") {

    it ("Should handle non-duplication in cube short names") {
      val cubeList = Seq(
        CubeInit(Cube("bar", "com.foo.foobar.bar", "1.0.0", "don't care"), Map.empty),
        CubeInit(Cube("foo", "com.foo.baz.foo", "1.0.0", "don't care"), Map.empty),
        CubeInit(Cube("foobar", "com.foo.baz.foobar", "1.0.0", "don't care"), Map.empty)
      )

      val newList = resolveAliasConflicts(cubeList)
      newList should be theSameInstanceAs cubeList

    }

    it ("Should handle duplication in cube short names") {
      val cubeList = Seq(
        CubeInit(Cube("bar", "com.foo.foobar.bar", "1.0.0", "don't care"), Map.empty),
        CubeInit(Cube("bar", "com.foo.baz.bar", "1.0.0", "don't care"), Map.empty),
        CubeInit(Cube("bar", "com.foo.bar.bar", "1.0.0", "don't care"), Map.empty)
      )
      val newList = resolveAliasConflicts(cubeList)
      newList should not be theSameInstanceAs (cubeList)
      val newAliases = newList map (_.info.name)
      val refAliases = Seq("foobar.bar", "baz.bar", "bar.bar")
      newAliases should be (refAliases)
    }

    it ("Should handle some duplication in cube names") {
      val cubeList = Seq(
        CubeInit(Cube("bar", "com.bar.baz.bar", "1.0.0", "don't care"), Map.empty),
        CubeInit(Cube("bar", "com.foo.baz.bar", "1.0.0", "don't care"), Map.empty),
        CubeInit(Cube("bar", "com.foo.bar.bar", "1.0.0", "don't care"), Map.empty)
      )
      val newList = resolveAliasConflicts(cubeList)
      newList should not be theSameInstanceAs (cubeList)
      val newAliases = newList map (_.info.name)
      val refAliases = Seq("bar.baz.bar", "foo.baz.bar", "bar.bar")
      newAliases should be (refAliases)
    }

    it ("Should load addOnConfig if provided") {
      val addOnConfig = ConfigFactory.parseMap(Map(
        "squbs.testAttribute" -> "foobar"
      ).asJava)
      val config = getFullConfig(Some(addOnConfig))
      config.getString("squbs.actorsystem-name") should be ("squbs")
      config.getString("squbs.testAttribute") should be ("foobar")
    }

    it ("Should load config files of all supported formats from external config dir") {

      val configDir = new File("squbsconfig")
      val createdConfDir = configDir.mkdir()

      { // 1. Deal with no config file
        val config = getFullConfig(None)
          config.getString("squbs.actorsystem-name") should be ("squbs")
          an [ConfigException.Missing] should be thrownBy config.getString("squbs.testAttribute1")

      }
      { // 2. Deal with .conf
        val appConf =
          """
            |squbs {
            |  testAttribute1 = foobar1
            |}
          """.stripMargin
        val confFile = new File(configDir, "application.conf")
        val writer = new PrintWriter(confFile)
        writer.append(appConf)
        writer.close()

        val config = getFullConfig(None)
        config.getString("squbs.actorsystem-name") should be ("squbs")
        config.getString("squbs.testAttribute1") should be ("foobar1")
        confFile.delete()
      }
      { // 3. Deal with .json
        val appConf =
          """
            |{
            |  "squbs" : {
            |    "testAttribute2" : "foobar2"
            |  }
            |}
          """.stripMargin
        val confFile = new File(configDir, "application.json")
        val writer = new PrintWriter(confFile)
        writer.append(appConf)
        writer.close()

        val config = getFullConfig(None)
        config.getString("squbs.actorsystem-name") should be ("squbs")
        config.getString("squbs.testAttribute2") should be ("foobar2")
        confFile.delete()
      }
      { // 4. Deal with .properties
      val appConf =
        """
          |squbs.testAttribute3=foobar3
        """.stripMargin

        val confFile = new File(configDir, "application.properties")
        val writer = new PrintWriter(confFile)
        writer.append(appConf)
        writer.close()

        val config = getFullConfig(None)
        config.getString("squbs.actorsystem-name")should be ("squbs")
        config.getString("squbs.testAttribute3")should be ("foobar3")
        confFile.delete()
      }
      if (createdConfDir) configDir.deleteOnExit()
    }

    it ("Should find the configured listeners and their configurations") {
      val appConf =
        """
          |default-listener {
          |  type = squbs.listener
          |  aliases = [ foo-listener, bar-listener ]
          |  bind-address = "0.0.0.0"
          |  bind-port = 8080
          |  secure = false
          |  full-address = false
          |}
          |
          |secure-listener {
          |  type = squbs.listener
          |  aliases = [ foobar-listener, baz-listener ]
          |  bind-address = "0.0.0.0"
          |  bind-port = 8443
          |  secure = true
          |  full-address = false
          |  ssl-context = "org.my.SSLContext"
          |}
          |
          |blocking-dispatcher {
          |  # Dispatcher is the name of the event-based dispatcher
          |  type = Dispatcher
          |  # What kind of ExecutionService to use
          |  executor = "fork-join-executor"
          |}
          |
          |some-config {
          |  foo = bar
          |}
          |
          |some-other-config = foo
        """.stripMargin
      val config = ConfigFactory.parseString(appConf)
      val listeners = configuredListeners(config)
      listeners.size should be (2)
      listeners.keys should contain only ("default-listener", "secure-listener")
      listeners.toMap.apply("secure-listener").getInt("bind-port") should be (8443)
    }

    it ("Should find the active and missing listeners") {
      val routeDef1 =
        """
          |    class-name = org.minime.Svc1
          |    listeners = [
          |      secure-listener
          |    ]
        """.stripMargin
      val route1 = ConfigFactory.parseString(routeDef1)

      val routeDef2 =
        """
          |    class-name = org.minime.Svc2
          |    listeners = [
          |      secure2-listener
          |    ]
        """.stripMargin
      val route2 = ConfigFactory.parseString(routeDef2)

      val routeDef3 =
        """
          |    class-name = org.minime.Svc3
          |    listeners = [
          |      local-listener
          |    ]
        """.stripMargin
      val route3 = ConfigFactory.parseString(routeDef3)

      val appConfDef =
        """
          |default-listener {
          |  type = squbs.listener
          |  aliases = [ foo-listener, bar-listener ]
          |  bind-address = "0.0.0.0"
          |  bind-port = 8080
          |  secure = false
          |  full-address = false
          |}
          |
          |secure-listener {
          |  type = squbs.listener
          |  aliases = [ secure2-listener, baz-listener ]
          |  bind-address = "0.0.0.0"
          |  bind-port = 8443
          |  secure = true
          |  full-address = false
          |  ssl-context = "org.my.SSLContext"
          |}
        """.stripMargin
      val appConf = ConfigFactory.parseString(appConfDef)
      val cubeList = Seq(
        CubeInit(Cube("foo", "com.foo.bar", "1.0.0", "don't care"), Map(StartupType.SERVICES -> Seq(route1))),
        CubeInit(Cube("bar", "com.foo.bar", "1.0.0", "don't care"), Map(StartupType.SERVICES -> Seq(route2, route3))))

      val (activeAliases, activeListeners, missingListeners) = findListeners(appConf, cubeList)
      activeAliases.keys should contain only ("secure-listener", "secure2-listener")
      activeListeners.keys should contain only "secure-listener"
      missingListeners should contain only "local-listener"
    }

    it ("should merge the addOnConfig with original config") {
      val addOnConfig = ConfigFactory.parseMap(Map(
        "configTest" -> Boolean.box(true)
      ).asJava)
      val finalConfig = UnicomplexBoot.getFullConfig(Some(addOnConfig))
      Try(finalConfig.getConfig("squbs")).toOption should not be (None)
      finalConfig.getBoolean("configTest") should be (true)
    }

    it ("should resolve duplicates") {
      val s1 = Seq(("k1", "v1"), ("k2", "v2"), ("k3", "v3"), ("k2", "v3"), ("k1", "v3"), ("k1", "v2"))
      resolveDuplicates[String](s1, (k, ass, v) => ()) shouldBe Map("k1" -> "v1", "k2" -> "v2", "k3" -> "v3")
    }
  }
}
