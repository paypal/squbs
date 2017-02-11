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

ackage org.squbs.bottlecube

import org.scalatest._
import org.squbs.testkit.SqubsTestKit
import org.squbs.bottlemsgs.StartEvents
import scala.concurrent.duration._
import akka.actor.Props
import org.squbs.bottlemsgs.Event

class LyricsActorTest extends SqubsTestKit with FunSuite {

  test ("Create and ping LyricsActor") {
    system.actorOf(Props[LyricsActor]) ! StartEvents
    expectMsgType[Event](10 seconds)
  }

  test ("Ping LyricsActor") {
    system.actorSelection("/user/bottlecube/lyrics") ! StartEvents
    expectMsgType[Event](10 seconds)
  }
}


class LyricsActorBehaviorTest extends SqubsTestKit with FunSpec {

  describe("The LyricsActor") {

    it ("Should respond with Events after receiving StartEvents") {
      system.actorSelection("/user/bottlecube/lyrics") ! StartEvents
      expectMsgType[Event](10 seconds)
    }

    it ("Should be creatable using normal actor creation") {
      system.actorOf(Props[LyricsActor]) ! StartEvents
      expectMsgType[Event](10 seconds)
    }
  }
}


class LyricsFeatureSpec extends SqubsTestKit with FeatureSpec with GivenWhenThen {

  feature ("Multiple response events in LyricsActor") {

    info ("I should be able to")
    info ("get multiple Event responses on one request")

    scenario ("One StartEvent sent to LyricsActor, resulting in multiple response events.") {
      system.actorSelection("/user/bottlecube/lyrics") ! StartEvents
      receiveN(2, 20 seconds) foreach {
        case _: Event => // good
        case x        => assert(condition = false, s"Received message is not an Event. Got ${x.getClass.getName}")
      }
    }
  }
}
