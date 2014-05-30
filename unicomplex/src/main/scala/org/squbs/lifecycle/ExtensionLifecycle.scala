/*
 * Copyright (c) 2013 eBay, Inc.
 * All rights reserved.
 *
 * Contributors:
 * asucharitakul
 */
package org.squbs.lifecycle

import com.typesafe.config.Config
import akka.actor.ActorSystem

object ExtensionLifecycle {

  private[lifecycle] val localActorSystem = new ThreadLocal[Option[ActorSystem]] {
    override def initialValue(): Option[ActorSystem] = None
  }

  def apply[T](actorSystem: ActorSystem)(creator: ()=>T): T = {
    localActorSystem.set(Some(actorSystem))
    val r = creator()
    localActorSystem.set(None)
    r
  }
}

trait ExtensionLifecycle {

  protected implicit val actorSystem = ExtensionLifecycle.localActorSystem.get.get

  def preInit(jarConfig: Seq[(String, Config)]) {}

  def init(jarConfig: Seq[(String, Config)]) {}

  def postInit(jarConfig: Seq[(String, Config)]) {}

  def shutdown(jarConfig: Seq[(String, Config)]) {}
}
