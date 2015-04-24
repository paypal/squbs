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
package org.squbs.concurrent

package object timeout {

  val fixedRule = FixedTimeoutRule

  trait TimeoutRuleConversions extends Any {

    /**
     * Timeout rule based on a given sigma (standard deviation) of previous response times.
     * @return the timeout rule
     */
    def sigma: TimeoutRule

      /**
       * Timeout rule based on the
       */
    def percentile: TimeoutRule

    /**
     * alias of sigma
     * @return
     */
    def σ = sigma


    /**
     * alias of percentile
     * @return
     */
    def percent = percentile

    /**
     * alias of percentile
     * @return
     */
    def `%ile` = percentile
  }

  implicit final class TimeoutRuleInt(private val n: Int) extends AnyVal with TimeoutRuleConversions {
    override def sigma: TimeoutRule = SigmaTimeoutRule(n)

    override def percentile: TimeoutRule = PercentileTimeoutRule(n.toDouble)
  }

  implicit final class TimeoutRuleDouble(private val n: Double) extends AnyVal with TimeoutRuleConversions {
    override def sigma: TimeoutRule = SigmaTimeoutRule(n)

    override def percentile: TimeoutRule = PercentileTimeoutRule(n)
  }
}
