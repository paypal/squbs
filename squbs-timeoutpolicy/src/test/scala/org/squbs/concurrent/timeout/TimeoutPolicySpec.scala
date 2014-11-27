package org.squbs.concurrent.timeout

import org.scalatest.{FlatSpecLike, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Random, Try}

/**
 * Created by miawang on 11/24/14.
 */
class TimeoutPolicySpec extends FlatSpecLike with Matchers{

  "Random.nextGaussian" should "work as expect" in {
    import scala.concurrent.ExecutionContext.Implicits.global
    val timeoutPolicy = TimeoutPolicy("test", initial = 1 seconds, rule = 3 `%ile`)
    val sigma = 30
    val mean = 50
    for (i <- 0 until 10000) {
      val tx = timeoutPolicy.transaction
      val future = Try{
        Await.ready(Future{
          val s = (Random.nextGaussian() * sigma + mean).round
          Thread.sleep(s)
        }, tx.waitTime)
      }
      tx.end
//      val metrics = timeoutPolicy.metrics
//      println(s"average=${metrics.averageTime}, standardDeviation=${metrics.standardDeviation}")
    }

    Thread.sleep(5000)
    val metrics = timeoutPolicy.metrics
    println(s"average=${metrics.averageTime.toLong}, standardDeviation=${metrics.standardDeviation.toLong}")
    val succeedPercent = (metrics.totalCount - metrics.timeoutCount) / metrics.totalCount.toDouble
    println(succeedPercent)
    println(metrics)
  }


}

