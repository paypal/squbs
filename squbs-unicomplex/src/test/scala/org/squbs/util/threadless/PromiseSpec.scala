package org.squbs.util.threadless

import org.scalatest.{Matchers, FunSpec}
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
 * Created by huzhou on 4/2/14.
 */
class PromiseSpec extends FunSpec with Matchers {

  describe("Promise") {

    it("should be able to resolved to concrete value") {

      val p = Promise[String]()

      p shouldNot be(null)
      p.future shouldNot be(null)
      p.isCompleted should be(false)

      p.success("value") should be theSameInstanceAs(p)
      p.isCompleted should be(true)
      p.future.value should equal(Some(Success("value")))
    }

    it("should reject repeated value resolutions") {

      val p = Promise[String]()

      p.success("value") should be theSameInstanceAs(p)

      intercept[IllegalStateException]{
        p.success("value-twice")
      }
    }

    it("should be able to resolved as failure") {

      val p = Promise[String]()
      p.isCompleted should be(false)

      val cause: IllegalArgumentException = new IllegalArgumentException("string cannot be null")
      p.failure(cause)
      p.isCompleted should be(true)
      p.future.value should equal(Some(Failure(cause)))
    }

    it("should be resolved given associated future") {

      val p = Promise[String]()
      val other = Promise[String]()

      p.isCompleted should be(false)
      other.isCompleted should be(false)

      p.completeWith(other.future)

      other.complete(Success("value"))
      other.isCompleted should be(true)

      p.isCompleted should be(true)
      p.future.value should equal(Some(Success("value")))
    }

    it("should be kept as failure if created as failed") {

      val cause = new IllegalArgumentException("already failed")
      val failure = Promise.failed[String](cause)

      failure shouldNot be(null)
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

      val success = Promise.successful("value")

      success shouldNot be(null)
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
