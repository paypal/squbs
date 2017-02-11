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

package org.squbs.lifecycle

import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import akka.actor.{PoisonPill, ActorRef, Actor, ActorSystem}
import org.scalatest.{BeforeAndAfterAll, Matchers, FlatSpecLike}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Future

class GracefulStopHelperTest extends TestKit(ActorSystem("testSystem")) with FlatSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll {

  import system.dispatcher

  "GracefulStopHelper" should "work when stop failed" in {

    val actorRef = TestActorRef(new Actor with GracefulStopHelper {
      def receive: Actor.Receive = {
        case "Stop" =>
          defaultMidActorStop(Seq(self))
          sender() ! "Done"
      }

      override def gracefulStop(target: ActorRef, timeout: FiniteDuration, stopMessage: Any = PoisonPill): Future[Boolean] = {
        Future {
          throw new RuntimeException("BadMan")
        }

      }
    })

    val actor = actorRef.underlyingActor

    actorRef ! "Stop"
    expectMsg("Done")

  }

  "GracefulStopHelper" should "work" in {

    val actorRef = TestActorRef(new Actor with GracefulStopHelper {
      def receive: Actor.Receive = {
        case "Stop" =>
          defaultMidActorStop(Seq(self))
          sender() ! "Done"
      }

      override def gracefulStop(target: ActorRef, timeout: FiniteDuration, stopMessage: Any = PoisonPill): Future[Boolean] = {

        Future {
          true
        }

      }
    })

    val actor = actorRef.underlyingActor

    actorRef ! "Stop"
    expectMsg("Done")

  }

}
