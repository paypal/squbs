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

package org.squbs.pattern.orchestration

import org.apache.pekko.actor._
import org.apache.pekko.testkit.{ImplicitSender, TestKit}
import org.scalatest.funspec.AnyFunSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.testkit.SlowTest
import org.squbs.testkit.Timeouts._
import org.squbs.testkit.stress._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class OrchestratorStressTest extends TestKit(ActorSystem("OrchestrationStressTest"))
with ImplicitSender with AnyFunSpecLike with Matchers {

  val ir = 500
  val warmUp = 2 minutes
  val steady = 3 minutes

  it(s"Should orchestrate synchronously at $ir submissions/sec, comparing CPU load to the DSL", SlowTest) {

    val startTime = System.nanoTime()

    val loadActor = system.actorOf(Props[LoadActor]())
    val statsActor = system.actorOf(Props[CPUStatsActor]())
    loadActor ! StartLoad(startTime, ir, warmUp, steady){
      system.actorOf(Props[SimpleForComprehensionActor]()) ! OrchestrationRequest("SyncLoadTest")
    }
    statsActor ! StartStats(startTime, warmUp, steady, 5 seconds)

    var sumFinishTime = 0L
    var sumFinishCount = 0L

    for (i <- 0 to 1) {
      fishForMessage(warmUp + steady + awaitMax) {
        case LoadStats(tps) =>
          println(s"Achieved $tps TPS")
          println(s"Avg time to finish: ${sumFinishTime / (1000000d * sumFinishCount)} ms")
          tps should be > (ir * 0.95) // Within 5% of IR
          true

        case CPUStats(avg, sDev) =>
          println(s"CPULoad $avg; Standard Deviation $sDev")
          true

        case FinishedOrchestration(_, _, time) =>
          val currentTime = System.nanoTime() - startTime
          if (currentTime > warmUp.toNanos && currentTime <= (warmUp + steady).toNanos) {
            sumFinishTime += time
            sumFinishCount += 1
          }
          false

        case _ => false
      }
    }
    loadActor ! PoisonPill
    statsActor ! PoisonPill
  }

  it(s"Should orchestrate asynchronously at $ir submissions/sec", SlowTest) {

    val startTime = System.nanoTime()

    val loadActor = system.actorOf(Props[LoadActor]())
    val statsActor = system.actorOf(Props[CPUStatsActor]())
    loadActor ! StartLoad(startTime, ir, warmUp, steady){
      system.actorOf(Props[TestOrchestrator]()) ! OrchestrationRequest("LoadTest")
    }
    statsActor ! StartStats(startTime, warmUp, steady, 5 seconds)

    var sumSubmitTime = 0L
    var sumSubmitCount = 0L

    var sumFinishTime = 0L
    var sumFinishCount = 0L

    for (i <- 0 to 1) {
      fishForMessage(warmUp + steady + awaitMax) {
        case LoadStats(tps) =>
          println(s"Achieved $tps TPS")
          println(s"Avg submit time: ${sumSubmitTime / (1000000d * sumSubmitCount)} ms")
          println(s"Avg time to finish: ${sumFinishTime / (1000000d * sumFinishCount)} ms")
          tps should be > (ir * 0.95) // Within 5% of IR
          true

        case CPUStats(avg, sDev) =>
          println(s"CPULoad $avg; Standard Deviation $sDev")
          true

        case SubmittedOrchestration(_, time) =>
          val currentTime = System.nanoTime() - startTime
          if (currentTime > warmUp.toNanos && currentTime <= steady.toNanos) {
            sumSubmitTime += time
            sumSubmitCount += 1
          }
          false

        case FinishedOrchestration(_, _, time) =>
          val currentTime = System.nanoTime() - startTime
          if (currentTime > warmUp.toNanos && currentTime <= (warmUp + steady).toNanos) {
            sumFinishTime += time
            sumFinishCount += 1
          }
          false
      }
    }
    loadActor ! PoisonPill
    statsActor ! PoisonPill
  }

  it(s"Should orchestrate asynchronously using ask at $ir submissions/sec", SlowTest) {

    val startTime = System.nanoTime()

    val loadActor = system.actorOf(Props[LoadActor]())
    val statsActor = system.actorOf(Props[CPUStatsActor]())
    loadActor ! StartLoad(startTime, ir, warmUp, steady){
      system.actorOf(Props[TestAskOrchestrator]()) ! OrchestrationRequest("LoadTest")
    }
    statsActor ! StartStats(startTime, warmUp, steady, 5 seconds)

    var sumSubmitTime = 0L
    var sumSubmitCount = 0L

    var sumFinishTime = 0L
    var sumFinishCount = 0L

    for (i <- 0 to 1) {
      fishForMessage(warmUp + steady + awaitMax) {
        case LoadStats(tps) =>
          println(s"Achieved $tps TPS")
          println(s"Avg submit time: ${sumSubmitTime / (1000000d * sumSubmitCount)} ms")
          println(s"Avg time to finish: ${sumFinishTime / (1000000d * sumFinishCount)} ms")
          tps should be > (ir * 0.95) // Within 5% of IR
          true

        case CPUStats(avg, sDev) =>
          println(s"CPULoad $avg; Standard Deviation $sDev")
          true

        case SubmittedOrchestration(_, time) =>
          val currentTime = System.nanoTime() - startTime
          if (currentTime > warmUp.toNanos && currentTime <= steady.toNanos) {
            sumSubmitTime += time
            sumSubmitCount += 1
          }
          false

        case finishedF: Future[FinishedOrchestration] =>
          val finished = Await.result(finishedF, warmUp + steady + (20 seconds))
          val currentTime = System.nanoTime() - startTime
          if (currentTime > warmUp.toNanos && currentTime <= (warmUp + steady).toNanos) {
            sumFinishTime += finished.timeNs
            sumFinishCount += 1
          }
          false

        case _ => false
      }
    }
    loadActor ! PoisonPill
    statsActor ! PoisonPill
  }
}


class SimpleForComprehensionActor extends Actor with Orchestrator with RequestFunctions {

  // Expecting the initial request
  expectOnce {
    case OrchestrationRequest(request) =>
      orchestrate(sender(), request)
  }

  /**
   * The "orchestrate" function saves the original requester/sender and processes the orchestration.
   * @param requester The original sender
   * @param request   The request message
   */
  def orchestrate(requester: ActorRef, request: String): Unit = {

    import Requests._

    val delay = 10 milliseconds

    val startTime = System.nanoTime()

    for {
      v0 <- loadResponse(delay)
      v1 <- loadResponse1(delay)(v0)
      v2 <- loadResponse2(delay)(v0, v1)
      v3 <- loadResponse3(delay)(v0, v1, v2)
      v4 <- loadResponse4(delay)(v0, v1, v2, v3)
      v5 <- loadResponse5(delay)(v0, v1, v2, v3, v4)
      v6 <- loadResponse6(delay)(v0, v1, v2, v3, v4, v5)
      v7 <- loadResponse7(delay)(v0, v1, v2, v3, v4, v5, v6)
      v8 <- loadResponse8(delay)(v0, v1, v2, v3, v4, v5, v6, v7)
      v9 <- loadResponse9(delay)(v0, v1, v2, v3, v4, v5, v6, v7, v8)
      v10 <- loadResponse10(delay)(v0, v1, v2, v3, v4, v5, v6, v7, v8, v9)
      v11 <- loadResponse11(delay)(v0, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10)
      v12 <- loadResponse12(delay)(v0, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11)
      v13 <- loadResponse13(delay)(v0, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12)
      v14 <- loadResponse14(delay)(v0, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13)
      v15 <- loadResponse15(delay)(v0, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14)
      v16 <- loadResponse16(delay)(v0, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15)
      v17 <- loadResponse17(delay)(v0, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16)
      v18 <- loadResponse18(delay)(v0, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17)
      v19 <- loadResponse19(delay)(v0, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18)
      v20 <- loadResponse20(delay)(v0, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19)
      v21 <- loadResponse21(delay)(v0, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20)
      v22 <- loadResponse22(delay)(v0, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21)
    } {
      requester ! FinishedOrchestration(v22, request, System.nanoTime() - startTime)
      context stop self
    }
  }
}
