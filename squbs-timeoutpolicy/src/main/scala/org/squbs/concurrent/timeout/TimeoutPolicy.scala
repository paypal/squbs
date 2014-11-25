package org.squbs.concurrent.timeout

import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit
import MathUtil._


import scala.concurrent.duration._
import scala.math._

/**
 * Created by miawang on 11/21/14.
 */

case class Metrics(initial: FiniteDuration, totalTime: Long, totalCount: Int, timeoutCount: Int, sumSquares: Double) {

  lazy val standardDeviation = sqrt(sumSquares / totalCount)

  lazy val averageTime = totalTime / totalCount

}

abstract class TimeoutPolicy(initial: FiniteDuration) {
  private var _totalCount = 0

  private var _totalTime = 0L

  private var _timeoutCount = 0

  private var _sumSquares = 0.0

  @volatile protected var _initial = initial

  private[timeout] def waitTime: FiniteDuration

  class TimeoutTransaction() {
    lazy val start = System.nanoTime()
    lazy val timeout = TimeoutPolicy.this.waitTime

    def waitTime: FiniteDuration = {
      start // set the start time
      timeout
    }

    def end: Unit = {
      val timeTaken = System.nanoTime() - start
      val isTimeout = timeTaken > timeout.toNanos
      TimeoutPolicy.this.update(timeTaken, isTimeout)
    }
  }

  def execute[T](f: FiniteDuration => T): T = {
    val tx = this.transaction
    try {
      f(tx.waitTime)
    } finally {
      tx.end
    }
  }

  def transaction = new TimeoutTransaction()


  /**
   * reset the policy, return the previous metrics
   * @param initial
   * @return
   */
  def reset(initial: FiniteDuration): Metrics = synchronized {
    val metric = Metrics(_initial, _totalTime, _totalCount, _timeoutCount, _sumSquares)
    _initial = if (initial != null) initial else _initial
    _totalCount = 0
    _totalTime = 0
    _timeoutCount = 0
    _sumSquares = 0.0
    metric
  }

  def metrics = synchronized {
    Metrics(_initial, _totalTime, _totalCount, _timeoutCount, _sumSquares)
  }

  private[timeout] def update(time: Long, isTimeout: Boolean): Unit = synchronized {
    if (isTimeout) {
      _timeoutCount = _timeoutCount + 1
    }

    _totalCount = _totalCount + 1
    _totalTime = _totalTime + time

    // sum Squares
    _sumSquares = if (_totalCount > 1) {
      val y = _totalCount * time - _totalTime
      val s = _sumSquares + y * y / (_totalCount * (_totalCount - 1).toDouble)
      if (s < 0) {
        //TODO, what we should to do if s < 0
        println(s"addSumSquare(s=${_sumSquares}, n=${_totalCount}, t=${_totalTime}, x=${time}) returned negative")
        _sumSquares
      } else {
        s
      }
    } else time
  }
}

class FixedTimeoutPolicy(timeout: FiniteDuration) extends TimeoutPolicy(timeout) {

  override def waitTime: FiniteDuration = _initial

}

/**
 * Timeout Policy by following sigma rules
 * http://en.wikipedia.org/wiki/68%E2%80%9395%E2%80%9399.7_rule
 * @param initial
 * @param unit
 */
class EmpiricalTimeoutPolicy(initial: FiniteDuration, unit: Double) extends TimeoutPolicy(initial) {

  override def waitTime: FiniteDuration = {

    val metrics = this.metrics
    val waitTime = if (metrics.totalCount > 1) {
      val standardDeviation = metrics.standardDeviation
      val averageTime = metrics.averageTime
      FiniteDuration((averageTime + unit * standardDeviation).toLong, TimeUnit.NANOSECONDS)
    } else metrics.initial
    if (waitTime > metrics.initial) metrics.initial else waitTime
  }
}

trait TimeoutRule {
}

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
  val unit = erfInv(percent) * (math.sqrt(2))
}

object TimeoutPolicy {
  val debugMode = ManagementFactory.getRuntimeMXBean.getInputArguments.toString.indexOf("jdwp") >= 0

  private val policyMap = new collection.mutable.WeakHashMap[String, TimeoutPolicy]

  /**
   *
   * @param initial
   * @param debug
   * @param rule
   * @return
   */
  def apply(name: String, initial: FiniteDuration, rule: TimeoutRule, debug: FiniteDuration = 1000 seconds): TimeoutPolicy = {
    require(initial != null, "initial is required")
    require(debug != null, "debug is required")
    if (debugMode) {
      new FixedTimeoutPolicy(debug)
    } else {
      val policy = rule match {
        case FixedTimeoutRule | null => new FixedTimeoutPolicy(initial)
        case SigmaTimeoutRule(unit) => new EmpiricalTimeoutPolicy(initial, unit)
        case r: PercentileTimeoutRule => new EmpiricalTimeoutPolicy(initial, r.unit)
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
   * @param name
   * @param initial new initial value, use previously if it's null
   * @return previous metrics
   */
  def resetPolicy(name: String, initial: FiniteDuration): Option[Metrics] = {
    policyMap.get(name).map(_.reset(initial))
  }
}


