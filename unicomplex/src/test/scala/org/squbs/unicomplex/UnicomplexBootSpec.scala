/*
 * Copyright (c) 2014 eBay, Inc.
 * All rights reserved.
 *
 * Contributors:
 * asucharitakul
 */
package org.squbs.unicomplex

import org.scalatest.{Matchers, FunSpecLike}
import org.squbs.unicomplex.UnicomplexBoot._
import com.typesafe.config.{ConfigException, ConfigFactory}
import java.io.{File, PrintWriter}

class UnicomplexBootSpec extends FunSpecLike with Matchers {

  describe ("The UnicomplexBootstrap") {

    it ("Should handle non-duplication in cube short names") {
      val initInfoList = Seq(
        InitInfo("don't care", "com.foo.foobar.bar", "bar", "1.0.0", Seq.empty, StartupType.ACTORS),
        InitInfo("don't care", "com.foo.baz.foo", "foo", "1.0.0", Seq.empty, StartupType.SERVICES),
        InitInfo("don't care", "com.foo.baz.foobar", "foobar", "1.0.0", Seq.empty, StartupType.ACTORS)
      )

      val newList = resolveAliasConflicts(initInfoList)
      newList should be theSameInstanceAs initInfoList

    }

    it ("Should handle duplication in cube short names") {
      val initInfoList = Seq(
        InitInfo("don't care", "com.foo.foobar.bar", "bar", "1.0.0", Seq.empty, StartupType.ACTORS),
        InitInfo("don't care", "com.foo.baz.bar", "bar", "1.0.0", Seq.empty, StartupType.SERVICES),
        InitInfo("don't care", "com.foo.bar.bar", "bar", "1.0.0", Seq.empty, StartupType.ACTORS)
      )
      val newList = resolveAliasConflicts(initInfoList)
      newList should not be theSameInstanceAs (initInfoList)
      val newAliases = newList map (_.alias)
      val refAliases = Seq("foobar.bar", "baz.bar", "bar.bar")
      newAliases should be (refAliases)
    }

    it ("Should handle some duplication in cube names") {
      val initInfoList = Seq(
        InitInfo("don't care", "com.bar.baz.bar", "bar", "1.0.0", Seq.empty, StartupType.ACTORS),
        InitInfo("don't care", "com.foo.baz.bar", "bar", "1.0.0", Seq.empty, StartupType.SERVICES),
        InitInfo("don't care", "com.foo.bar.bar", "bar", "1.0.0", Seq.empty, StartupType.ACTORS)
      )
      val newList = resolveAliasConflicts(initInfoList)
      newList should not be theSameInstanceAs (initInfoList)
      val newAliases = newList map (_.alias)
      val refAliases = Seq("bar.baz.bar", "foo.baz.bar", "bar.bar")
      newAliases should be (refAliases)
    }

    it ("Should load addOnConfig if provided") {
      import scala.collection.JavaConversions._
      val addOnConfig = ConfigFactory.parseMap(Map(
        "squbs.testAttribute" -> "foobar"
      ))
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
  }
}
