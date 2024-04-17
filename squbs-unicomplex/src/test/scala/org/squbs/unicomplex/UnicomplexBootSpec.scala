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

import org.apache.pekko.actor.ActorSystem
import com.typesafe.config.{ConfigException, ConfigFactory}
import org.scalatest.funspec.AnyFunSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.UnicomplexBoot._

import java.io.{File, PrintWriter}
import java.util.UUID
import scala.jdk.CollectionConverters._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class UnicomplexBootSpec extends AnyFunSpecLike with Matchers with MockitoSugar
{

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

  describe("Service infra startup timeout") {

    def useUnicomplex(
      infraStartTime: Duration,
      overallTimeout: Duration,
      listenerTimeout: Duration
    )(useFn: PartialFunction[Try[UnicomplexBoot], Unit]): Unit = {

      val serviceInfraTimeouts = Map(
        "timeout" -> overallTimeout.toString,
        "listener-timeout" -> listenerTimeout.toString
      )

      val props = Map(
        "squbs" -> Map(
          "actorsystem-name"     -> s"unicomplexbootspec-${UUID.randomUUID().toString}",
          JMX.prefixConfig       -> "true",
          "test.actor-init-time" -> infraStartTime.toString,
          "service-infra"        -> serviceInfraTimeouts.asJava
        ).asJava
      )

      val config = ConfigFactory.parseMap(props.asJava)

      val boot = Try {
        UnicomplexBoot(config)
          .createUsing {(name, config) => ActorSystem(name, config)}
          .scanComponents(List(classpath))
          .initExtensions.start()
      }

      try {
        useFn(boot)
      } finally {
        boot match {
          case Success(b) =>
            val system = b.actorSystem
            Unicomplex(system).uniActor ! GracefulStop
            val wait = 15.seconds
            Await.ready(system.whenTerminated, atMost = 15.seconds)
          case _ =>
        }
      }
    }

    lazy val classpath =
      s"${getClass.getClassLoader.getResource("classpaths").getPath}/UnicomplexBootTimeouts"

    describe("Setting the timeout in configuration") {
      it("boot success if listeners start up in less than the configured timeout") {
        useUnicomplex(infraStartTime = 1.nano, overallTimeout = 5.seconds, listenerTimeout = 5.seconds) {
          case Success(boot) => boot.started should be(true)
        }
      }

      it("fail if listeners do not start up in less than the configured timeout") {
        useUnicomplex(infraStartTime = 5.seconds, overallTimeout = 10.millis, listenerTimeout = 10.seconds) {
          case Failure(e) => e.getMessage should endWith("timed out after [10 milliseconds]")
        }
      }
    }

    /**
      * The following are 'pretty quick', but I still don't want to run these unless we
      * need them.  In fact, if this comment stays a while, we can just blow this away..
      */
    describe("Error conditions") {

      describe("These values are not allowed for the service infra timeout") {
        List(
          -1.millis,
          0.millis,
          1.nano
        ).foreach { to =>
          it(to.toString) {
            useUnicomplex(infraStartTime = 15.seconds, overallTimeout = to, listenerTimeout = 5.seconds) {
              case Failure(e) => e.getMessage should be(
                s"requirement failed: The config property, squbs.service-infra.timeout, must be " +
                  "greater than 0 milliseconds.")
            }
          }
        }
      }
    }
  }
}
