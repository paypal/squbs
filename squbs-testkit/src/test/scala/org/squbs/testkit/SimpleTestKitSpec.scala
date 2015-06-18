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

package org.squbs.testkit

import akka.actor.Props
import akka.testkit.ImplicitSender
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{FlatSpecLike, Matchers}
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.Unicomplex

class SimpleTestKitSpec extends SimpleTestKit
with ImplicitSender with FlatSpecLike with Matchers with Eventually {

  override implicit val patienceConfig = new PatienceConfig(timeout = Span(3, Seconds))

  import scala.concurrent.duration._

  it should "return a Pong on a Ping" in {
    system.actorOf(Props[TestActor]) ! TestPing
    receiveOne(10 seconds) should be (TestPong)
  }

  override protected def afterAll() {
    Unicomplex(system).uniActor ! GracefulStop
  }
}