package org.squbs.concurrent.timeout

import org.squbs.concurrent.timeout.TimeoutRuleConversions.Classifier

trait TimeoutRuleConversions extends Any {
  protected def sigmaRule: TimeoutRule
  protected def percentileRule: TimeoutRule

  def sigma = sigmaRule
  def σ = sigma

  /**
   * I'd like to use % directly, however, it's conflict with default operator % on number
   * @return
   */
  def percent = percentileRule

  def sigma[C](c: C)(implicit ev: Classifier[C]): ev.R = ev.convert(sigma)
  def percent[C](c: C)(implicit ev: Classifier[C]): ev.R = ev.convert(percent)
}

object TimeoutRuleConversions {
  trait Classifier[C] {
    type R
    def convert(d: TimeoutRule): R
  }
}