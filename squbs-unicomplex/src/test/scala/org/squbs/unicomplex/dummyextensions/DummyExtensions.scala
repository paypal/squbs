package org.squbs.unicomplex.dummyextensions

import org.squbs.lifecycle.ExtensionLifecycle

/**
 * Created by zhuwang on 3/11/14.
 */

trait DummyExtension extends ExtensionLifecycle {

  private[dummyextensions] var _state = "start"

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
class DummyExtensionA extends DummyExtension{

  def state = "A" + _state
}

class DummyExtensionB extends DummyExtension{

  def state = "B" + _state
}
