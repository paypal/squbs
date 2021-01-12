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

import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class TimeoutRuleConversionsSpec extends AnyFlatSpecLike with Matchers{

  "Implicit Conversions" should "work" in {

    (1.sigma).asInstanceOf[SigmaTimeoutRule].unit should be(1.0)
    (1.σ).asInstanceOf[SigmaTimeoutRule].unit should be(1.0)

    // around 2.99
    ((99.7.percent).asInstanceOf[SigmaTimeoutRule].unit * 10).round should be(30)
    ((99.7.`%ile`).asInstanceOf[SigmaTimeoutRule].unit * 10).round should be(30)
    ((99.7.percentile).asInstanceOf[SigmaTimeoutRule].unit * 10).round should be(30)

    fixedRule should be(FixedTimeoutRule)
  }
}
