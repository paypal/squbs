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

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class OrchestratorSpec extends TestKit(ActorSystem("OrchestrationSpec"))
with ImplicitSender with AnyFunSpecLike with Matchers {

  describe ("The Orchestration DSL") {

    it ("Should submit all orchestration asynchronously and return result after finish") {
      system.actorOf(Props[TestOrchestrator]()) ! OrchestrationRequest("test")

      // Check for the submitted message
      val submitted = expectMsgType[SubmittedOrchestration](awaitMax)
      val submitTime = submitted.timeNs / 1000L
      println(s"Submission took $submitTime microseconds.")
      (submitTime / 1000L) should be < 230000L
      submitted.request should be ("test")

      // Check for the finished message
      val finished = expectMsgType[FinishedOrchestration](awaitMax)
      val finishTime = finished.timeNs / 1000L
      println(s"Orchestration took $finishTime microseconds.")
      finishTime should be > 230000L // 23 orchestrations with 10 millisecond delay each
      finished.request should be ("test")
      finished.lastId should be (23)
    }

    it ("Should submit all orchestration asynchronously and return a future of result resolved at finish") {
      system.actorOf(Props[TestAskOrchestrator]()) ! OrchestrationRequest("test")

      // Check for the submitted message
      val submitted = expectMsgType[SubmittedOrchestration](awaitMax)
      val submitTime = submitted.timeNs / 1000L
      println(s"Submission took $submitTime microseconds.")
      (submitTime / 1000L) should be < 230000L
      submitted.request should be ("test")

      // Check for the future of finish message
      val finishedF = expectMsgType[Future[FinishedOrchestration]](awaitMax)
      val finished = Await.result(finishedF, awaitMax)
      val finishTime = finished.timeNs / 1000L
      println(s"Orchestration took $finishTime microseconds.")
      finishTime should be > 230000L // 23 orchestrations with 10 millisecond delay each + 22 Future resolutions
      finished.request should be ("test")
      finished.lastId should be (45)
    }

    it ("Should have submission time close to 0 on repeat", SlowTest) {
      for (i <- 0 until 100) {
        system.actorOf(Props[TestOrchestrator]()) ! OrchestrationRequest("test")

        // Check for the submitted message
        val submitted = expectMsgType[SubmittedOrchestration](awaitMax)
        val submitTime = submitted.timeNs / 1000L
        println(s"Submission took $submitTime microseconds.")
        (submitTime / 1000000L) should be < 20000L
        submitted.request should be("test")

        // Check for the finished message
        val finished = expectMsgType[FinishedOrchestration](awaitMax)
        val finishTime = finished.timeNs / 1000L
        println(s"Orchestration took $finishTime microseconds.")
        finishTime should be > 230000L // 23 orchestrations with 10 millisecond delay each
        finished.request should be("test")
        finished.lastId should be(23)
      }
    }
  }
}

case class OrchestrationRequest(request: String)
case class OrchestrationResponse(response: String)
case class ServiceRequest(id: Long, delay: FiniteDuration)
case class ServiceResponse(id: Long)
case class SubmittedOrchestration(request: String, timeNs: Long)
case class FinishedOrchestration(lastId: Long, request: String, timeNs: Long)

class TestOrchestrator extends Actor with Orchestrator with RequestFunctions with ActorLogging {

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

    val f0 = loadResponse(delay)
    val f1 = f0 >> loadResponse1(delay)
    val f2 = (f0, f1) >> loadResponse2(delay)
    val f3 = (f0, f1, f2) >> loadResponse3(delay)
    val f4 = (f0, f1, f2, f3) >> loadResponse4(delay)
    val f5 = (f0, f1, f2, f3, f4) >> loadResponse5(delay)
    val f6 = (f0, f1, f2, f3, f4, f5) >> loadResponse6(delay)
    val f7 = (f0, f1, f2, f3, f4, f5, f6) >> loadResponse7(delay)
    val f8 = (f0, f1, f2, f3, f4, f5, f6, f7) >> loadResponse8(delay)
    val f9 = (f0, f1, f2, f3, f4, f5, f6, f7, f8) >> loadResponse9(delay)
    val f10 = (f0, f1, f2, f3, f4, f5, f6, f7, f8, f9) >> loadResponse10(delay)
    val f11 = (f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10) >> loadResponse11(delay)
    val f12 = (f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11) >> loadResponse12(delay)
    val f13 = (f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12) >> loadResponse13(delay)
    val f14 = (f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13) >> loadResponse14(delay)
    val f15 = (f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14) >> loadResponse15(delay)
    val f16 = (f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15) >> loadResponse16(delay)
    val f17 = (f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16) >> loadResponse17(delay)
    val f18 = (f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17) >> loadResponse18(delay)
    val f19 = (f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18
      ) >> loadResponse19(delay)
    val f20 = (f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18, f19
      ) >> loadResponse20(delay)
    val f21 = (f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18, f19, f20
      ) >> loadResponse21(delay)
    val f22 = (f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18, f19, f20, f21
      ) >> loadResponse22(delay)

    // This should happen right after all orchestration requests are submitted
    requester ! SubmittedOrchestration(request, System.nanoTime() - startTime)

    // But this should happen after all orchestrations are finished
    for (lastId <- f22) {
      requester ! FinishedOrchestration(lastId, request, System.nanoTime() - startTime)
      context stop self
    }
  }
}

trait RequestFunctions {
  this: Actor with Orchestrator =>

  object Requests {

    val service = context.actorOf(Props[ServiceEmulator]())

    def loadResponse(delay: FiniteDuration): OFuture[Long] = {
      val promise = OPromise[Long]()
      val id = nextMessageId
      service ! ServiceRequest(id, delay)
      expectOnce {
        case ServiceResponse(`id`) => promise success id
      }
      promise.future
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


class TestAskOrchestrator extends Actor with Orchestrator with ActorLogging {

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

    val f0 = loadResponse(delay)
    val f1 = f0 >> loadResponse1(delay)
    val f2 = (f0, f1) >> loadResponse2(delay)
    val f3 = (f0, f1, f2) >> loadResponse3(delay)
    val f4 = (f0, f1, f2, f3) >> loadResponse4(delay)
    val f5 = (f0, f1, f2, f3, f4) >> loadResponse5(delay)
    val f6 = (f0, f1, f2, f3, f4, f5) >> loadResponse6(delay)
    val f7 = (f0, f1, f2, f3, f4, f5, f6) >> loadResponse7(delay)
    val f8 = (f0, f1, f2, f3, f4, f5, f6, f7) >> loadResponse8(delay)
    val f9 = (f0, f1, f2, f3, f4, f5, f6, f7, f8) >> loadResponse9(delay)
    val f10 = (f0, f1, f2, f3, f4, f5, f6, f7, f8, f9) >> loadResponse10(delay)
    val f11 = (f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10) >> loadResponse11(delay)
    val f12 = (f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11) >> loadResponse12(delay)
    val f13 = (f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12) >> loadResponse13(delay)
    val f14 = (f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13) >> loadResponse14(delay)
    val f15 = (f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14) >> loadResponse15(delay)
    val f16 = (f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15) >> loadResponse16(delay)
    val f17 = (f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16) >> loadResponse17(delay)
    val f18 = (f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17) >> loadResponse18(delay)
    val f19 = (f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18
      ) >> loadResponse19(delay)
    val f20 = (f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18, f19
      ) >> loadResponse20(delay)
    val f21 = (f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18, f19, f20
      ) >> loadResponse21(delay)
    val f22 = (f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18, f19, f20, f21
      ) >> loadResponse22(delay)

    // This should happen right after all orchestration requests are submitted
    import context.dispatcher
    requester ! SubmittedOrchestration(request, System.nanoTime() - startTime)
    requester ! ((f22: Future[Long]) map { id => FinishedOrchestration(id, request, System.nanoTime() - startTime) })
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




class ServiceEmulator extends Actor {

  import context.dispatcher

  def receive: Receive = {
    case ServiceRequest(id, duration) =>
      context.system.scheduler.scheduleOnce(duration, sender(), ServiceResponse(id))
  }
}
