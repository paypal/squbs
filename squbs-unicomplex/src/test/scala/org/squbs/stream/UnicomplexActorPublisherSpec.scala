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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Keep
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest._
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex._

import scala.concurrent.duration._

final class UnicomplexActorPublisherSpec extends FlatSpecLike with Matchers with BeforeAndAfterAll {
  val myConfig: Config = ConfigFactory.parseString(
    """
      | squbs.actorsystem-name = UnicomplexActorPublisherSpec
    """.stripMargin)
  val boot = UnicomplexBoot(myConfig).createUsing((name, config) => ActorSystem(name, config))
    .scanResources("/")
    .initExtensions
    .start()
  implicit val system = boot.actorSystem
  implicit val materializer = ActorMaterializer()
  val duration = 10.second

  val in = TestSource.probe[String]

  // expose probe port(s)
  val ((pubIn, pubTrigger), sub) = LifecycleManaged(system).source(in).toMat(TestSink.probe[String])(Keep.both).run()

  override def afterAll() {
    Unicomplex(system).uniActor ! GracefulStop
  }

  "UnicomplexTrigger" should "activate flow by unicomplex" in {
    // send 2 elements to in
    pubIn.sendNext("1")
    pubIn.sendNext("2")
    sub.request(2)
    sub.expectNext(duration, "1")
    sub.expectNext("2")

    // re-send Active to unicomplex trigger, flow continues
    sub.request(2)
    sub.expectNoMsg()
    pubTrigger ! SystemState
    pubIn.sendNext("3")
    pubIn.sendNext("4")
    sub.expectNext("3", "4")
  }
}
