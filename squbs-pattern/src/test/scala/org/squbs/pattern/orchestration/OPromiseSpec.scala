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

package org.squbs.pattern.orchestration


import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import scala.language.postfixOps
import scala.util.{Failure, Success}

class OPromiseSpec extends AnyFunSpec with Matchers {

  describe("Promise") {

    it("should be able to resolved to concrete value") {

      val p = OPromise[String]()

      p should not be null
      p.future should not be null
      p should not be 'completed

      p.success("value") should be theSameInstanceAs p
      p shouldBe 'completed
      p.future.value should equal(Some(Success("value")))
    }

    it("should reject repeated value resolutions") {

      val p = OPromise[String]()

      p.success("value") should be theSameInstanceAs p

      intercept[IllegalStateException]{
        p.success("value-twice")
      }
    }

    it("should be able to resolved as failure") {

      val p = OPromise[String]()
      p should not be 'completed

      val cause: IllegalArgumentException = new IllegalArgumentException("string cannot be null")
      p.failure(cause)
      p shouldBe 'completed
      p.future.value should equal(Some(Failure(cause)))
    }

    it("should be resolved given associated future") {

      val p = OPromise[String]()
      val other = OPromise[String]()

      p should not be 'completed
      other should not be 'completed

      p.completeWith(other.future)

      other.complete(Success("value"))
      other shouldBe 'completed

      p shouldBe 'completed
      p.future.value should equal(Some(Success("value")))
    }

    it("should be kept as failure if created as failed") {

      val cause = new IllegalArgumentException("already failed")
      val failure = OPromise.failed[String](cause)

      failure should not be null
      failure.isCompleted should equal(true)
      failure.future.value should equal(Some(Failure(cause)))

      intercept[IllegalStateException]{
        failure.failure(cause)
      }

      intercept[IllegalStateException]{
        failure.success("value")
      }
    }

    it("should be kept as result if created given resolved value") {

      val success = OPromise.successful("value")

      success should not be null
      success.isCompleted should equal(true)
      success.future.value should equal(Some(Success("value")))

      intercept[IllegalStateException]{
        success.failure(new IllegalArgumentException("already assigned?"))
      }

      intercept[IllegalStateException]{
        success.success("value-twice")
      }
    }
  }
}
