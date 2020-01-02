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

import org.scalactic.TolerantNumerics
import org.scalatest.{Matchers, FlatSpecLike}
import org.apache.commons.math3.special.Erf

/**
 * see https://git-wip-us.apache.org/repos/asf?p=commons-math.git;a=blob_plain;f=src/test/java/org/apache/commons/math3/special/ErfTest.java;h=3e26662bd618d46e95b447ab538f4d45a473805e;hb=HEAD
 */
class MathUtilSpec extends FlatSpecLike with Matchers{

  it should "pass NaN cases" in {
    java.lang.Double.isNaN(MathUtil.erfInv(-1.001)) should be(true)
    java.lang.Double.isNaN(MathUtil.erfInv(1.001)) should be(true)
  }

  it should "pass Infinite case" in {
    MathUtil.erfInv(1) should be(Double.PositiveInfinity)
    MathUtil.erfInv(-1) should be(Double.NegativeInfinity)
  }

  it should "pass regular case" in {
    for (x <- (-5.9).until(5.9, 0.01)) {
      val y = Erf.erf(x)
      val dydx = 2 * math.exp(-x * x) / math.sqrt(Math.PI)
      implicit val equality = TolerantNumerics.tolerantDoubleEquality(1.0e-15 / dydx)
      x shouldEqual(MathUtil.erfInv(y))
    }
  }
}
