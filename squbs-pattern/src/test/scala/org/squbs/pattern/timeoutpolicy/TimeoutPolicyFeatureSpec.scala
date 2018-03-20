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

import java.util.concurrent.TimeUnit

import org.scalatest.{FlatSpecLike, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Random, Try}

class TimeoutPolicyFeatureSpec extends FlatSpecLike with Matchers{

  "TimeoutPolicy return faster than timeout" should "works fine" in {
    //val policy = TimeoutPolicy(name = Some("test"), initial = 1 second, fixedRule, minSamples = 1)
    val policy = TimeoutPolicy(name = Some("mypolicy"), initial = 1.second)

    for(i<- 1 to 9) {
      val start = System.nanoTime
      val tx = policy.transaction
      Await.result(Future {
        val s = 100 * i
        Thread.sleep(s)
      }, tx.waitTime)
      tx.end
      val diff = TimeUnit.NANOSECONDS.toMillis(System.nanoTime - start)
      println(diff)
      assert(diff < 100*(i+1))
    }
  }

  "TimeoutPolicy throw exception when timeout" should "works fine" in {
    val policy = TimeoutPolicy(name = Some("mypolicy"), initial = 1.second)

    for(i<- 1 to 3) {
      val start = System.nanoTime
      val tx = policy.transaction
      intercept[java.util.concurrent.TimeoutException]{
        println(tx.waitTime)
        Await.result(Future {
          val s = 1000 * i + 100
          println(s)
          Thread.sleep(s)
        }, tx.waitTime)
      }
      tx.end
      val diff = TimeUnit.NANOSECONDS.toMillis(System.nanoTime - start)
      println(diff)
      assert(diff < 1100)
    }
  }

  "Random.nextGaussian" should "work as expected" in {
    //import scala.concurrent.ExecutionContext.Implicits.global
    val timeoutPolicy = TimeoutPolicy(name = Some("test"), initial = 1.seconds, rule = 3.sigma, minSamples = 50, startOverCount = 500)
    val sigma = 30
    val mean = 50
    val random = new Random(100)
    for (i <- 0 until 55) {
      val tx = timeoutPolicy.transaction
      println(i+":"+tx.waitTime.toMillis)
      Try{
        Await.ready(Future{
          val n = random.nextGaussian()
          val a = 0
          val b = 100


          val s = (n*sigma+mean).round
          Thread.sleep(s)
        }, tx.waitTime)
      }
      tx.end()
      //      val metrics = timeoutPolicy.metrics
      //      println(s"average=${metrics.averageTime}, standardDeviation=${metrics.standardDeviation}")
    }
    val metrics = timeoutPolicy.metrics
    println(s"average=${metrics.averageTime.toLong}, standardDeviation=${metrics.standardDeviation.toLong}")
    val succeedPercent = (metrics.totalCount - metrics.timeoutCount) / metrics.totalCount.toDouble
    println(succeedPercent)
    println(metrics)

    //timeout should be 142

    //test fast pass
    val start1 = System.nanoTime
    val tx = timeoutPolicy.transaction
    println(tx.waitTime.toMillis)
    Await.ready(Future{
      val s = 100
      Thread.sleep(s)
    }, tx.waitTime)
    tx.end()
    val diff = TimeUnit.NANOSECONDS.toMillis(System.nanoTime - start1)
    println(diff)
    assert(diff < 150)

    //test fast fail
    val start2 = System.nanoTime
    intercept[java.util.concurrent.TimeoutException] {
      val tx2 = timeoutPolicy.transaction
      println(tx2.waitTime.toMillis)
      Await.ready(Future {
        val s = 200
        Thread.sleep(s)
      }, tx2.waitTime)
      tx2.end()
    }
    val diff2 = TimeUnit.NANOSECONDS.toMillis(System.nanoTime - start2)
    println(diff)
    assert(diff < 200)

  }

  "Random.nextGaussian with 1 sigma" should "work as expected" in {
    //import scala.concurrent.ExecutionContext.Implicits.global
    val timeoutPolicy = TimeoutPolicy(name = Some("test"), initial = 1.seconds, rule = 1.sigma, minSamples = 50, startOverCount = 500)
    val sigma = 30
    val mean = 50
    val random = new Random(100)
    for (i <- 0 until 55) {
      val tx = timeoutPolicy.transaction
      println(i+":"+tx.waitTime.toMillis)
      Try{
        Await.ready(Future{
          val n = random.nextGaussian()
          val a = 0
          val b = 100


          val s = (n*sigma+mean).round
          Thread.sleep(s)
        }, tx.waitTime)
      }
      tx.end()
      //      val metrics = timeoutPolicy.metrics
      //      println(s"average=${metrics.averageTime}, standardDeviation=${metrics.standardDeviation}")
    }
    val metrics = timeoutPolicy.metrics
    println(s"average=${metrics.averageTime.toLong}, standardDeviation=${metrics.standardDeviation.toLong}")
    val succeedPercent = (metrics.totalCount - metrics.timeoutCount) / metrics.totalCount.toDouble
    println(succeedPercent)
    println(metrics)

    //timeout should be 84
    var waitTime:Long = 0
    //test fast pass
    val start1 = System.nanoTime
    val tx = timeoutPolicy.transaction
    waitTime = tx.waitTime.toMillis
    println("current wait time:"+tx.waitTime.toMillis)
    Await.ready(Future{
      val s = 50
      Thread.sleep(s)
    }, tx.waitTime)
    tx.end()
    val diff = TimeUnit.NANOSECONDS.toMillis(System.nanoTime - start1)
    println("diff:"+diff)
    assert(diff < 100)

    //test fast fail
    val start2 = System.nanoTime

    intercept[java.util.concurrent.TimeoutException] {
      val tx2 = timeoutPolicy.transaction
      waitTime = tx2.waitTime.toMillis
      println("current wait time:"+tx2.waitTime.toMillis)
      Await.ready(Future {
        val s = 100
        Thread.sleep(s)
      }, tx2.waitTime)
      tx2.end()
    }
    val diff2 = TimeUnit.NANOSECONDS.toMillis(System.nanoTime - start2)
    println("diff:"+diff2)
    assert(diff2 >= waitTime)
  }
}

