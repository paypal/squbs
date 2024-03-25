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
package org.squbs.stream

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.dispatch.Futures
import org.apache.pekko.stream.scaladsl.Keep
import org.apache.pekko.stream.testkit.scaladsl.{TestSink, TestSource}
import org.apache.pekko.stream.{ActorMaterializer, OverflowStrategy}
import org.apache.pekko.testkit.TestKit
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.stream.TriggerEvent._

import scala.language.postfixOps

class TriggerMergeSpec extends TestKit(ActorSystem.create("TriggerMergeSpec")) with AnyFlatSpecLike with Matchers {

  val in = TestSource.probe[String]
  val trigger = TestSource.probe[Int].collect {
    case 0 => DISABLE
    case 1 => ENABLE
  }

  // expose probe port(s)
  val ((pubIn, pubTrigger), sub) = new Trigger().source(in, trigger).buffer(1, OverflowStrategy.backpressure).mapAsync(2) { item =>
    Futures.successful {
      item
    }
  }.toMat(TestSink.probe[String])(Keep.both).run()

  "TriggerMerge" should "start the flow" in {
    // send 2 elements to in
    pubIn.sendNext("1")
    pubIn.sendNext("2")
    sub.request(2)

    // does not trigger flow
    pubTrigger.sendNext(3)
    sub.expectNoMessage(remainingOrDefault)

    // does not trigger flow
    pubTrigger.sendNext(0)
    sub.expectNoMessage(remainingOrDefault)

    // trigger flow
    pubTrigger.sendNext(1)
    sub.expectNext("1", "2")
  }

  "TriggerMerge" should "pause the flow" in {
    // does not pause flow
    pubTrigger.sendNext(3)
    pubIn.sendNext("3")
    sub.request(1)
    sub.expectNext("3")

    // does not pause flow
    pubTrigger.sendNext(1)
    pubIn.sendNext("4")
    sub.request(1)
    sub.expectNext("4")

    // pause flow allowing previous pull to go through
    pubTrigger.sendNext(0)
    pubIn.sendNext("5")
    sub.request(1)
    sub.expectNext("5")
    // pause flow
    pubIn.sendNext("6")
    sub.request(1)
    sub.expectNoMessage(remainingOrDefault)
  }

  "TriggerMerge" should "re-start the flow" in {
    // does not re-start flow
    pubTrigger.sendNext(3)
    sub.expectNoMessage(remainingOrDefault)

    // does not re-start flow
    pubTrigger.sendNext(0)
    sub.expectNoMessage(remainingOrDefault)

    // re-start flow
    pubTrigger.sendNext(1)
    sub.expectNext("6")
  }

  "TriggerMerge" should "handle complete" in {
    // trigger complete will not complete the flow
    pubTrigger.sendComplete()
    pubIn.sendNext("7")
    pubIn.sendNext("8")
    sub.request(2)
    sub.expectNext("7", "8")

    // input complete will complete the flow
    pubIn.sendComplete()
    sub.request(1)
    sub.expectComplete()

    pubIn.sendNext("9")
    sub.request(1)
    sub.expectNoMessage(remainingOrDefault)
  }

  "TriggerMerge" should "match stage name" in {
    new TriggerMerge[String].toString shouldBe "TriggerMerge"
  }
}
