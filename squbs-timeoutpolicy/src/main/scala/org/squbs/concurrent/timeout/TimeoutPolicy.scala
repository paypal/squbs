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

import java.lang.management.ManagementFactory
import MathUtil._
import akka.agent.Agent
import akka.event.slf4j.SLF4JLogging


import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.math._

/**
 * the metrics case class for the TimeoutPolicy statistics
 * @param name same with TimeoutPolicy.name
 * @param initial initial value of the TimeoutPolicy
 * @param startOverCount max count for start over the statistics
 * @param totalTime sum time in nano-second of all of the transactions
 * @param totalCount total count of the transactions
 * @param timeoutCount count of timeout transactions
 * @param sumSquares
 */
case class Metrics(name:String, initial: FiniteDuration, startOverCount: Int, totalTime: Double = 0.0, totalCount: Int = 0, timeoutCount: Int = 0, sumSquares: Double = 0.0) {

  lazy val standardDeviation = if (totalCount > 0) sqrt(sumSquares / totalCount) else 0

  lazy val averageTime = if (totalCount > 0) totalTime / totalCount else 0

}

/**
 *
 * @param name name of the policy
 * @param initial initial(also max) value of the timeout duration
 * @param startOverCount max total transaction count for start over the statistics
 * @param ec implicit parameter of ExecutionContext
 */
abstract class TimeoutPolicy(name: String, initial: FiniteDuration, startOverCount: Int)(implicit ec: ExecutionContext) extends SLF4JLogging{
  require(initial != null, "initial duration is required")
  require(startOverCount > 0, "slidePoint should be positive")

  private val agent = Agent(Metrics(name, initial, startOverCount))

  private[timeout] def waitTime: FiniteDuration

  class TimeoutTransaction() {
    lazy val start = System.nanoTime()
    lazy val waitTime = {
      start
      TimeoutPolicy.this.waitTime
    }

    def end(): Unit = {
      val timeTaken = System.nanoTime() - start
      if (timeTaken < 0) {
        //TODO only happened if user forgot to call waitTime first, should we throw an exception out?
        log.warn("call end without call waitTime first, ignore this transaction")
      } else {
        val isTimeout = timeTaken > waitTime.toNanos
        TimeoutPolicy.this.update(timeTaken, isTimeout)
      }

    }
  }

  def execute[T](f: FiniteDuration => T): T = {
    val tx = this.transaction
    try {
      f(tx.waitTime)
    } finally {
      tx.end()
    }
  }

  def transaction = new TimeoutTransaction()


  /**
   * reset the policy, return the previous metrics
   * @param initial new initial value
   * @param newStartOverCount new start over count
   * @return statistics of before the reset operation
   */
  def reset(initial: Option[FiniteDuration] = None, newStartOverCount: Int = 0): Metrics = {
    val previous = agent()
    val init = initial.getOrElse(previous.initial)
    val slidePoint = if (newStartOverCount > 0) newStartOverCount else previous.startOverCount
    agent send Metrics(name, init, slidePoint)
    previous
  }

  def metrics = agent()

  private[timeout] def update(time: Double, isTimeout: Boolean): Unit = agent send{m =>
    if (m.totalCount < m.startOverCount) {
      val timeoutCount = if (isTimeout) m.timeoutCount + 1 else m.timeoutCount
      val totalCount = m.totalCount + 1
      val totalTime = m.totalTime + time
      val sumSquares = if (totalCount > 1) {
        val y = totalCount * time - totalTime
        val s = m.sumSquares + y * y / (totalCount.toDouble * (totalCount - 1))
        if (s < 0) {
          log.warn(s"addSumSquare(s=${m.sumSquares}, n=$totalCount, t=$totalTime, x=$time) returned negative")
          m.sumSquares
        } else s
      } else m.sumSquares
      m.copy(totalTime = totalTime, totalCount = totalCount, timeoutCount = timeoutCount, sumSquares = sumSquares)
    } else {
      // reach the max value, need to reset
      m.copy(totalTime = time, totalCount = 1, timeoutCount = if (isTimeout) 1 else 0, sumSquares = 0.0)
    }
  }
}

class FixedTimeoutPolicy(name: String, initial: FiniteDuration, startOverCount:Int)(implicit ec:ExecutionContext) extends TimeoutPolicy(name, initial, startOverCount) {
  override def waitTime: FiniteDuration = metrics.initial

}

/**
 * Timeout Policy by following sigma rules
 * http://en.wikipedia.org/wiki/68%E2%80%9395%E2%80%9399.7_rule
 * @param name name of the policy
 * @param initial initial value of duration
 * @param startOverCount count for start over the statistic calculating
 * @param sigmaUnits unit of sigma, must be positive
 * @param minSamples min sample for the policy take effect
 */
class EmpiricalTimeoutPolicy(name: String, initial: FiniteDuration, startOverCount:Int, sigmaUnits: Double, minSamples: Int)(implicit ec:ExecutionContext) extends TimeoutPolicy(name, initial, startOverCount) {
  require(minSamples > 0, "miniSamples should be positive")
  require(sigmaUnits > 0, "sigmaUnits should be positive")

  override def waitTime: FiniteDuration = {

    val metrics = this.metrics
    val waitTime = if (metrics.totalCount > minSamples) {
      val standardDeviation = metrics.standardDeviation
      val averageTime = metrics.averageTime
      (averageTime + sigmaUnits * standardDeviation).ceil nanoseconds
    } else metrics.initial
    if (waitTime > metrics.initial) metrics.initial else waitTime
  }
}

trait TimeoutRule

object FixedTimeoutRule extends TimeoutRule

trait StandardDeviationRule extends TimeoutRule {
  /**
   * unit of Standard Deviation
   * @return
   */
  def unit: Double
}

case class SigmaTimeoutRule(unit: Double) extends StandardDeviationRule {
  require(unit > 0, "unit should be positive")
}

case class PercentileTimeoutRule(percent: Double) extends StandardDeviationRule {
  require(percent > 0 && percent < 1, "percent should in (0-1)")

  /**
   * percent = erf(x/sqrt(2)), therefore, x = erfInv(percent) * (sqrt(2))
   * http://en.wikipedia.org/wiki/68%E2%80%9395%E2%80%9399.7_rule
   */
  val unit = erfInv(percent) * math.sqrt(2)
}

object TimeoutPolicy extends SLF4JLogging{
  val debugMode = ManagementFactory.getRuntimeMXBean.getInputArguments.toString.indexOf("jdwp") >= 0

  private val policyMap = new collection.mutable.WeakHashMap[String, TimeoutPolicy]

  /**
   *
   * @param name name of the timeout policy
   * @param initial the initial value of timeout duration, also would be the max value on using non-FixedTimeoutRule
   * @param rule rule for the timeout policy, default is FixedTimeoutRule
   * @param debug the timeout duration in debug mode
   * @param minSamples required on choosing EmpiricalTimeoutPolicy, which would be the gatekeeper of the statistics take effect. default value is 1000
   * @param startOverCount the max count for start over the statistics, default is Int.MaxValue
   * @return timeout policy
   */
  def apply(name: String, initial: FiniteDuration, rule: TimeoutRule = FixedTimeoutRule, debug: FiniteDuration = 1000 seconds, minSamples: Int = 1000, startOverCount: Int = Int.MaxValue)(implicit ec: ExecutionContext): TimeoutPolicy = {
    require(initial != null, "initial is required")
    require(debug != null, "debug is required")
    if (debugMode) {
      log.warn("running in debug mode, use the debug duration instead")
      new FixedTimeoutPolicy(name, debug, startOverCount)
    } else {
      val policy = rule match {
        case FixedTimeoutRule => new FixedTimeoutPolicy(name, initial, startOverCount)
        case SigmaTimeoutRule(unit) => new EmpiricalTimeoutPolicy(name, initial, startOverCount, unit, minSamples)
        case r: PercentileTimeoutRule => new EmpiricalTimeoutPolicy(name, initial, startOverCount, r.unit, minSamples)
      }

      if (name != null) policyMap.put(name, policy)

      policy
    }
  }

  /**
   *
   * @return all of the metrics
   */
  def policyMetrics = policyMap.map(entry => (entry._1, entry._2.metrics)).toMap

  /**
   * reset the timeout Policy
   * @param name name of the policy
   * @param initial new initial value, use previously if it's None
   * @return previous metrics
   */
  def resetPolicy(name: String, initial: Option[FiniteDuration] = None, startOverCount: Int = 0): Option[Metrics] = {
    policyMap.get(name).map(_.reset(initial, startOverCount))
  }

}


