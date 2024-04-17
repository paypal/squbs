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

package org.squbs.testkit

import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.apache.pekko.testkit.TestKitBase
import com.typesafe.config.Config
import org.scalatest.Suite
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.{Unicomplex, UnicomplexBoot}

abstract class CustomRouteTestKit(val boot: UnicomplexBoot) extends {
  implicit override val system = boot.actorSystem
} with TestKitBase with Suite with ScalatestRouteTest with DebugTiming with PortGetter {

  def this() = this(CustomTestKit.boot())

  def this(actorSystemName: String) = this(CustomTestKit.boot(Option(actorSystemName)))

  def this(config: Config) = this(CustomTestKit.boot(config = Option(config)))

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
