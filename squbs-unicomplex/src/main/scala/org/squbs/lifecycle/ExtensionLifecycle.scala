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

package org.squbs.lifecycle

import org.squbs.unicomplex.UnicomplexBoot

object ExtensionLifecycle {

  private[lifecycle] val tlBoot = new ThreadLocal[Option[UnicomplexBoot]] {
    override def initialValue(): Option[UnicomplexBoot] = None
  }

  def apply[T](boot: UnicomplexBoot)(creator: ()=>T): T = {
    tlBoot.set(Option(boot))
    val r = creator()
    tlBoot.set(None)
    r
  }
}

trait ExtensionLifecycle {

  protected implicit val boot = ExtensionLifecycle.tlBoot.get.get

  /**
    * Before the [[init]] phase, to let extensions to influence each other
    */
  def preInit(): Unit = {}

  /**
    * Before unicomplex starts (actor system has not been created yet)
    */
  def init(): Unit = {}

  /**
    * After unicomplex starts, but before any cubes are initialized
    */
  def preCubesInit(): Unit = {}

  /**
    * After cubes are initialized
    */
  def postInit(): Unit = {}

  /**
    * [[akka.actor.ActorSystem.registerOnTermination]]
    */
  def shutdown(): Unit = {}
}
