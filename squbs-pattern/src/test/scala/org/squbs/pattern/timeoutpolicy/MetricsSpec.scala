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

package org.squbs.pattern.timeoutpolicy

import org.scalatest.{Matchers, FlatSpecLike}
import concurrent.duration._

class MetricsSpec extends FlatSpecLike with Matchers{

  "Metrics" should "start over on totalCount exceed startOverCount" in {
    val metrics = Metrics(None, 2 seconds, 3)
    val metrics2 = metrics.update(3.millis.toNanos, isTimeout = false)
    val metrics3 = metrics2.update(4.millis.toNanos, isTimeout = true)
    val metrics4 = metrics3.update(4.millis.toNanos, isTimeout = true)
    metrics4.totalCount shouldBe 3

    // exceed start over Count
    val metrics5 = metrics4.update (3.millis.toNanos, isTimeout = false)
    metrics5.totalCount should be (1)
    metrics5.totalTime should be (3.millis.toNanos)
    metrics5.timeoutCount should be (0)
    metrics5.sumSquares should be (0.0)
  }

  "Metrics" should "be reset correctly" in {
    val metrics = Metrics(None, 2 seconds, 3)
    val metrics2 = metrics.update(3.millis.toNanos, isTimeout = false)
    val metrics3 = metrics2.update(4.millis.toNanos, isTimeout = true)
    val metrics4 = metrics3.update(4.millis.toNanos, isTimeout = true)
    metrics4.totalCount shouldBe 3

    // reset
    val metrics5 = metrics4.reset()
    metrics5.totalCount should be (0)
    metrics5.initial should be (2 seconds)
    metrics5.startOverCount should be (3)
    metrics5.sumSquares should be (0.0)
    metrics5.timeoutCount should be (0)
    metrics5.totalTime should be (0)

    val metrics6 = metrics5.reset(Some(3 seconds), 4)
    metrics6.totalCount should be (0)
    metrics6.initial should be (3 seconds)
    metrics6.startOverCount should be (4)
    metrics6.sumSquares should be (0.0)
    metrics6.timeoutCount should be (0)
    metrics6.totalTime should be (0)
  }
}
