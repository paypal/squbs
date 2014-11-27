package org.squbs.concurrent.timeout

import java.lang.management.ManagementFactory
import MathUtil._
import akka.agent.Agent
import akka.event.slf4j.SLF4JLogging


import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.math._

/**
 * Created by miawang on 11/21/14.
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
 * @param ec
 */
abstract class TimeoutPolicy(name: String, initial: FiniteDuration, startOverCount: Int)(implicit ec: ExecutionContext) extends SLF4JLogging{
  require(initial != null, "initial duration is required")
  require(startOverCount > 0, "slidePoint should be positive")

  private val agent = Agent(Metrics(name, initial, startOverCount))

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
      if (timeTaken < 0) {
        //TODO only happened if user forgot to call waitTime first, should we throw an exception out?
        log.warn("call end without call waitTime first, ignore this transaction")
      } else {
        val isTimeout = timeTaken > timeout.toNanos
        TimeoutPolicy.this.update(timeTaken, isTimeout)
      }

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
  def reset(initial: FiniteDuration, newStartOverCount: Int = 0): Metrics = {
    val previous = agent()
    val init = if (initial != null) initial else previous.initial
    val slidePoint = if (newStartOverCount > 0) newStartOverCount else previous.startOverCount
    agent send Metrics(name, init, slidePoint)
    previous
  }

  def metrics = agent()

  private[timeout] def update(time: Double, isTimeout: Boolean): Unit = {
    agent send(m => {
      if (m.totalCount < m.startOverCount) {
        val timeoutCount = if (isTimeout) m.timeoutCount + 1 else m.timeoutCount
        val totalCount = m.totalCount + 1
        val totalTime = m.totalTime + time
        val sumSquares = if (totalCount > 1) {
          val y = totalCount * time - totalTime
          val s = m.sumSquares + y * y / (totalCount.toDouble * (totalCount - 1))
          if (s < 0) {
            log.warn(s"addSumSquare(s=${m.sumSquares}, n=${totalCount}, t=${totalTime}, x=${time}) returned negative")
            m.sumSquares
          } else s
        } else m.sumSquares
        m.copy(totalTime = totalTime, totalCount = totalCount, timeoutCount = timeoutCount, sumSquares = sumSquares)
      } else {
        // reach the max value, need to reset
        m.copy(totalTime = time, totalCount = 1, timeoutCount = (if (isTimeout) 1 else 0), sumSquares = 0.0)
      }
    })
  }
}

class FixedTimeoutPolicy(name: String, initial: FiniteDuration, startOverCount:Int)(implicit ec:ExecutionContext) extends TimeoutPolicy(name, initial, startOverCount)(ec) {
  override def waitTime: FiniteDuration = metrics.initial

}

/**
 * Timeout Policy by following sigma rules
 * http://en.wikipedia.org/wiki/68%E2%80%9395%E2%80%9399.7_rule
 * @param initial
 * @param sigmaUnits
 */
class EmpiricalTimeoutPolicy(name: String, initial: FiniteDuration, startOverCount:Int, sigmaUnits: Double, minSamples: Int)(implicit ec:ExecutionContext) extends TimeoutPolicy(name, initial, startOverCount)(ec) {
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
  val unit = erfInv(percent) * (math.sqrt(2))
}

object TimeoutPolicy extends SLF4JLogging{
  val debugMode = ManagementFactory.getRuntimeMXBean.getInputArguments.toString.indexOf("jdwp") >= 0

  private val policyMap = new collection.mutable.WeakHashMap[String, TimeoutPolicy]

  /**
   *
   * @param initial
   * @param debug
   * @param rule
   * @return
   */
  def apply(name: String, initial: FiniteDuration, rule: TimeoutRule, debug: FiniteDuration = 1000 seconds, minSamples: Int = 1000, startOverCount: Int = Int.MaxValue)(implicit ec: ExecutionContext): TimeoutPolicy = {
    require(initial != null, "initial is required")
    require(debug != null, "debug is required")
    if (debugMode) {
      log.warn("running in debug mode, use the debug duration instead")
      new FixedTimeoutPolicy(name, debug, startOverCount)
    } else {
      val policy = rule match {
        case FixedTimeoutRule | null => new FixedTimeoutPolicy(name, initial, startOverCount)
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
   * @param name
   * @param initial new initial value, use previously if it's null
   * @return previous metrics
   */
  def resetPolicy(name: String, initial: FiniteDuration): Option[Metrics] = {
    policyMap.get(name).map(_.reset(initial))
  }
}


