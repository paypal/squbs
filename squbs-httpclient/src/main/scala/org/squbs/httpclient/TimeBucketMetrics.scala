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

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.reflect.ClassTag

class TimeBucketMetrics[T: ClassTag](val units: Int, val unitSize: FiniteDuration, bucketCreator: () => T,
                                     clearBucket: T => Unit)(implicit val system: ActorSystem) {

  require(units >= 1)

  private[httpclient] val bucketCount = units + 1

  private[httpclient] val buckets = Array.fill(bucketCount){ bucketCreator() }

  private val unitNanos = unitSize.toNanos

  private val cancellable = scheduleCleanup()

  private[httpclient] def currentIndex(time: Long): Int = {
    // Note: currentTime can be negative so signedIdx can be negative
    // signShift is 0 if positive and 1 if negative
    // Avoid using if statements (branches) to not throw off the CPU's branch prediction.
    val signShift = time >>> 63
    val signedIdx = ((time / unitNanos % bucketCount) - signShift).toInt
    (signedIdx + bucketCount) % bucketCount // This ensures the index is 0 or positive
  }

  private[httpclient] def indexAt(offset: Int, time: Long): Int = {
    val signShift = time >>> 63
    val signedIdx = ((time / unitNanos % bucketCount) - signShift).toInt
    val offsetIdx = (signedIdx + offset) % bucketCount
    (offsetIdx + bucketCount) % bucketCount
  }

  /**
   * Obtains the current stats bucket, given the current nano time
   * @param time The current nano time
   * @return The current bucket for stats manipulations
   */
  def currentBucket(time: Long): T = buckets(currentIndex(time))

  /**
   * Obtains the stats bucket with offset from current bucket. This offset can be positive or negative.
   * An index of 0 being the current bucket, 1 being the next bucket, and -1 being the previous bucket.
   * @param index The offset from the current index
   * @param time The current nano time
   * @return The bucket in question, usually for testing or cleaning
   */
  def bucketAt(index: Int, time: Long): T = buckets(indexAt(index, time))

  private def clearNext(time: Long): Unit = clearBucket(bucketAt(1, time))

  /**
   * Provides the time into the bucket useful for calculating ratios in the current bucket when reporting,
   * especially in conjunction with the bucket unit size.
   * @param time The current nano time
   * @return The time into this bucket, in nanoseconds
   */
  def timeIntoBucket(time: Long): Long = {
    val bucketTime = time % unitNanos
    // In case of negative current time, bucketTime is negative and offsets leftwards
    if (bucketTime < 0) unitNanos + bucketTime else bucketTime
  }

  private[httpclient] def nextBucketBase(time: Long) = {
    val bucketTime = time % unitNanos
    val bucketBase = time - bucketTime
    // In case of negative current time, bucketTime is negative and offsets leftwards
    if (bucketTime < 0) bucketBase else bucketBase + unitNanos
  }

  private def scheduleCleanup() = {
    // Schedule the first cleanup 10ms or a quarter into the next bucket time, whichever is smaller.
    // 10ms is a safe margin already.
    val offset = math.min((10 milliseconds).toNanos, unitNanos / 4)
    val currentTime = System.nanoTime
    val firstCleanup = nextBucketBase(currentTime) + offset
    import system.dispatcher
    system.scheduler
      .scheduleWithFixedDelay((firstCleanup - currentTime).nanos, unitSize) { () => clearNext(System.nanoTime) }
  }

  /**
   * Return the stats buckets in their historical order, starting with current time unit and moving back in time.
   */
  def history(time: Long): Seq[T] = for (i <- 0 until units) yield bucketAt(-i, time)

  /**
   * Cancels all current timers associated with the stats.
   */
  def cancel(): Unit = cancellable.cancel()
}
