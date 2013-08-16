/*
 * Copyright (c) 2013 eBay, Inc.
 * All rights reserved.
 *
 * Contributors:
 * asucharitakul
 */
package org.squbs.lifecycle

import java.util.jar.{Manifest => JarManifest}

trait ExtensionInit {

  def preInit(allJars: Array[(String, JarManifest)]) {}

  def init(allJars: Array[(String, JarManifest)]) {}

  def postInit(allJars: Array[(String, JarManifest)]) {}
}
