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
import org.apache.pekko.pattern.ask
import org.apache.pekko.testkit.{ImplicitSender, TestKit}
import org.scalatest.funspec.AnyFunSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.testkit.SlowTest
import org.squbs.testkit.Timeouts._
import org.squbs.testkit.stress._

import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Unlike OrchestratorStressTest which stresses all orchestration functions to the max allows no concurrency,
 * OrchestratorStress2Test simulates a real world ViewItem orchestration scenario which has smaller number of
 * callbacks and a large amount of allowed concurrency.
 */
class OrchestratorStress2Test extends TestKit(ActorSystem("OrchestrationStress2Test"))
with ImplicitSender with AnyFunSpecLike with Matchers {

  val ir = 500
  val warmUp = 2 minutes
  val steady = 3 minutes

  it(s"Should orchestrate synchronously at $ir submissions/sec, comparing CPU load to the DSL", SlowTest) {

    val startTime = System.nanoTime()

    val loadActor = system.actorOf(Props[LoadActor]())
    val statsActor = system.actorOf(Props[CPUStatsActor]())
    loadActor ! StartLoad(startTime, ir, warmUp, steady){
      system.actorOf(Props[SimpleForComprehension2Actor]()) ! OrchestrationRequest("SyncLoadTest")
    }
    statsActor ! StartStats(startTime, warmUp, steady, 5.seconds)

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
      system.actorOf(Props[Test2Orchestrator]()) ! OrchestrationRequest("LoadTest")
    }
    statsActor ! StartStats(startTime, warmUp, steady, 5.seconds)

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
      system.actorOf(Props[TestAsk2Orchestrator]()) ! OrchestrationRequest("LoadTest")
    }
    statsActor ! StartStats(startTime, warmUp, steady, 5.seconds)

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

        case _ => false
      }
    }
    loadActor ! PoisonPill
    statsActor ! PoisonPill
  }
}

class SimpleForComprehension2Actor extends Actor with Orchestrator with RequestFunctions {

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

    val delay = 10.milliseconds

    val startTime = System.nanoTime()

    for {
      v0  <- loadResponse(delay)
      v1  <- loadResponse(delay)
      v2  <- loadResponse(delay)
      v3  <- loadResponse(delay)
      v4  <- loadResponse1(delay)(v2)
      v5  <- loadResponse1(delay)(v4)
      v6  <- loadResponse1(delay)(v4)
      v7  <- loadResponse1(delay)(v4)
      v8  <- loadResponse1(delay)(v4)
      v9  <- loadResponse1(delay)(v4)
      v10 <- loadResponse1(delay)(v4)
      v11 <- loadResponse1(delay)(v4)
      v12 <- loadResponse1(delay)(v5)
      v13 <- loadResponse2(delay)(v4, v8)
      v14 <- loadResponse2(delay)(v2, v4)
      v15 <- loadResponse2(delay)(v4, v3)
      v16 <- loadResponse3(delay)(v4, v8, v9)
      v17 <- loadResponse3(delay)(v4, v8, v9)
    } {
      val lastId = Vector(v0, v1, v6, v7, v10, v11, v12, v13, v14, v15, v16, v17).max
      requester ! FinishedOrchestration(lastId, request, System.nanoTime() - startTime)
      context stop self
    }
  }
}

class Test2Orchestrator extends Actor with Orchestrator with RequestFunctions with ActorLogging {

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

    val delay = 10.milliseconds

    val startTime = System.nanoTime()

    val f0 = loadResponse(delay)
    val f1 = loadResponse(delay)
    val f2 = loadResponse(delay)
    val f3 = loadResponse(delay)
    val f4 = f2 >> loadResponse1(delay)
    val f5 = f4 >> loadResponse1(delay)
    val f6 = f4 >> loadResponse1(delay)
    val f7 = f4 >> loadResponse1(delay)
    val f8 = f4 >> loadResponse1(delay)
    val f9 = f4 >> loadResponse1(delay)
    val f10 = f4 >> loadResponse1(delay)
    val f11 = f4 >> loadResponse1(delay)
    val f12 = f5 >> loadResponse1(delay)
    val f13 = (f4, f8) >> loadResponse2(delay)
    val f14 = (f2, f4) >> loadResponse2(delay)
    val f15 = (f4, f3) >> loadResponse2(delay)
    val f16 = (f4, f8, f9) >> loadResponse3(delay)
    val f17 = (f4, f8, f9) >> loadResponse3(delay)

    // This should happen right after all orchestration requests are submitted
    requester ! SubmittedOrchestration(request, System.nanoTime() - startTime)

    // But this should happen after all orchestrations are finished
    for {
      v0 <- f0
      v1 <- f1
      v6 <- f6
      v7 <- f7
      v10 <- f10
      v11 <- f11
      v12 <- f12
      v13 <- f13
      v14 <- f14
      v15 <- f15
      v16 <- f16
      v17 <- f17
    } {
      val lastId = Vector(v0, v1, v6, v7, v10, v11, v12, v13, v14, v15, v16, v17).max
      requester ! FinishedOrchestration(lastId, request, System.nanoTime() - startTime)
      context stop self
    }
  }
}

class TestAsk2Orchestrator extends Actor with Orchestrator with ActorLogging {

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

    val delay = 10.milliseconds

    val startTime = System.nanoTime()

    val f0 = loadResponse(delay)
    val f1 = loadResponse(delay)
    val f2 = loadResponse(delay)
    val f3 = loadResponse(delay)
    val f4 = f2 >> loadResponse1(delay)
    val f5 = f4 >> loadResponse1(delay)
    val f6 = f4 >> loadResponse1(delay)
    val f7 = f4 >> loadResponse1(delay)
    val f8 = f4 >> loadResponse1(delay)
    val f9 = f4 >> loadResponse1(delay)
    val f10 = f4 >> loadResponse1(delay)
    val f11 = f4 >> loadResponse1(delay)
    val f12 = f5 >> loadResponse1(delay)
    val f13 = (f4, f8) >> loadResponse2(delay)
    val f14 = (f2, f4) >> loadResponse2(delay)
    val f15 = (f4, f3) >> loadResponse2(delay)
    val f16 = (f4, f8, f9) >> loadResponse3(delay)
    val f17 = (f4, f8, f9) >> loadResponse3(delay)

    // This should happen right after all orchestration requests are submitted
    requester ! SubmittedOrchestration(request, System.nanoTime() - startTime)

    // But this should happen after all orchestrations are finished
    for {
      v0 <- f0
      v1 <- f1
      v6 <- f6
      v7 <- f7
      v10 <- f10
      v11 <- f11
      v12 <- f12
      v13 <- f13
      v14 <- f14
      v15 <- f15
      v16 <- f16
      v17 <- f17
    } {
      val lastId = Vector(v0, v1, v6, v7, v10, v11, v12, v13, v14, v15, v16, v17).max
      requester ! FinishedOrchestration(lastId, request, System.nanoTime() - startTime)
      context stop self
    }

  }

  object Requests {

    val service = context.actorOf(Props[ServiceEmulator]())

    def loadResponse(delay: FiniteDuration): OFuture[Long] = {
      import context.dispatcher
      (service ? ServiceRequest(nextMessageId, delay)).mapTo[ServiceResponse] map (_.id)
    }


    def loadResponse1(delay: FiniteDuration)(prevId: Long): OFuture[Long] = loadResponse(delay)

    def loadResponse2(delay: FiniteDuration)(prevId: Long, prevId2: Long): OFuture[Long] = loadResponse(delay)

    def loadResponse3(delay: FiniteDuration)(prevId: Long, prevId2: Long, prevId3: Long): OFuture[Long] =
      loadResponse(delay)

    def loadResponse4(delay: FiniteDuration)(prevId: Long, prevId2: Long, prevId3: Long, prevId4: Long):
    OFuture[Long] = loadResponse(delay)

    def loadResponse5(delay: FiniteDuration)(prevId: Long, prevId2: Long, prevId3: Long, prevId4: Long, prevId5: Long):
    OFuture[Long] = loadResponse(delay)

    def loadResponse6(delay: FiniteDuration)(prevId: Long, prevId2: Long, prevId3: Long, prevId4: Long, prevId5: Long,
                                             prevId6: Long): OFuture[Long] = loadResponse(delay)

    def loadResponse7(delay: FiniteDuration)(prevId: Long, prevId2: Long, prevId3: Long, prevId4: Long, prevId5: Long,
                                             prevId6: Long, prevId7: Long): OFuture[Long] = loadResponse(delay)

    def loadResponse8(delay: FiniteDuration)(prevId: Long, prevId2: Long, prevId3: Long, prevId4: Long, prevId5: Long,
                                             prevId6: Long, prevId7: Long, prevId8: Long): OFuture[Long] =
      loadResponse(delay)

    def loadResponse9(delay: FiniteDuration)(prevId: Long, prevId2: Long, prevId3: Long, prevId4: Long, prevId5: Long,
                                             prevId6: Long, prevId7: Long, prevId8: Long, prevId9: Long):
    OFuture[Long] = loadResponse(delay)

    def loadResponse10(delay: FiniteDuration)(prevId: Long, prevId2: Long, prevId3: Long, prevId4: Long, prevId5: Long,
                                              prevId6: Long, prevId7: Long, prevId8: Long, prevId9: Long,
                                              prevId10: Long): OFuture[Long] = loadResponse(delay)

    def loadResponse11(delay: FiniteDuration)(prevId: Long, prevId2: Long, prevId3: Long, prevId4: Long, prevId5: Long,
                                              prevId6: Long, prevId7: Long, prevId8: Long, prevId9: Long,
                                              prevId10: Long, prevId11: Long): OFuture[Long] = loadResponse(delay)

    def loadResponse12(delay: FiniteDuration)(prevId: Long, prevId2: Long, prevId3: Long, prevId4: Long, prevId5: Long,
                                              prevId6: Long, prevId7: Long, prevId8: Long, prevId9: Long,
                                              prevId10: Long, prevId11: Long, prevId12: Long): OFuture[Long] =
      loadResponse(delay)

    def loadResponse13(delay: FiniteDuration)(prevId: Long, prevId2: Long, prevId3: Long, prevId4: Long, prevId5: Long,
                                              prevId6: Long, prevId7: Long, prevId8: Long, prevId9: Long,
                                              prevId10: Long, prevId11: Long, prevId12: Long, prevId13: Long):
    OFuture[Long] = loadResponse(delay)

    def loadResponse14(delay: FiniteDuration)(prevId: Long, prevId2: Long, prevId3: Long, prevId4: Long, prevId5: Long,
                                              prevId6: Long, prevId7: Long, prevId8: Long, prevId9: Long,
                                              prevId10: Long, prevId11: Long, prevId12: Long, prevId13: Long,
                                              prevId14: Long): OFuture[Long] = loadResponse(delay)

    def loadResponse15(delay: FiniteDuration)(prevId: Long, prevId2: Long, prevId3: Long, prevId4: Long, prevId5: Long,
                                              prevId6: Long, prevId7: Long, prevId8: Long, prevId9: Long,
                                              prevId10: Long, prevId11: Long, prevId12: Long, prevId13: Long,
                                              prevId14: Long, prevId15: Long): OFuture[Long] = loadResponse(delay)

    def loadResponse16(delay: FiniteDuration)(prevId: Long, prevId2: Long, prevId3: Long, prevId4: Long, prevId5: Long,
                                              prevId6: Long, prevId7: Long, prevId8: Long, prevId9: Long,
                                              prevId10: Long, prevId11: Long, prevId12: Long, prevId13: Long,
                                              prevId14: Long, prevId15: Long, prevId16: Long): OFuture[Long] =
      loadResponse(delay)

    def loadResponse17(delay: FiniteDuration)(prevId: Long, prevId2: Long, prevId3: Long, prevId4: Long, prevId5: Long,
                                              prevId6: Long, prevId7: Long, prevId8: Long, prevId9: Long,
                                              prevId10: Long, prevId11: Long, prevId12: Long, prevId13: Long,
                                              prevId14: Long, prevId15: Long, prevId16: Long, prevId17: Long):
    OFuture[Long] = loadResponse(delay)

    def loadResponse18(delay: FiniteDuration)(prevId: Long, prevId2: Long, prevId3: Long, prevId4: Long, prevId5: Long,
                                              prevId6: Long, prevId7: Long, prevId8: Long, prevId9: Long,
                                              prevId10: Long, prevId11: Long, prevId12: Long, prevId13: Long,
                                              prevId14: Long, prevId15: Long, prevId16: Long, prevId17: Long,
                                              prevId18: Long): OFuture[Long] = loadResponse(delay)

    def loadResponse19(delay: FiniteDuration)(prevId: Long, prevId2: Long, prevId3: Long, prevId4: Long, prevId5: Long,
                                              prevId6: Long, prevId7: Long, prevId8: Long, prevId9: Long,
                                              prevId10: Long, prevId11: Long, prevId12: Long, prevId13: Long,
                                              prevId14: Long, prevId15: Long, prevId16: Long, prevId17: Long,
                                              prevId18: Long, prevId19: Long): OFuture[Long] = loadResponse(delay)

    def loadResponse20(delay: FiniteDuration)(prevId: Long, prevId2: Long, prevId3: Long, prevId4: Long, prevId5: Long,
                                              prevId6: Long, prevId7: Long, prevId8: Long, prevId9: Long,
                                              prevId10: Long, prevId11: Long, prevId12: Long, prevId13: Long,
                                              prevId14: Long, prevId15: Long, prevId16: Long, prevId17: Long,
                                              prevId18: Long, prevId19: Long, prevId20: Long): OFuture[Long] =
      loadResponse(delay)

    def loadResponse21(delay: FiniteDuration)(prevId: Long, prevId2: Long, prevId3: Long, prevId4: Long, prevId5: Long,
                                              prevId6: Long, prevId7: Long, prevId8: Long, prevId9: Long,
                                              prevId10: Long, prevId11: Long, prevId12: Long, prevId13: Long,
                                              prevId14: Long, prevId15: Long, prevId16: Long, prevId17: Long,
                                              prevId18: Long, prevId19: Long, prevId20: Long, prevId21: Long):
    OFuture[Long] = loadResponse(delay)

    def loadResponse22(delay: FiniteDuration)(prevId: Long, prevId2: Long, prevId3: Long, prevId4: Long, prevId5: Long,
                                              prevId6: Long, prevId7: Long, prevId8: Long, prevId9: Long,
                                              prevId10: Long, prevId11: Long, prevId12: Long, prevId13: Long,
                                              prevId14: Long, prevId15: Long, prevId16: Long, prevId17: Long,
                                              prevId18: Long, prevId19: Long, prevId20: Long, prevId21: Long,
                                              prevId22: Long): OFuture[Long] = loadResponse(delay)

  }
}

