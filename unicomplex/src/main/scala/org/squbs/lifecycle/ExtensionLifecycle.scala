/*
 * Copyright (c) 2013 eBay, Inc.
 * All rights reserved.
 *
 * Contributors:
 * asucharitakul
 */
package org.squbs.lifecycle

import com.typesafe.config.Config
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

  def preInit(jarConfig: Seq[(String, Config)]) {}

  def init(jarConfig: Seq[(String, Config)]) {}

  def postInit(jarConfig: Seq[(String, Config)]) {}

  def shutdown(jarConfig: Seq[(String, Config)]) {}
}
