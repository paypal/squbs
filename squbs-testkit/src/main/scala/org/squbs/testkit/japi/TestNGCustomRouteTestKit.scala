/*
 * Copyright 2017 PayPal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.squbs.testkit.japi

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.TestKitBase
import com.typesafe.config.Config
import org.squbs.testkit.{DebugTiming, PortGetter, CustomTestKit => SCustomTestKit}
import org.squbs.unicomplex.UnicomplexBoot

import scala.jdk.CollectionConverters._

abstract class TestNGCustomRouteTestKit(val boot: UnicomplexBoot) extends {
  implicit override val system: ActorSystem = boot.actorSystem
} with TestNGRouteTest with TestKitBase with DebugTiming with PortGetter {

  private[this] val _systemResource = new CustomTestKitSystemResource(boot)

  override protected def systemResource: SystemResource = _systemResource

  def this() = this(SCustomTestKit.boot())

  def this(config: Config) = this(SCustomTestKit.boot(config = Option(config)))

  def this(withClassPath: Boolean) = this(SCustomTestKit.boot(withClassPath = Option(withClassPath)))

  def this(resources: java.util.List[String], withClassPath: Boolean) =
    this(SCustomTestKit.boot(resources = Option(resources.asScala.toSeq), withClassPath = Option(withClassPath)))

  def this(config: Config, resources: java.util.List[String], withClassPath: Boolean) =
    this(SCustomTestKit.boot(config = Option(config), resources = Option(resources.asScala.toSeq),
      withClassPath = Option(withClassPath)))
}

class CustomTestKitSystemResource(boot: UnicomplexBoot)
  extends SystemResource(boot.actorSystem.name, boot.config) {
    override protected def config: Config = boot.config
    override protected def createSystem(): ActorSystem = boot.actorSystem
  }
