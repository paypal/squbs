package org.squbs.concurrent.timeout

import java.lang.management.ManagementFactory

import org.scalatest.{FlatSpecLike, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Random, Try}
import scala.concurrent.ExecutionContext.Implicits.global
/**
 * Created by miawang on 11/24/14.
 */
class TimeoutPolicySpec extends FlatSpecLike with Matchers{

  "TimeoutPolicy Object" should "works fine" in {
    intercept[IllegalArgumentException](TimeoutPolicy("", null, null))
    intercept[IllegalArgumentException](TimeoutPolicy("", 1 second, fixedRule, debug = null))

    val preLength = TimeoutPolicy.policyMetrics.size
    val policy = TimeoutPolicy(null, 1 second, fixedRule)

    TimeoutPolicy.policyMetrics.size should be(preLength)
    policy.isInstanceOf[FixedTimeoutPolicy] should be(true)

    val sigmaPolicy = TimeoutPolicy("test", 1 seconds, 2 sigma, minSamples = 1)

    TimeoutPolicy.policyMetrics.get("test") shouldNot be(null)
  }

  "Run FixedTimeoutPolicy in function" should "work" in {
    val policy = TimeoutPolicy(name = "test", initial = 1 second, fixedRule, minSamples = 1)

    for(i <- 0 until 10) {
      policy.execute(timeout => {
        timeout should be(1 second)
        Thread.sleep(10)
      })
    }

    Thread.sleep(3000)

    val metrics = policy.metrics
    metrics.name should be("test")
    metrics.initial should be(1 second)
    metrics.totalCount should be(10)
  }

  "Run SigmaTimeoutPolicy in explicit transaction" should "work" in {
    val policy = TimeoutPolicy(name = "test", initial = 1 second, 3 sigma, minSamples = 1)

    for(i <- 0 until 10) {
      val tx = policy.transaction
      Try {
        Await.ready(Future{
          Thread.sleep(100)
          if (i > 2) {
            tx.waitTime.toMillis should be < 1000l
          }
        }, tx.waitTime)
      }
      tx.end
    }

    Thread.sleep(3000)

    val metrics = policy.metrics
    metrics.name should be("test")
    metrics.initial should be(1 second)
    metrics.totalCount should be(10)
  }
}

