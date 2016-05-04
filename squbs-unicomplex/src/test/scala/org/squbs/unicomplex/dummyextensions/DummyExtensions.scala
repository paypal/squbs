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

package org.squbs.unicomplex.dummyextensions

import org.squbs.lifecycle.ExtensionLifecycle

trait DummyExtension extends ExtensionLifecycle {

  private[dummyextensions] var _state = "start"

  def state: String

  override def preInit() {
    _state += "preInit"
  }

  override def init() {
    _state += "init"
  }

  override def preCubesInit() {
    _state += "preCubesInit"
  }

  override def postInit() {
    _state += "postInit"
  }
}

class DummyExtensionA extends DummyExtension {

  def state = "A" + _state
}

class DummyExtensionB extends DummyExtension {

  def state = "B" + _state
}

class DummyExtensionC extends DummyExtension {

  def state = "C" + _state

  override def init() {
    throw new RuntimeException("BadInit", new RuntimeException("BadInitRootCause"))
  }

  override def shutdown() {
    println("DummyExtensionC shutdown...")
    throw new RuntimeException("BadShutdown")
  }

}
