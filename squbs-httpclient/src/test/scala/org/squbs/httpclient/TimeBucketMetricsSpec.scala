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

package org.squbs.httpclient

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.TestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpecLike
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.language.postfixOps

class TimeBucketMetricsSpec extends TestKit(ActorSystem("CircuitBreakerMetricsSpec")) with AnyFunSpecLike
  with Matchers with BeforeAndAfterAll {

  it ("should index the buckets correctly near and around 0") {
    val bucketSize = 1 minute
    val bucketSizeNanos = bucketSize.toNanos
    val metrics = new TimeBucketMetrics[AtomicInteger](5, bucketSize,
        { () => new AtomicInteger(0) }, { ai => ai.set(0) })
    val indexes =
      for (testMinute <- -10 to 10) yield {
        val time = bucketSizeNanos * testMinute + (bucketSizeNanos / 2)
        val idx = metrics.currentIndex(time)
        println(s"minute: $testMinute, time: $time, index: $idx")
        idx
      }
    metrics.cancel()
    indexes should contain theSameElementsAs Seq(2, 3, 4, 5, 0, 1, 2, 3, 4, 5, 0, 1, 2, 3, 4, 5, 0, 1, 2, 3, 4)
  }

  it ("should index the previous buckets correctly near and around 0") {
    val bucketSize = 1 minute
    val bucketSizeNanos = bucketSize.toNanos
    val metrics = new TimeBucketMetrics[AtomicInteger](5, bucketSize,
    { () => new AtomicInteger(0) }, { ai => ai.set(0) })
    val indexes =
      for (testMinute <- -10 to 10) yield {
        val time = bucketSizeNanos * testMinute + (bucketSizeNanos / 2)
        metrics.indexAt(-1, time)
      }
    metrics.cancel()
    indexes should contain theSameElementsAs Seq(1, 2, 3, 4, 5, 0, 1, 2, 3, 4, 5, 0, 1, 2, 3, 4, 5, 0, 1, 2, 3)
  }

  it ("should index buckets with large negative offsets correctly near and around 0") {
    val bucketSize = 1 minute
    val bucketSizeNanos = bucketSize.toNanos
    val metrics = new TimeBucketMetrics[AtomicInteger](5, bucketSize,
                  { () => new AtomicInteger(0) }, { ai => ai.set(0) })
    val indexes =
      for (testMinute <- -10 to 10) yield {
        val time = bucketSizeNanos * testMinute + (bucketSizeNanos / 2)
        metrics.indexAt(-10, time)
      }
    metrics.cancel()
    indexes should contain theSameElementsAs Seq(4, 5, 0, 1, 2, 3, 4, 5, 0, 1, 2, 3, 4, 5, 0, 1, 2, 3, 4, 5, 0)
  }

  it ("should index the next buckets correctly near and around 0") {
    val bucketSize = 1 minute
    val bucketSizeNanos = bucketSize.toNanos
    val metrics = new TimeBucketMetrics[AtomicInteger](5, bucketSize,
                  { () => new AtomicInteger(0) }, { ai => ai.set(0) })
    val indexes =
      for (testMinute <- -10 to 10) yield {
        val time = bucketSizeNanos * testMinute + (bucketSizeNanos / 2)
        metrics.indexAt(1, time)
      }
    metrics.cancel()
    indexes should contain theSameElementsAs Seq(3, 4, 5, 0, 1, 2, 3, 4, 5, 0, 1, 2, 3, 4, 5, 0, 1, 2, 3, 4, 5)
  }

  it ("should provide correct history") {
    val time = (30 seconds).toNanos
    val metrics = new TimeBucketMetrics[AtomicInteger](5, 1 minute, { () => new AtomicInteger(0) }, { ai => ai.set(0) })
    metrics.history(time) should contain theSameElementsAs Seq(
      metrics.buckets(0), metrics.buckets(5), metrics.buckets(4), metrics.buckets(3), metrics.buckets(2))
  }

  it ("should properly calculate the time into bucket when time is negative") {
    val time = -(105 seconds).toNanos
    val metrics = new TimeBucketMetrics[AtomicInteger](5, 1 minute, { () => new AtomicInteger(0) }, { ai => ai.set(0) })
    metrics.timeIntoBucket(time) shouldBe (15 seconds).toNanos
  }

  it ("should properly calculate the time into bucket when time is positive") {
    val time = (75 seconds).toNanos
    val metrics = new TimeBucketMetrics[AtomicInteger](5, 1 minute, { () => new AtomicInteger(0) }, { ai => ai.set(0) })
    metrics.timeIntoBucket(time) shouldBe (15 seconds).toNanos
  }

  it ("should properly calculate the base time of the next bucket when time is negative") {
    val time = -(90 seconds).toNanos
    val metrics = new TimeBucketMetrics[AtomicInteger](5, 1 minute, { () => new AtomicInteger(0) }, { ai => ai.set(0) })
    metrics.nextBucketBase(time) shouldBe -(1 minute).toNanos
  }

  it ("should properly calculate the base time of the next bucket when time is positive") {
    val time = (90 seconds).toNanos
    val metrics = new TimeBucketMetrics[AtomicInteger](5, 1 minute, { () => new AtomicInteger(0) }, { ai => ai.set(0) })
    metrics.nextBucketBase(time) shouldBe (2 minutes).toNanos
  }

  it ("should add stats and clear the next bucket ahead of time") {
    val bucketSize = 1 second
    val bucketSizeNanos = bucketSize.toNanos
    val buckets = 5
    val metrics = new TimeBucketMetrics[AtomicInteger](buckets, bucketSize,
        { () => new AtomicInteger(0) }, { ai => ai.set(0) })

    // Make sure we go through each bucket twice.
    for (i <- 0 until (metrics.bucketCount * 2)) {
      // wait for the time beyond half way past the next second.
      val currentTime = System.nanoTime
      val timeLeftInBucket =
        if (currentTime < 0L) -currentTime % bucketSizeNanos
        else bucketSizeNanos - (currentTime % bucketSizeNanos)

      // The cleanup happens at the middle of bucket time. So check at 3/4 beyond bucket time start
      val checkTime = currentTime + timeLeftInBucket + (3 * bucketSizeNanos / 4)

      @tailrec
      def sleepAndAddStatsUntil(checkTime: Long): Unit = {
        val currentTime = System.nanoTime()
        if (currentTime < checkTime) {
          metrics.currentBucket(currentTime).incrementAndGet()
          Thread.sleep(10L) // 10 millisecond sleep time for each iteration.
          sleepAndAddStatsUntil(checkTime)
        }
      }

      sleepAndAddStatsUntil(checkTime)

      val t2 = System.nanoTime
      val clearedIndex = (metrics.currentIndex(t2) + 1) % metrics.bucketCount
      val clearedBucket = metrics.buckets(clearedIndex)
      val currentBucket = metrics.currentBucket(t2)
      currentBucket.get() should be > 0
      clearedBucket.get() shouldBe 0
    }
    metrics.cancel()
  }

  override protected def afterAll(): Unit = system.terminate()
}
