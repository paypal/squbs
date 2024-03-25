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

package org.squbs.testkit

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.{Config, ConfigFactory, ConfigObject}
import org.scalatest.{BeforeAndAfterAll, Suite}
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.{JMX, Unicomplex, UnicomplexBoot}
import org.squbs.util.ConfigUtil._

import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._
import scala.util.Try

object CustomTestKit {

  private[testkit] val actorSystems = collection.concurrent.TrieMap.empty[String, ActorSystem]

  private[testkit] def checkInit(actorSystem: ActorSystem): Unit = {
    if (actorSystems.putIfAbsent(actorSystem.name, actorSystem).isEmpty)
      sys.addShutdownHook {
        val stopTimeoutInMs = actorSystem.settings.config.getDuration("squbs.default-stop-timeout", TimeUnit.MILLISECONDS)
        Await.ready(actorSystem.terminate(), FiniteDuration(stopTimeoutInMs, TimeUnit.MILLISECONDS))
      }
  }

  // JUnit creates a new object for each @Test method.  To prevent actor system name collisions, appending an integer
  // to the actor system name.
  val counter = new AtomicInteger(0)

  // scala 2.12 - CustomTestKit$ x 2 -> Option -> CustomTestKit$ -> CustomTestKit -> Spec
  // scala 2.11 - CustomTestKit$ x 3 -> Option -> CustomTestKit$ -> CustomTestKit -> Spec
  val stackTraceDepth: Int = if (util.Properties.versionNumberString.startsWith("2.11")) 6 else 5
  /* Example stack trace:

    java.lang.Exception
      at org.squbs.testkit.CustomTestKit$.defaultActorSystemName(CustomTestKit.scala:52)
      at org.squbs.testkit.CustomTestKit$.$anonfun$boot$1(CustomTestKit.scala:109)
      at scala.Option.getOrElse(Option.scala:121)
      at org.squbs.testkit.CustomTestKit$.boot(CustomTestKit.scala:109)
      at org.squbs.testkit.CustomTestKit.<init>(CustomTestKit.scala:128)
      at org.squbs.testkit.CustomTestKitDefaultSpec.<init>(CustomTestKitSpec.scala:60)
      at sun.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)
      at sun.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:62)
      at sun.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:45)
      at java.lang.reflect.Constructor.newInstance(Constructor.java:423)
      at java.lang.Class.newInstance(Class.java:442)
 */
  def defaultActorSystemName =
    s"${actorSystemNameFrom((new Exception).getStackTrace.apply(stackTraceDepth).getClassName)}-${counter.getAndIncrement()}"

  def actorSystemNameFrom(className: String): String =
    className
      .replace('.', '-')
      .replace('_', '-')
      .filter(_ != '$')

  /**
    * Detects default resources for this test. These are usually at two locations:
    * <ul>
    *   <li>$project-path/target/scala-2.11/classes/META-INF/squbs-meta.conf</li>
    *   <li>$project-path/target/scala-2.11/test-classes/META-INF/squbs-meta.conf</li>
    * </ul>
    * @return The list of detected resources
    */
  val defaultResources: Seq[String] = {
    val loader = getClass.getClassLoader
    val resourceHome = loader.getResource("").getPath
    val lastSlashOption = Try {
      if (resourceHome endsWith "/") resourceHome.lastIndexOf("/", resourceHome.length - 2)
      else resourceHome.lastIndexOf("/")
    }   .toOption.filter { _ > 0 }
    val targetPathOption = lastSlashOption map { lastSlash => resourceHome.substring(0, lastSlash + 1) }

    targetPathOption map { targetPath =>
      Seq("conf", "json", "properties")
        .flatMap { ext => loader.getResources(s"META-INF/squbs-meta.$ext").asScala }
        .map { _.getPath}
        .filter { _.startsWith(targetPath) }
    } getOrElse Seq.empty
  }

  def defaultConfig(actorSystemName: String): Config = {
    val baseConfig = ConfigFactory.load()
    val listeners = baseConfig.root.asScala.toSeq.collect {
      case (n, v: ConfigObject) if v.toConfig.getOption[String]("type").contains("squbs.listener") => n
    }

    val portOverrides = listeners.map { listener => s"$listener.bind-port = 0" }   .mkString("\n")

    ConfigFactory.parseString(
      s"""
         |squbs {
         |  actorsystem-name = $actorSystemName
         |  ${JMX.prefixConfig} = true
         |}
      """.stripMargin + portOverrides
    )
  }

  def boot(actorSystemName: Option[String] = None,
           config: Option[Config] = None,
           resources: Option[Seq[String]] = None,
           withClassPath: Option[Boolean] = None): UnicomplexBoot = {
    val baseConfig = defaultConfig(actorSystemName.getOrElse(defaultActorSystemName))
    boot(config.map(_.withFallback(baseConfig)).getOrElse(baseConfig), resources, withClassPath)
  }

  private def boot(config: Config, resources: Option[Seq[String]], withClassPath: Option[Boolean]): UnicomplexBoot =
    UnicomplexBoot(config)
      .createUsing {(name, config) => ActorSystem(name, config)}
      .scanResources(withClassPath.getOrElse(false), resources.getOrElse(defaultResources):_*)
      .initExtensions.start()
}

/**
 * The custom test kit allows custom configuration of the Unicomplex before boot.  It also does not require the test
 * to run in a separate process and allow for parallel tests.
 */
abstract class CustomTestKit(val boot: UnicomplexBoot) extends TestKit(boot.actorSystem)
    with DebugTiming with ImplicitSender with Suite with BeforeAndAfterAll with PortGetter {

  def this() = this(CustomTestKit.boot())

  def this(actorSystemName: String) = this(CustomTestKit.boot(Option(actorSystemName)))

  def this(config: Config) = this(CustomTestKit.boot(config = Option(config)))

  def this(withClassPath: Boolean) = this(CustomTestKit.boot(withClassPath = Option(withClassPath)))

  def this(resources: Seq[String], withClassPath: Boolean) =
    this(CustomTestKit.boot(resources = Option(resources), withClassPath = Option(withClassPath)))

  def this(actorSystemName: String, resources: Seq[String], withClassPath: Boolean) =
    this(CustomTestKit.boot(Option(actorSystemName), resources = Option(resources),
      withClassPath = Option(withClassPath)))

  def this(config: Config, resources: Seq[String], withClassPath: Boolean) =
    this(CustomTestKit.boot(config = Option(config), resources = Option(resources),
      withClassPath = Option(withClassPath)))

  override protected def beforeAll(): Unit = CustomTestKit.checkInit(system)

  override protected def afterAll(): Unit = Unicomplex(system).uniActor ! GracefulStop
}
