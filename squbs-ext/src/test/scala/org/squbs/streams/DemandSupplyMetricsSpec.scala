/*
 * Copyright 2017 PayPal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.squbs.streams

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.ThrottleMode.Shaping
import org.apache.pekko.stream.scaladsl._
import org.apache.pekko.testkit.TestKit
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.metrics.MetricsExtension

import java.lang.management.ManagementFactory
import javax.management.ObjectName
import scala.concurrent.duration._

class DemandSupplyMetricsSpec extends TestKit(ActorSystem("DemandSupplyMetricsSpec")) with AsyncFlatSpecLike
  with Matchers {

  it should "throttle demand" in {
    val name = "test1"
    val testFlow = DemandSupplyMetrics[Int](name)(system)
    val result = Source(1 to 10).via(testFlow).throttle(1, 1.second, 1, Shaping).runWith(Sink.ignore)
    result map { _ =>
      assert(jmxValue[Long](s"$name-downstream-counter", "Count").get == 10)
    }
  }

  it should "require demand >= supply" in {
    val name = "test2"
    val testFlow = DemandSupplyMetrics[Int](name)(system)
    val result = Source(1 to 100000).via(testFlow).throttle(10000, 1.second, 10000, Shaping).runWith(Sink.ignore)

    result map { _ =>
      assert(jmxValue[Long](s"$name-upstream-counter", "Count").get == 100000)
      val downstreamCount = jmxValue[Long](s"$name-downstream-counter", "Count").get
      assert(downstreamCount == 100000 || downstreamCount == 100001)
    }
  }

  it should "report metrics correctly with buffering and throttling" in {
    val name = "test3"

    val preFlow = DemandSupplyMetrics[Int](s"$name-pre")(system)
    val postFlow = DemandSupplyMetrics[Int](s"$name-post")(system)

    val result = Source(1 to 100)
      .via(preFlow)
      .throttle(10, 1.second, 10, Shaping)
      .buffer(2, OverflowStrategy.backpressure)
      .throttle(20, 1.second, 20, Shaping)
      .via(postFlow)
      .runWith(Sink.ignore)

    result map { _ =>
      assert(jmxValue[Long](s"$name-pre-upstream-counter", "Count").get == 100)
      assert(jmxValue[Long](s"$name-pre-downstream-counter", "Count").get == 100)

      assert(jmxValue[Long](s"$name-post-upstream-counter", "Count").get == 100)
      assert(jmxValue[Long](s"$name-post-downstream-counter", "Count").get == 101)
    }
  }

  it should "report metrics correctly with buffering and upstream throttle" in {
    val name = "test4"

    val preFlow = DemandSupplyMetrics[Int](s"$name-pre")(system)
    val postFlow = DemandSupplyMetrics[Int](s"$name-post")(system)

    val result = Source(1 to 100)
      .via(preFlow)
      .buffer(2, OverflowStrategy.backpressure)
      .throttle(20, 1.second, 20, Shaping)
      .via(postFlow)
      .runWith(Sink.ignore)

    result map { _ =>
      assert(jmxValue[Long](s"$name-pre-upstream-counter", "Count").get == 100)
      assert(jmxValue[Long](s"$name-pre-downstream-counter", "Count").get == 100)

      assert(jmxValue[Long](s"$name-post-upstream-counter", "Count").get == 100)
      assert(jmxValue[Long](s"$name-post-downstream-counter", "Count").get == 101)
    }
  }

  it should "report metrics correctly with multiple materializations" in {
    val name = "test5"

    val dsMetric = DemandSupplyMetrics[Int](s"$name")(system)
    val flow = Source(1 to 10).via(dsMetric)

    val f1 = flow.runWith(Sink.ignore)
    val f2 = flow.runWith(Sink.ignore)
    val lf = for(f1Result <- f1 ; f2Result <- f2) yield(f1Result, f2Result)
    lf map { _ =>
      assert(jmxValue[Long](s"$name-upstream-counter", "Count").get >= 20)
      assert(jmxValue[Long](s"$name-downstream-counter", "Count").get >= 20)
    }
  }

  def jmxValue[T](beanName: String, key: String): Option[T] = {
    val oName =
      ObjectName.getInstance(s"${MetricsExtension(system).Domain}:name=${MetricsExtension(system).Domain}.$beanName")
    Option(ManagementFactory.getPlatformMBeanServer.getAttribute(oName, key)).map(_.asInstanceOf[T])
  }

}
