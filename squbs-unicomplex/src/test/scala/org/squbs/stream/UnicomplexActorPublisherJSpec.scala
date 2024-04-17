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
import org.apache.pekko.stream.testkit.scaladsl.TestSource
import org.apache.pekko.testkit.TestKit
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex._

import scala.concurrent.duration._

object UnicomplexActorPublisherJSpec {
  val myConfig: Config = ConfigFactory.parseString(
    """
      | squbs.actorsystem-name = UnicomplexActorPublisherJSpec
    """.stripMargin)
  val boot = UnicomplexBoot(myConfig).createUsing((name, config) => ActorSystem(name, config))
    .scanResources("/")
    .initExtensions
    .start()
}

final class UnicomplexActorPublisherJSpec extends TestKit(UnicomplexActorPublisherJSpec.boot.actorSystem)
    with AnyFlatSpecLike with Matchers with BeforeAndAfterAll {

  val duration = 10.second

  val in = TestSource.probe[String]

  // expose probe port(s)
  val mat = new UnicomplexActorPublisherJ(system).runnableGraph()
  val (pub, sub) = mat.toScala
  val (pubIn, pubTrigger) = pub.toScala

  override def afterAll(): Unit = {
    Unicomplex(system).uniActor ! GracefulStop
  }

  "UnicomplexTriggerJ" should "activate flow by unicomplex" in {
    // send 2 elements to in
    pubIn.sendNext("1")
    pubIn.sendNext("2")
    sub.request(2)
    sub.expectNext(duration, "1")
    sub.expectNext("2")

    // re-send Active to unicomplex trigger, flow continues
    sub.request(2)
    sub.expectNoMessage(remainingOrDefault)
    pubTrigger.get ! SystemState
    pubIn.sendNext("3")
    pubIn.sendNext("4")
    sub.expectNext("3", "4")
  }
}
