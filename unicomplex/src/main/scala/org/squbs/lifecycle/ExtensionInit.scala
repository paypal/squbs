/*
 * Copyright (c) 2013 eBay, Inc.
 * All rights reserved.
 *
 * Contributors:
 * asucharitakul
 */
package org.squbs.lifecycle

import com.typesafe.config.Config

trait ExtensionInit {

  def preInit(jarConfig: Seq[(String, Config)]) {}

  def init(jarConfig: Seq[(String, Config)]) {}

  def postInit(jarConfig: Seq[(String, Config)]) {}
}
