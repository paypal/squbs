package org.squbs.concurrent

/**
 * Created by miawang on 11/24/14.
 */
package object timeout {

  val fixedRule = FixedTimeoutRule

  implicit final class TimeoutRuleInt(private val n: Int) extends AnyVal with TimeoutRuleConversions {
    override protected def sigmaRule: TimeoutRule = SigmaTimeoutRule(n)

    override protected def percentileRule: TimeoutRule = PercentileTimeoutRule(n.toDouble / 100)
  }

  implicit final class TimeoutRuleDouble(private val n: Double) extends AnyVal with TimeoutRuleConversions {
    override protected def sigmaRule: TimeoutRule = SigmaTimeoutRule(n)

    override protected def percentileRule: TimeoutRule = PercentileTimeoutRule(n / 100)
  }
}
