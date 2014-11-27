package org.squbs.concurrent.timeout

import org.scalatest.{Matchers, FlatSpecLike}
import org.squbs.concurrent.util.Random

import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.util.{Try}

/**
 * Created by miawang on 11/27/14.
 */
class NegativeExponentialSpec extends FlatSpecLike with Matchers{

  "NegativeExponentialTruncated" should "works fine with TimeoutPolicy " in {
    test(true)
  }

  "NegativeExponentialNotTruncated" should "works fine with TimeoutPolicy " in {
    test(false)
  }

  def test(truncate: Boolean): Unit = {
    val delay = getDelay(truncate = truncate, cycleMin = 20 millis, cycleMean = 30 millis, cycleMax = 80 milliseconds, random = new Random())

    import scala.concurrent.ExecutionContext.Implicits.global
    val timeoutPolicy = TimeoutPolicy("test", initial = 1 seconds, rule = 3 `%ile`)
    for (i <- 0 until 10000) {
      val tx = timeoutPolicy.transaction
      val future = Try{
        Await.ready(Future{
          val s = delay().toMillis
          Thread.sleep(s)
        }, tx.waitTime)
      }
      tx.end
      val metrics = timeoutPolicy.metrics
    }

    Thread.sleep(5000)
    val metrics = timeoutPolicy.metrics
    println(s"average=${metrics.averageTime.toLong}, standardDeviation=${metrics.standardDeviation.toLong}")
    val succeedPercent = (metrics.totalCount - metrics.timeoutCount) / metrics.totalCount.toDouble
    println(succeedPercent)
    println(metrics)

  }

  def getDelay(truncate: Boolean = true,
               cycleMin: FiniteDuration = 0 seconds,
               cycleMean: FiniteDuration = 1 seconds,
               cycleMax: FiniteDuration = 5 seconds,
               random: Random):() => FiniteDuration = {
    var mean = cycleMean.toNanos
    var shift = 0L

    if (!truncate) {
      shift = cycleMin.toNanos
      mean = mean - shift
    }

    () => {
      var delay = 0L
      if (cycleMean.toNanos > 0) {
        var x:Double = random.drandom(0.0, 1.0)
        if (x == 0) {
          x = 1e-20d
        }
        delay = shift + (mean * -Math.log(x)).toLong
        if (delay < cycleMin.toNanos) {
          delay = cycleMin.toNanos
        } else if (delay > cycleMax.toNanos) {
          delay = cycleMax.toNanos
        }
      }
      delay nanoseconds
    }
  }
}
