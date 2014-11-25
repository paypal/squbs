package org.squbs.concurrent.timeout

import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit
import MathUtil._


import scala.concurrent.duration._

/**
 * Created by miawang on 11/21/14.
 */

class Metrics(private var _initial: FiniteDuration) {
  private var _totalCount = 0

  private var _totalTime = 0L

  private var _timeoutCount = 0

  private var _square = 0.0

  private[timeout] def update(time: Long, timeout:Boolean) {
    if (timeout) {
      _timeoutCount = _timeoutCount + 1
    }

    _totalCount = _totalCount + 1
    _totalTime = _totalTime + time

    // get square
    sumSquare(time)
  }

  private def sumSquare(time: Long) {
    if (_totalCount > 1) {
      val y = _totalCount * time - totalTime
      _square = _square + y * y / (_totalCount * (_totalCount - 1).toDouble)
    } else _square = time
  }

  def totalCount = _totalCount

  /**
   * total Time in nano unit
   * @return
   */
  def totalTime = _totalTime

  def timeoutCount = _timeoutCount

  def square = _square

  def initial = _initial

  override def toString: String = {
    s"[initial=${_initial}, totalCount=${_totalCount},totalTime=${_totalTime}, square=${_square}, timeoutCount=${_timeoutCount}]"
  }
}

class TimeoutItem(val policy: TimeoutPolicy) {
  lazy val startTime = System.nanoTime()
  lazy val timeout = policy.waitTime

  def waitTime: FiniteDuration = {
    startTime // set the start time
    timeout
  }

  def update() = {
    val timeTaken = System.nanoTime() - startTime
    val isTimeout = timeTaken > timeout.toNanos
    policy.metrics.update(timeTaken, isTimeout)
  }
}

abstract class TimeoutPolicy(initial: FiniteDuration) {
  @volatile private[timeout] var _metrics = new Metrics(initial)

  def waitTime:FiniteDuration

  def execute[T](f: FiniteDuration => T): T = {
    val start = System.nanoTime()
    lazy val timeout = waitTime
    try {
      f(timeout)
    } finally {
      val timeTaken = System.nanoTime() - start
      val isTimeout = timeTaken > timeout.toNanos
      _metrics.update(timeTaken, isTimeout)
    }
  }

  def item = new TimeoutItem(this)

  /**
   * reset the metrics, return the previous metrics
   * @param initial
   * @return
   */
  def reset(initial: FiniteDuration): Metrics = {
    val prev = _metrics
    val init = if (initial != null) initial else prev.initial
    _metrics = new Metrics(init)
    prev
  }

  def metrics = _metrics
}

class FixedTimeoutPolicy(timeout: FiniteDuration) extends TimeoutPolicy(timeout) {

  override def waitTime: FiniteDuration = _metrics.initial

}

/**
 * Timeout Policy by following sigma rules
 * http://en.wikipedia.org/wiki/68%E2%80%9395%E2%80%9399.7_rule
 * @param initial
 * @param unit
 */
class EmpiricalTimeoutPolicy(initial: FiniteDuration, unit: Double) extends TimeoutPolicy(initial) {

  override def waitTime: FiniteDuration = {
    import math._
    // assign the instance field to a local variable for preventing data in-consistence
    val metrics = this._metrics
    val duration = if (metrics.totalCount > 1) {
      val standardDeviation = sqrt(metrics.square / metrics.totalCount)
      val averageTime = metrics.totalTime / metrics.totalCount.toDouble
      FiniteDuration((averageTime + (unit * standardDeviation)).toLong, TimeUnit.NANOSECONDS)
    } else metrics.initial
    if (duration > metrics.initial) metrics.initial else duration
  }
}

trait TimeoutRule {
}

object FixedTimeoutRule extends TimeoutRule

trait StandardDeviationRule extends TimeoutRule{
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
  def apply(name: String, initial: FiniteDuration, debug: FiniteDuration = 1000 seconds, rule: TimeoutRule): TimeoutPolicy = if (debugMode) {
    new FixedTimeoutPolicy(debug)
  } else {
    val policy = rule match {
      case FixedTimeoutRule | null => new FixedTimeoutPolicy(initial)
      case SigmaTimeoutRule(unit) => new EmpiricalTimeoutPolicy(initial, unit)
      case r: PercentileTimeoutRule => new EmpiricalTimeoutPolicy(initial, r.unit)
    }

    policyMap.put(name, policy)

    policy
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


