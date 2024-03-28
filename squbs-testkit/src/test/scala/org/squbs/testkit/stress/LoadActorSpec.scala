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

package org.squbs.testkit.stress

import org.apache.pekko.actor._
import org.apache.pekko.testkit.{ImplicitSender, TestKit}
import org.scalatest.funspec.AnyFunSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.testkit.Timeouts._
import org.squbs.testkit.{TestPing, TestPong, Timeouts}

import scala.concurrent.duration._

class LoadActorSpec extends TestKit(ActorSystem("LoadActorSpec"))
with ImplicitSender with AnyFunSpecLike with Matchers {

  val warmUp = 20.seconds
  val steady = 40.seconds

  it ("Shall achieve the requested large TPS and report proper CPU statistics") {
    val ir = 500
    val startTime = System.nanoTime()
    val loadActor = system.actorOf(Props[LoadActor]())
    val statsActor = system.actorOf(Props[CPUStatsActor]())
    val statLogger = system.actorOf(Props(classOf[StatLogger], startTime, warmUp, steady, loadActor, statsActor))
    loadActor ! StartLoad(startTime, ir, warmUp, steady) {
      system.actorOf(Props[LoadTestActor]()) ! TestPing
    }
    statsActor ! StartStats(startTime, warmUp, steady, 1.seconds)

    var responseCount = 0L

    for (i <- 0 to 1) {
      fishForMessage(warmUp + steady + awaitMax) {
        case LoadStats(tps) =>
          println(s"Achieved $tps TPS")
          println(s"Response count in steady state: $responseCount")
          tps should be > (ir * 0.95) // Within 5% of IR
          responseCount should be > (ir * steady.toSeconds * 0.95).toLong
          true

        case CPUStats(avg, sDev) =>
          println(s"CPULoad $avg; Standard Deviation $sDev")
          avg should be > 0.0
          true

        case TestPong =>
          val currentTime = System.nanoTime() - startTime
          if (currentTime > warmUp.toNanos && currentTime <= (warmUp + steady).toNanos) {
            responseCount += 1
          }
          false
      }
    }
    loadActor ! PoisonPill
    statsActor ! PoisonPill
    statLogger ! PoisonPill
  }

  it ("Shall achieve the requested small TPS and report proper CPU statistics") {
    val ir = 10
    val startTime = System.nanoTime()
    val loadActor = system.actorOf(Props[LoadActor]())
    val statsActor = system.actorOf(Props[CPUStatsActor]())
    val statLogger = system.actorOf(Props(classOf[StatLogger], startTime, warmUp, steady, loadActor, statsActor))
    loadActor ! StartLoad(startTime, ir, warmUp, steady) {
      system.actorOf(Props[LoadTestActor]()) ! TestPing
    }
    statsActor ! StartStats(startTime, warmUp, steady, 1.seconds)

    var responseCount = 0L

    for (i <- 0 to 1) {
      fishForMessage(warmUp + steady + awaitMax) {
        case LoadStats(tps) =>
          println(s"Achieved $tps TPS")
          println(s"Response count in steady state: $responseCount")
          tps should be > (ir * 0.95) // Within 5% of IR
          responseCount should be > (ir * steady.toSeconds * 0.95).toLong
          true

        case CPUStats(avg, sDev) =>
          println(s"CPULoad $avg; Standard Deviation $sDev")
          avg should be > 0.0
          true

        case TestPong =>
          val currentTime = System.nanoTime() - startTime
          if (currentTime > warmUp.toNanos && currentTime <= (warmUp + steady).toNanos) {
            responseCount += 1
          }
          false
      }
    }
    loadActor ! PoisonPill
    statsActor ! PoisonPill
    statLogger ! PoisonPill
  }
}




class LoadTestActor extends Actor {
  def receive = {
    case TestPing => sender() ! TestPong
  }
}

class StatLogger(startTimeNs: Long, warmUp: FiniteDuration, steady: FiniteDuration,
                 loadActor: ActorRef, statsActor: ActorRef) extends Actor {
  import context.dispatcher
  // Starting 5 seconds early to test stats outside warmUp.
  val scheduler = context.system.scheduler.scheduleWithFixedDelay(
    warmUp - (5.seconds) + ((startTimeNs - System.nanoTime()).nanoseconds), 5.seconds, self, TestPing)

  def receive = {
    case TestPing =>
      loadActor ! GetStats
      statsActor ! GetStats
      if (((System.nanoTime() - startTimeNs).nanoseconds) > warmUp + steady)
        scheduler.cancel()

    case LoadStats(tpsSoFar) => println(s"Achieved TPS: $tpsSoFar")

    case CPUStats(avg, sDev) => println(s"CPU load $avg; SDEV $sDev")
  }
}
