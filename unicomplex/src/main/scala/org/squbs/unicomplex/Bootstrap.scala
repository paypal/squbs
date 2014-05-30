package org.squbs.unicomplex

import java.io._
import akka.actor.ActorSystem
import org.squbs.lifecycle.GracefulStop

object Bootstrap extends App {

  println("Booting unicomplex")

  UnicomplexBoot { () => ActorSystem("squbs") }
    .scanComponents(System.getProperty("java.class.path").split(File.pathSeparator))
    .initExtensions
    .stopJVMOnExit
    .start()
}

object Shutdown extends App {
  Unicomplex("squbs") ! GracefulStop
}