/*
 * Copyright 2015 PayPal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.squbs.circuitbreaker

import java.beans.ConstructorProperties

import scala.beans.BeanProperty

// $COVERAGE-OFF$
case class CircuitBreakerInfo @ConstructorProperties(Array("name", "status", "historyUnitDuration", "successTimes",
  "fallbackTimes", "failFastTimes", "exceptionTimes", "history")) (@BeanProperty name: String,
                                                                   @BeanProperty status: String,
                                                                   @BeanProperty historyUnitDuration: String,
                                                                   @BeanProperty successTimes: Long,
                                                                   @BeanProperty fallbackTimes: Long,
                                                                   @BeanProperty failFastTimes: Long,
                                                                   @BeanProperty exceptionTimes: Long,
                                                                   @BeanProperty history: java.util.List[CBHistory])

case class CBHistory @ConstructorProperties(Array("period", "successes", "fallbacks", "failFasts", "exceptions",
  "errorRate", "failFastRate", "exceptionRate"))(@BeanProperty period: String,
                                                 @BeanProperty successes: Int,
                                                 @BeanProperty fallbacks: Int,
                                                 @BeanProperty failFasts: Int,
                                                 @BeanProperty exceptions: Int,
                                                 @BeanProperty errorRate: String,
                                                 @BeanProperty failFastRate: String,
                                                 @BeanProperty exceptionRate: String)

// $COVERAGE-ON$

trait CircuitBreakerMXBean {
  def getHttpClientCircuitBreakerInfo: java.util.List[CircuitBreakerInfo]
}
