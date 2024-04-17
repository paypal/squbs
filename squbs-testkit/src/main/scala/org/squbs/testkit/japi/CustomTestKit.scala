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

package org.squbs.testkit.japi

import org.apache.pekko.actor.ActorSystem
import com.typesafe.config.Config
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.{Unicomplex, UnicomplexBoot}
import org.squbs.testkit.{PortGetter, CustomTestKit => SCustomTestKit}

import scala.jdk.CollectionConverters._

abstract class CustomTestKit(val boot: UnicomplexBoot) extends PortGetter {
  val system: ActorSystem = boot.actorSystem

  SCustomTestKit.checkInit(system)

  def this() = this(SCustomTestKit.boot())

  def this(actorSystemName: String) = this(SCustomTestKit.boot(Option(actorSystemName)))

  def this(config: Config) = this(SCustomTestKit.boot(config = Option(config)))

  def this(withClassPath: Boolean) = this(SCustomTestKit.boot(withClassPath = Option(withClassPath)))

  def this(resources: java.util.List[String], withClassPath: Boolean) =
    this(SCustomTestKit.boot(resources = Option(resources.asScala.toList), withClassPath = Option(withClassPath)))

  def this(actorSystemName: String, resources: java.util.List[String], withClassPath: Boolean) =
    this(SCustomTestKit.boot(Option(actorSystemName), resources = Option(resources.asScala.toList),
      withClassPath = Option(withClassPath)))

  def this(config: Config, resources: java.util.List[String], withClassPath: Boolean) =
    this(SCustomTestKit.boot(config = Option(config), resources = Option(resources.asScala.toList),
      withClassPath = Option(withClassPath)))

  def shutdown(): Unit = Unicomplex(system).uniActor ! GracefulStop
}
