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

import scala.concurrent.duration.FiniteDuration
import scala.math._

/**
 * the metrics case class for the TimeoutPolicy statistics
 * @param name same with TimeoutPolicy.name
 * @param initial initial value of the TimeoutPolicy
 * @param startOverCount max count for start over the statistics
 * @param totalTime sum time in nano-second of all of the transactions
 * @param totalCount total count of the transactions
 * @param timeoutCount count of timeout transactions
 * @param sumSquares sum of variation of response times
 */
case class Metrics(name:String, initial: FiniteDuration, startOverCount: Int, totalTime: Double = 0.0, totalCount: Int = 0, timeoutCount: Int = 0, sumSquares: Double = 0.0) {

  lazy val standardDeviation = if (totalCount > 0) sqrt(sumSquares / totalCount) else 0

  lazy val averageTime = if (totalCount > 0) totalTime / totalCount else 0

  /**
   * update the Metrics
   * @param time time taken in nano seconds
   * @param isTimeout true if this transaction is timed out
   * @return a new Metrics
   */
  def update(time: Double, isTimeout: Boolean): Metrics = if (this.totalCount < this.startOverCount) {
    val timeoutCount = if (isTimeout) this.timeoutCount + 1 else this.timeoutCount
    val totalCount = this.totalCount + 1
    val totalTime = this.totalTime + time
    val sumSquares = if (totalCount > 1) {
      val y = totalCount * time - totalTime
      val s = this.sumSquares + y * y / (totalCount.toDouble * (totalCount - 1))
      if (s < 0) this.sumSquares else s
    } else this.sumSquares
    this.copy(totalTime = totalTime, totalCount = totalCount, timeoutCount = timeoutCount, sumSquares = sumSquares)
  } else {
    // reach the max value, need to reset
    this.copy(totalTime = time, totalCount = 1, timeoutCount = if (isTimeout) 1 else 0, sumSquares = 0.0)
  }

  def reset(initial: Option[FiniteDuration] = None, newStartOverCount: Int = 0): Metrics = {
    val init = initial.getOrElse(this.initial)
    val slidePoint = if (newStartOverCount > 0) newStartOverCount else this.startOverCount
    Metrics(name, init, slidePoint)
  }

}