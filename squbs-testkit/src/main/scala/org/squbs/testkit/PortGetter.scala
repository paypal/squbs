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

import org.apache.pekko.actor.ActorSystem
import org.squbs.testkit.Timeouts._
import org.squbs.unicomplex.{PortBindings, Unicomplex}

import scala.concurrent.Await

trait PortGetter {

  val system: ActorSystem
  lazy val port: Int = port(listener)

  import org.apache.pekko.pattern.ask
  def port(listener: String) = Await.result((Unicomplex(system).uniActor ? PortBindings).mapTo[Map[String, Int]], awaitMax)(listener)

  def listener: String = "default-listener"
}
