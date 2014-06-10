package org.squbs.unicomplex

import java.io._
import akka.actor.ActorSystem
import org.squbs.lifecycle.GracefulStop

object Bootstrap extends App {

  println("Booting unicomplex")

  // Note, the config directories may change during extension init. It is important to re-read the full config
  // for the actor system start.
  UnicomplexBoot { (name, config) => ActorSystem(name, config) }
    .scanComponents(System.getProperty("java.class.path").split(File.pathSeparator))
    .initExtensions
    .stopJVMOnExit
    .start()
}


object Shutdown extends App {
  val preConfig = UnicomplexBoot.getFullConfig(None)
  val actorSystemName = preConfig.getString("squbs.actorsystem-name")
  Unicomplex(actorSystemName) ! GracefulStop
}