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

package org.squbs.testkit.japi

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.javadsl
import org.squbs.testkit.DebugTiming

import scala.concurrent.duration.Duration

class DebugTimingTestKit(actorSystem: ActorSystem) extends javadsl.TestKit(actorSystem) {
  override def receiveOne(max: Duration): AnyRef =
    if (DebugTiming.debugMode) super.receiveOne(java.time.Duration.ofNanos(DebugTiming.debugTimeout.toNanos))
    else super.receiveOne(java.time.Duration.ofNanos(max.toNanos))

  override def receiveOne(max: java.time.Duration): AnyRef =
    if (DebugTiming.debugMode) super.receiveOne(java.time.Duration.ofNanos(DebugTiming.debugTimeout.toNanos))
    else super.receiveOne(max)
}
