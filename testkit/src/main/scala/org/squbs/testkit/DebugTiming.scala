package org.squbs.testkit

import scala.concurrent.duration._
import akka.testkit.TestKitBase

object DebugTiming {

  val debugMode = java.lang.management.ManagementFactory.getRuntimeMXBean.
    getInputArguments.toString.indexOf("jdwp") >= 0

  val debugTimeout = 10000.seconds

  if (debugMode) println(
    "\n##################\n" +
      s"IMPORTANT: Detected system running in debug mode. Test timeouts overridden to $debugTimeout.\n" +
      "##################\n\n")
}

trait DebugTiming extends TestKitBase {
  import DebugTiming._
  override def receiveOne(max: Duration): AnyRef =
    if (debugMode) super.receiveOne(debugTimeout)
    else super.receiveOne(max)
}
