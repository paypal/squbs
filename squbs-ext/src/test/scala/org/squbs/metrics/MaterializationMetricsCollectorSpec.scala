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

package org.squbs.metrics

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{Keep, Sink}
import org.apache.pekko.stream.testkit.scaladsl.TestSource
import org.apache.pekko.testkit.TestKit
import org.scalatest.OptionValues._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers

import java.lang.management.ManagementFactory
import javax.management.ObjectName
import scala.concurrent.duration._

class MaterializationMetricsCollectorSpec extends TestKit(ActorSystem("MaterializationMetricsCollectorSpec"))
  with AsyncFlatSpecLike with Matchers with ScalaFutures {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(scaled(1.minute))

  it should "update metrics when upstream finishes" in {

    val stream = TestSource.probe[Int]
      .via(MaterializationMetricsCollector[Int]("upstream-finishes"))
      .toMat(Sink.ignore)(Keep.both)
    val (probe1, future1) = stream.run()
    val (probe2, future2) = stream.run()

    jmxValue("upstream-finishes-active-count", "Count").value shouldBe 2
    jmxValue("upstream-finishes-creation-count", "Count").value shouldBe 2

    probe1.sendComplete()
    future1.futureValue

    // It should decrement the counter when stream fails
    jmxValue("upstream-finishes-active-count", "Count").value shouldBe 1
    jmxValue("upstream-finishes-creation-count", "Count").value shouldBe 2
    jmxValue("upstream-finishes-termination-count", "Count").value shouldBe 1

    val (probe3, future3) = stream.run()
    // It should increment the counter with a new materialization
    jmxValue("upstream-finishes-active-count", "Count").value shouldBe 2
    jmxValue("upstream-finishes-creation-count", "Count").value shouldBe 3
    jmxValue("upstream-finishes-termination-count", "Count").value shouldBe 1

    probe2.sendComplete()
    probe3.sendComplete()

    // Ensures the futures complete.
    future2.futureValue
    future3.futureValue

    jmxValue("upstream-finishes-active-count", "Count").value shouldBe 0
    jmxValue("upstream-finishes-creation-count", "Count").value shouldBe 3
    jmxValue("upstream-finishes-termination-count", "Count").value shouldBe 3
  }

  it should "update metrics when upstream fails" in {

    val stream = TestSource.probe[Int]
      .via(MaterializationMetricsCollector[Int]("upstream-fails"))
      .toMat(Sink.head[Int])(Keep.both)
    val (probe1, future1) = stream.run()
    val (probe2, future2) = stream.run()

    jmxValue("upstream-fails-active-count", "Count").value shouldBe 2
    jmxValue("upstream-fails-creation-count", "Count").value shouldBe 2

    probe1.sendError(new Exception("boom"))
    future1.failed.futureValue

    // It should decrement the counter when stream fails
    jmxValue("upstream-fails-active-count", "Count").value shouldBe 1
    jmxValue("upstream-fails-creation-count", "Count").value shouldBe 2
    jmxValue("upstream-fails-termination-count", "Count").value shouldBe 1

    val (probe3, future3) = stream.run()
    // It should increment the counter with a new materialization
    jmxValue("upstream-fails-active-count", "Count").value shouldBe 2
    jmxValue("upstream-fails-creation-count", "Count").value shouldBe 3
    jmxValue("upstream-fails-termination-count", "Count").value shouldBe 1

    probe2.sendError(new Exception("boom"))
    probe3.sendError(new Exception("boom"))

    // Ensures the futures complete.
    future2.failed.futureValue
    future3.failed.futureValue

    jmxValue("upstream-fails-active-count", "Count").value shouldBe 0
    jmxValue("upstream-fails-creation-count", "Count").value shouldBe 3
    jmxValue("upstream-fails-termination-count", "Count").value shouldBe 3
  }

  it should "update metrics when downstream terminates" in {

    val stream = TestSource.probe[Int]
      .via(MaterializationMetricsCollector[Int]("downstream-finishes"))
      .map { elem =>
        if(elem == 3) throw new Exception("boom")
        else elem
      }
      .toMat(Sink.ignore)(Keep.both)
    val (probe1, future1) = stream.run()
    val (probe2, future2) = stream.run()

    jmxValue("downstream-finishes-active-count", "Count").value shouldBe 2
    jmxValue("downstream-finishes-creation-count", "Count").value shouldBe 2

    probe1.sendNext(1)
    probe1.sendNext(2)
    probe1.sendNext(3)
    future1.failed.futureValue

    // It should decrement the counter when downstream fails
    jmxValue("downstream-finishes-active-count", "Count").value shouldBe 1
    jmxValue("downstream-finishes-creation-count", "Count").value shouldBe 2
    jmxValue("downstream-finishes-termination-count", "Count").value shouldBe 1

    val (probe3, future3) = stream.run()
    // It should increment the counter with a new materialization
    jmxValue("downstream-finishes-active-count", "Count").value shouldBe 2
    jmxValue("downstream-finishes-creation-count", "Count").value shouldBe 3
    jmxValue("downstream-finishes-termination-count", "Count").value shouldBe 1

    probe2.sendNext(1)
    probe2.sendNext(2)
    probe2.sendNext(3)
    probe3.sendNext(1)
    probe3.sendNext(2)
    probe3.sendNext(3)

    // Ensures the futures complete.
    future2.failed.futureValue
    future3.failed.futureValue

    jmxValue("downstream-finishes-active-count", "Count").value shouldBe 0
    jmxValue("downstream-finishes-creation-count", "Count").value shouldBe 3
    jmxValue("downstream-finishes-termination-count", "Count").value shouldBe 3
  }


  def jmxValue(beanName: String, key: String): Option[AnyRef] = {
    val oName =
      ObjectName.getInstance(s"${MetricsExtension(system).Domain}:name=${MetricsExtension(system).Domain}.$beanName")
    Option(ManagementFactory.getPlatformMBeanServer.getAttribute(oName, key))
  }
}
