/*
 *  Copyright 2017 PayPal
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

package org.squbs.testkit

import org.apache.pekko.testkit.TestKitBase

import scala.concurrent.duration._

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
