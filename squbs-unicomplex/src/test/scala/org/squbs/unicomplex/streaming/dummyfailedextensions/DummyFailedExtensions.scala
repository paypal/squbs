/*
 * Copyright 2015 PayPal
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

package org.squbs.unicomplex.streaming.dummyfailedextensions

import org.squbs.lifecycle.ExtensionLifecycle

trait DummyFailedExtension extends ExtensionLifecycle {

  private[dummyfailedextensions] var _state = "start"

  def state: String

  override def preInit() {
    _state += "preInit"
  }

  override def init() {
    _state += "init"
  }

  override def postInit() {
    _state += "postInit"
  }
}
class DummyFailedExtensionA extends DummyFailedExtension{

  def state = "A" + _state

  override def preInit() {
    throw new IllegalStateException("Test failing preInit()")
  }
}

class DummyFailedExtensionB extends DummyFailedExtension{

  def state = "B" + _state

  override def postInit(): Unit = {
    throw new IllegalStateException("Test failing postInit()")
  }
}

