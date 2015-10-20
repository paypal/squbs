/*
 *  Copyright 2015 PayPal
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

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest._
import org.scalatest.OptionValues._
import org.squbs.httpclient.dummy.DummyServiceEndpointResolver
import org.squbs.httpclient.endpoint.EndpointRegistry
import org.squbs.testkit.Timeouts._

import scala.annotation.tailrec
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class HttpClientCircuitBreakerSpec extends TestKit(ActorSystem("HttpClientCircuitBreakerSpec")) with FlatSpecLike
with Matchers with CircuitBreakerSupport with HttpClientTestKit with BeforeAndAfterAll with BeforeAndAfterEach {

  implicit val _system = system

  override def beforeEach() {
    EndpointRegistry(system).register(new DummyServiceEndpointResolver)
  }

  override def afterAll() {
    shutdownActorSystem()
  }

  override def afterEach() {
    clearHttpClient()
  }

  "HttpClient with Success ServiceCallStatus" should "go through the correct logic" in {
    val httpClient = HttpClientFactory.get("DummyService")
    Await.ready(httpClient.readyFuture, awaitMax)
    val clientState = HttpClientManager(system).httpClientMap.get((httpClient.name, httpClient.env))
    val cbMetrics = clientState.value.cbMetrics
    cbMetrics.total.successTimes should be (0)
    val time = System.nanoTime
    cbMetrics.currentBucket(time).successTimes should be (0)
    cbMetrics.add(ServiceCallStatus.Success, time)
    1 should (be (cbMetrics.currentBucket(time).successTimes) or be (
        cbMetrics.buckets((cbMetrics.currentIndex(time) + 1) % cbMetrics.bucketCount).successTimes))
    cbMetrics.total.successTimes should be (1)
  }

  "HttpClient with Fallback ServiceCallStatus" should "go through the correct logic" in {
    val httpClient = HttpClientFactory.get("DummyService")
    Await.ready(httpClient.readyFuture, awaitMax)
    val clientState = HttpClientManager(system).httpClientMap.get((httpClient.name, httpClient.env))
    val cbMetrics = clientState.value.cbMetrics
    cbMetrics.total.fallbackTimes should be (0)
    val time = System.nanoTime
    cbMetrics.currentBucket(time).fallbackTimes should be (0)
    cbMetrics.add(ServiceCallStatus.Fallback, time)
    1 should (be (cbMetrics.currentBucket(time).fallbackTimes) or be (
      cbMetrics.buckets((cbMetrics.currentIndex(time) + 1) % cbMetrics.bucketCount).fallbackTimes))
    cbMetrics.total.fallbackTimes should be (1)
  }

  "HttpClient with FailFast ServiceCallStatus" should "go through the correct logic" in {
    val httpClient = HttpClientFactory.get("DummyService")
    Await.ready(httpClient.readyFuture, awaitMax)
    val clientState = HttpClientManager(system).httpClientMap.get((httpClient.name, httpClient.env))
    val cbMetrics = clientState.value.cbMetrics
    cbMetrics.total.failFastTimes should be (0)
    val time = System.nanoTime
    cbMetrics.currentBucket(time).failFastTimes should be (0)
    cbMetrics.add(ServiceCallStatus.FailFast, time)
    1 should (be (cbMetrics.currentBucket(time).failFastTimes) or be (
      cbMetrics.buckets((cbMetrics.currentIndex(time) + 1) % cbMetrics.bucketCount).failFastTimes))
    cbMetrics.total.failFastTimes should be (1)
  }

  "HttpClient with Exception ServiceCallStatus" should "go through the correct logic" in {
    val httpClient = HttpClientFactory.get("DummyService")
    Await.ready(httpClient.readyFuture, awaitMax)
    val clientState = HttpClientManager(system).httpClientMap.get((httpClient.name, httpClient.env))
    val cbMetrics = clientState.value.cbMetrics
    cbMetrics.total.exceptionTimes should be (0)
    val time = System.nanoTime
    cbMetrics.currentBucket(time).exceptionTimes should be (0)
    cbMetrics.add(ServiceCallStatus.Exception, time)
    1 should (be (cbMetrics.currentBucket(time).exceptionTimes) or be (
      cbMetrics.buckets((cbMetrics.currentIndex(time) + 1) % cbMetrics.bucketCount).exceptionTimes))
    cbMetrics.total.exceptionTimes should be (1)
  }
}

class CircuitBreakerMetricsSpec extends TestKit(ActorSystem("CircuitBreakerMetricsSpec")) with FunSpecLike
with Matchers with BeforeAndAfterAll {

  describe ("CircuitBreakerMetrics") {

    it ("should index the buckets correctly near and around 0") {
      val bucketSize = 1 minute
      val bucketSizeNanos = bucketSize.toNanos
      val metrics = new CircuitBreakerMetrics(5, bucketSize)(system)
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

    it ("should add stats and clear the next bucket ahead of time") {
      val bucketSize = 1 second
      val bucketSizeNanos = bucketSize.toNanos
      val buckets = 5
      val metrics = new CircuitBreakerMetrics(buckets, bucketSize)(system)

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
            metrics.add(ServiceCallStatus.Success, currentTime)
            metrics.add(ServiceCallStatus.Fallback, currentTime)
            metrics.add(ServiceCallStatus.FailFast, currentTime)
            metrics.add(ServiceCallStatus.Exception, currentTime)
            Thread.sleep(10L) // 10 millisecond sleep time for each iteration.
            sleepAndAddStatsUntil(checkTime)
          }
        }

        sleepAndAddStatsUntil(checkTime)

        val t2 = System.nanoTime
        val clearedIndex = (metrics.currentIndex(t2) + 1) % metrics.bucketCount
        val clearedBucket = metrics.buckets(clearedIndex)
        val currentBucket = metrics.currentBucket(t2)

        currentBucket.successTimes should be > 0
        currentBucket.fallbackTimes should be > 0
        currentBucket.failFastTimes should be > 0
        currentBucket.exceptionTimes should be > 0

        clearedBucket.successTimes shouldBe 0
        clearedBucket.fallbackTimes shouldBe 0
        clearedBucket.failFastTimes shouldBe 0
        clearedBucket.exceptionTimes shouldBe 0
      }
      metrics.total.successTimes should be > 0L
      metrics.total.fallbackTimes should be > 0L
      metrics.total.failFastTimes should be > 0L
      metrics.total.exceptionTimes should be > 0L
      metrics.cancel()
    }
  }

  override protected def afterAll(): Unit = system.shutdown()
}