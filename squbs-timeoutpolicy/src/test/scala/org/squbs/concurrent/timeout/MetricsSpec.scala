/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the AUTHORS file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.squbs.concurrent.timeout

import org.scalatest.{Matchers, FlatSpecLike}
import concurrent.duration._

class MetricsSpec extends FlatSpecLike with Matchers{

  "Metrics" should "start over on totalCount exceed startOverCount" in {
    var metrics = Metrics(None, 2 seconds, 3)
    metrics = metrics.update(3.millis.toNanos, false)
    metrics = metrics.update(4.millis.toNanos, true)
    metrics = metrics.update(4.millis.toNanos, true)
    metrics.totalCount shouldBe(3)

    // exceed start over Count
    metrics = metrics.update(3.millis.toNanos, false)
    metrics.totalCount should be(1)
    metrics.totalTime should be(3.millis.toNanos)
    metrics.timeoutCount should be(0)
    metrics.sumSquares should be(0.0)
  }

  "Metrics" should "be reset correctly" in {
    var metrics = Metrics(None, 2 seconds, 3)
    metrics = metrics.update(3.millis.toNanos, false)
    metrics = metrics.update(4.millis.toNanos, true)
    metrics = metrics.update(4.millis.toNanos, true)
    metrics.totalCount shouldBe(3)

    // reset
    metrics = metrics.reset()
    metrics.totalCount should be(0)
    metrics.initial should be(2 seconds)
    metrics.startOverCount should be(3)
    metrics.sumSquares should be(0.0)
    metrics.timeoutCount should be(0)
    metrics.totalTime should be(0)

    metrics = metrics.reset(Some(3 seconds), 4)
    metrics.totalCount should be(0)
    metrics.initial should be(3 seconds)
    metrics.startOverCount should be(4)
    metrics.sumSquares should be(0.0)
    metrics.timeoutCount should be(0)
    metrics.totalTime should be(0)
  }
}
