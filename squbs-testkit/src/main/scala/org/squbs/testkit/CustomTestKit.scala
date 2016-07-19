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

package org.squbs.testkit

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{BeforeAndAfterAll, Suite}
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.{UnicomplexBoot, JMX, Unicomplex}
import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration

object CustomTestKit {

  val actorSystems = collection.concurrent.TrieMap.empty[String, ActorSystem]

  private[testkit] def checkInit(actorSystem: ActorSystem) {
    if (actorSystems.putIfAbsent(actorSystem.name, actorSystem) == None)
      sys.addShutdownHook {
        val stopTimeoutInMs = actorSystem.settings.config.getDuration("squbs.default-stop-timeout", TimeUnit.MILLISECONDS)
        Await.ready(actorSystem.terminate(), FiniteDuration(stopTimeoutInMs, TimeUnit.MILLISECONDS))
      }
  }

  val stackTraceDepth = 6 // CustomTestKit$ x 3 -> Option -> CustomTestKit$ -> CustomTestKit -> Spec

  def defaultActorSystemName = actorSystemNameFrom((new Exception).getStackTrace.apply(stackTraceDepth).getClassName)

  def actorSystemNameFrom(className: String) =
    className
      .replace('.', '-')
      .replace('_', '-')
      .filter(_ != '$')

  val defaultResource = Seq(getClass.getClassLoader.getResource("").getPath + "/META-INF/squbs-meta.conf")

  def defaultConfig(actorSystemName: String) = ConfigFactory.parseString(
    s"""
       |squbs {
       |  actorsystem-name = ${actorSystemName}
       |  ${JMX.prefixConfig} = true
       |}
       |default-listener.bind-port = 0
    """.stripMargin
  )

  def boot(actorSystemName: Option[String] = None,
           config: Option[Config] = None,
           resources: Option[Seq[String]] = None,
           withClassPath: Option[Boolean] = None): UnicomplexBoot = {
    val baseConfig = defaultConfig(actorSystemName.getOrElse(defaultActorSystemName))
    boot(config.map(_.withFallback(baseConfig)).getOrElse(baseConfig), resources, withClassPath)
  }

  def boot(config: Config, resources: Option[Seq[String]], withClassPath: Option[Boolean]) = UnicomplexBoot(config)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanResources(withClassPath.getOrElse(false), resources.getOrElse(defaultResource):_*)
    .initExtensions.start()
}

/**
 * The custom test kit allows custom configuration of the Unicomplex before boot.  It also does not require the test
 * to run in a separate process and allow for parallel tests.
 */
abstract class CustomTestKit(val boot: UnicomplexBoot) extends TestKit(boot.actorSystem)
    with DebugTiming with ImplicitSender with Suite with BeforeAndAfterAll with PortGetter {

  def this() {
    this(CustomTestKit.boot())
  }

  def this(actorSystemName: String) {
    this(CustomTestKit.boot(Option(actorSystemName)))
  }

  def this(config: Config) {
    this(CustomTestKit.boot(config = Option(config)))
  }

  def this(resources: Seq[String], withClassPath: Boolean) {
    this(CustomTestKit.boot(resources = Option(resources), withClassPath = Option(withClassPath)))
  }

  def this(actorSystemName: String, resources: Seq[String], withClassPath: Boolean) {
    this(CustomTestKit.boot(Option(actorSystemName), resources = Option(resources), withClassPath = Option(withClassPath)))
  }

  def this(config: Config, resources: Seq[String], withClassPath: Boolean) {
    this(CustomTestKit.boot(config = Option(config), resources = Option(resources), withClassPath = Option(withClassPath)))
  }

  override protected def beforeAll() {
    CustomTestKit.checkInit(system)
  }

  override protected def afterAll() {
    Unicomplex(system).uniActor ! GracefulStop
  }
}
