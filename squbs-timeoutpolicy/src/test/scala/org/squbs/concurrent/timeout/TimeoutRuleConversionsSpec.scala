package org.squbs.concurrent.timeout

import org.scalatest.{Matchers, FlatSpecLike}
import org.squbs.concurrent.timeout._

import scala.concurrent.duration._

/**
 * Created by miawang on 11/28/14.
 */
class TimeoutRuleConversionsSpec extends FlatSpecLike with Matchers{

  "Implicit Conversions" should "work" in {

    (1 sigma).asInstanceOf[StandardDeviationRule].unit should be(1.0)
    (1 σ).asInstanceOf[StandardDeviationRule].unit should be(1.0)

    // around 2.99
    ((99.7 percent).asInstanceOf[StandardDeviationRule].unit * 10).round should be(30)
    ((99.7 `%ile`).asInstanceOf[StandardDeviationRule].unit * 10).round should be(30)
    ((99.7 percentile).asInstanceOf[StandardDeviationRule].unit * 10).round should be(30)

    fixedRule should be(FixedTimeoutRule)
  }
}
