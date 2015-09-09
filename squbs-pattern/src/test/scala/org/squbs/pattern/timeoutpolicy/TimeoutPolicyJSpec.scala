/*
 *  Copyright 2015 PayPal
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

import org.scalatest.{FlatSpec, Matchers}

class TimeoutPolicyJSpec extends FlatSpec with Matchers {

  behavior of "A Java TimeoutPolicyJ"
  val timeoutPolicyJ = new TimeoutPolicyJ

  it should "work for fixed timeout policy using execute()" in {
    // fixed timeout
    for (i <- 0 until 100) {
      timeoutPolicyJ.invokeExecute(timeoutPolicyJ.getFixedTimeoutPolicy)
      timeoutPolicyJ.getFixedTimeoutPolicy.waitTime.toMillis shouldBe TimeoutPolicyJ.INITIAL_TIMEOUT
    }
  }

  timeoutPolicyJ.resetTimeoutPolicy(timeoutPolicyJ.getFixedTimeoutPolicy)

  it should "work for fixed timeout policy using inline" in {
    // fixed timeout
    for (i <- 0 until 100) {
      timeoutPolicyJ.invokeInline(timeoutPolicyJ.getFixedTimeoutPolicy)
      timeoutPolicyJ.getFixedTimeoutPolicy.waitTime.toMillis shouldBe TimeoutPolicyJ.INITIAL_TIMEOUT
    }
  }

  it should "work for sigma timeout policy using execute()" in {
    // initial timeout is INITIAL_TIMEOUT, task duration is randomized from 50 ms to 150 ms
    // 3 sigma will be less than 150 ms plus buffer
    for (i <- 0 until 100) {
      timeoutPolicyJ.invokeExecute(timeoutPolicyJ.getSigmaTimeoutPolicy)
      if (i > 10) {
        assert(timeoutPolicyJ.getSigmaTimeoutPolicy.waitTime.toMillis < 220)
      }
    }
  }

  timeoutPolicyJ.resetTimeoutPolicy(timeoutPolicyJ.getSigmaTimeoutPolicy)

  it should "work for sigma timeout policy using inline" in {
    // initial timeout is INITIAL_TIMEOUT, task duration is randomized from 50 ms to 150 ms
    // 3 sigma will be less than 150 ms plus buffer
    for (i <- 0 until 100) {
      timeoutPolicyJ.invokeInline(timeoutPolicyJ.getSigmaTimeoutPolicy)
      if (i > 10) {
        assert(timeoutPolicyJ.getSigmaTimeoutPolicy.waitTime.toMillis < 220)
      }
    }
  }

  it should "work for percentile timeout policy using execute()" in {
    // initial timeout is INITIAL_TIMEOUT, task duration is randomized from 50 ms to 150 ms
    // 95% will be less than 150 ms plus buffer
    for (i <- 0 until 100) {
      timeoutPolicyJ.invokeExecuteClosure(timeoutPolicyJ.getPercentileTimeoutPolicy)
      if (i > 10) {
        assert(timeoutPolicyJ.getPercentileTimeoutPolicy.waitTime.toMillis < 220)
      }
    }
  }

  timeoutPolicyJ.resetTimeoutPolicy(timeoutPolicyJ.getPercentileTimeoutPolicy)

  it should "work for percentile timeout policy using inline" in {
    // initial timeout is INITIAL_TIMEOUT, task duration is randomized from 50 ms to 150 ms
    // 95% will be less than 150 ms plus buffer
    for (i <- 0 until 100) {
      timeoutPolicyJ.invokeInline(timeoutPolicyJ.getPercentileTimeoutPolicy)
      if (i > 10) {
        assert(timeoutPolicyJ.getPercentileTimeoutPolicy.waitTime.toMillis < 220)
      }
    }
  }
}
