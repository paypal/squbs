/*
 *  Copyright 2019 PayPal
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

import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}

import akka.NotUsed
import akka.actor.{Actor, ActorRef, ActorRefFactory, ActorSystem, Props}
import akka.pattern._
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{Flow, GraphDSL, MergeHub, RunnableGraph, Sink, Source}
import akka.util.Timeout
import org.scalatest.{FunSpec, Inside, Matchers}
import org.squbs.stream.PerpetualStreamMatValueSpecHelper.PerpStreamActors
import org.squbs.unicomplex._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

class PerpetualStreamMatValueSpec
extends FunSpec
with Matchers
with Inside {

  import PerpStreamActors._
  import PerpetualStreamMatValueSpecHelper._

  describe("PerpetualStreamMatValue matValue") {
    describe("Successful cases") {

      it("Sink[T, NotUsed]") {
        val to = FiniteDuration(1, TimeUnit.SECONDS)
        useSystem(classOf[SinkMaterializingStream]) {
          case Success(actor) =>
            implicit val timeout = Timeout(to)
            val queue = Await.result((actor ? Queue).mapTo[LinkedBlockingQueue[Long]], to)
            queue.poll(1, TimeUnit.SECONDS) should be(PerpetualStreamMatValueSpecHelper.payload)
          case _ => fail("Expected a success")
        }
      }

      describe("Cases where we examine the 'first' element, and it is a Sink[T, NotUsed]") {

        List(
          ("Product"       , classOf[GoodProductSinkMaterializingStream]),
          ("akka.japi.Pair", classOf[GoodJapiPairSinkMaterializingStream]),
          ("java.util.List", classOf[GoodJavaListSinkMaterializingStream])
        ).foreach { case (testName, clz) =>
          it(testName) {
            val to = FiniteDuration(1, TimeUnit.SECONDS)
            useSystem(clz) {
              case Success(actor) =>
                implicit val timeout = Timeout(to)
                val queue = Await.result((actor ? Queue).mapTo[LinkedBlockingQueue[Long]], to)
                queue.poll(1, TimeUnit.SECONDS) should be(PerpetualStreamMatValueSpecHelper.payload)
              case Failure(e) => fail("Expected a success", e)
            }
          }
        }
      }
    }

    describe("Error cases that throw ClassCastException") {

      describe("Cases where we examine the 'first' element, and it is NOT a Sink[T, NotUsed]") {
        List(
          ("Products"      , classOf[BadProductSinkMaterializingStream]),
          ("akka.japi.Pair", classOf[BadJapiPairSinkMaterializingStream]),
          ("java.util.List", classOf[BadJavaListSinkMaterializingStream])
        ).foreach { case (testName, clz) =>

          it (testName) {
            useSystem(clz) {
              case Failure(e) =>
                e shouldBe a[ClassCastException]
                e.getMessage should be(
                  s"Materialized value mismatch. First element should be a Sink. Found ${classOf[Integer].getName}."
                )
              case _ => fail("Expected a failure")
            }
          }
        }
      }

      it("Empty java.util.List") {
        useSystem(classOf[EmptyJavaListSinkMaterializingStream]) {
          case Failure(e) =>
            e shouldBe a[ClassCastException]
            e.getMessage should be(
              s"Materialized value mismatch. First element should be a Sink. Found an empty java.util.List."
            )
          case _ => fail("Expected a failure")
        }
      }

      it("All other cases") {
        useSystem(classOf[IntProducingStream]) {
          case Failure(e) =>
            e shouldBe a[ClassCastException]
            e.getMessage should be(
              "Materialized value mismatch. Should be a Sink or a Product/akka.japi.Pair/java.util.List " +
                s"with a Sink as its first element. Found ${classOf[Integer].getName}."
            )
          case _ => fail("Expected a failure")
        }
      }
    }
  }
}

object PerpetualStreamMatValueSpecHelper {

  import scala.concurrent.duration._
  val payload = 1L
  case object Queue
  implicit val timeout = Timeout(1.second)

  def useSystem[PC <: PerpStream[_]](perpStream: Class[PC])(fn: Try[ActorRef] => Unit): Unit = {
    implicit val actorSystem = ActorSystem()
    implicit val materializer = ActorMaterializer()

    // TODO Is it ok to violate actor creation guidelines here?
    val perpRef = actorSystem.actorOf(Props(perpStream), "act1")
    val someRef = actorSystem.actorOf(Props(new SomeActor(perpRef)), "act2")

    Await.result(someRef ? payload, 1.second) match {
      case Success(_) => fn(Success(perpRef))
      case Failure(e) => fn(Failure(e))
    }
    actorSystem.terminate()
  }

  trait PerpStream[T] extends PerpetualStream[T] {
    val batchQueue = new LinkedBlockingQueue[Long]()
    // https://stackoverflow.com/a/18469420
    override def receive= ({
      case Queue => sender() ! batchQueue
    }: Receive) orElse super.receive

    def addToBatch: Flow[Long, Long, NotUsed] = Flow[Long].map { l => batchQueue.add(l); l }
  }

  object PerpStreamActors {
    // TODO Can I not put these within the test:(:(
    // Seems like I can't due to some Akka actor construction rule.

    class SinkMaterializingStream extends PerpStream[Sink[Long, NotUsed]] {
      self ! Active
      override def streamGraph =
        MergeHub.source[Long].via(addToBatch).to(Sink.ignore)
    }

    /**
      * This has Sink[T, NotUsed] as the materialized value's first element.
      */
    class GoodProductSinkMaterializingStream extends PerpStream[(Sink[Long, NotUsed], Future[akka.Done])] {
      self ! Active
      override def streamGraph = {
        RunnableGraph.fromGraph(GraphDSL.create(MergeHub.source[Long], Sink.ignore)((_, _)) { implicit builder =>
          (mergeHubSource, sink) =>
            import GraphDSL.Implicits._
            mergeHubSource ~> addToBatch ~> sink
            ClosedShape
        })
      }
    }

    /**
      * This has Sink[T, NotUsed] as the materialized value's first element of a [[akka.japi.Pair]].
      */
    class GoodJapiPairSinkMaterializingStream
    extends PerpStream[akka.japi.Pair[Sink[Long, NotUsed], _]] {
      self ! Active
      override def streamGraph = {
        // an alternative to all the effort it took to do GoodProduct above..
        val sink: Sink[Long, NotUsed] = Flow[Long].via(addToBatch).to(Sink.ignore)
        Source.single(1).toMat(Sink.ignore)((_, _) => akka.japi.Pair(sink, 1))
      }
    }

    /**
      * This has Sink[T, NotUsed] as the materialized value's first element of a [[java.util.List]].
      */
    class GoodJavaListSinkMaterializingStream
    extends PerpStream[java.util.List[_]] {
      self ! Active
      override def streamGraph = {
        val sink: Sink[Long, NotUsed] = Flow[Long].via(addToBatch).to(Sink.ignore)
        Source.single(1).toMat(Sink.ignore)((_, _) => java.util.Arrays.asList(sink, 1))
      }
    }

    /**
      * This materializes a product that does NOT have Sink[T, NotUsed] as its first element.
      */
    class BadProductSinkMaterializingStream extends PerpStream[(Int, Any)] {
      self ! Active
      override def streamGraph = Source.single(1).toMat(Sink.ignore)((_, _) => (1, 2))
    }

    /**
      * This materializes an akka.japi.Pair that does NOT have Sink[T, NotUsed] as its first element.
      */
    class BadJapiPairSinkMaterializingStream extends PerpStream[akka.japi.Pair[Int, Any]] {
      self ! Active
      override def streamGraph =
        Source.single(1).toMat(Sink.ignore)((_, _) => akka.japi.Pair(1, 2))
    }

    /**
      * This materializes a java.util.List that does NOT have Sink[T, NotUsed] as its first element.
      */
    class BadJavaListSinkMaterializingStream extends PerpStream[java.util.List[Int]] {
      self ! Active
      override def streamGraph =
        Source.single(1).toMat(Sink.ignore)((_, _) => java.util.Arrays.asList(1, 2, 3))
    }

    /**
      * This materializes an empty java.util.List.
      */
    class EmptyJavaListSinkMaterializingStream extends PerpStream[java.util.List[_]] {
      self ! Active
      override def streamGraph =
        Source.single(1).toMat(Sink.ignore)((_, _) => java.util.Collections.emptyList())
    }

    /**
      * This materializes an Int (neither a Sink[T, NotUsed] or a product).
      */
    class IntProducingStream extends PerpStream[Int] {
      self ! Active
      override def streamGraph = Source.single(1).toMat(Sink.ignore)((_, _) => 6)
    }
  }

  class SomeActor(perpStream: ActorRef) extends Actor with PerpetualStreamMatValue[Long] {
    implicit val mat = ActorMaterializer()

    override def actorLookup(name: String)(implicit refFactory: ActorRefFactory, timeout: Timeout) =
      perpStream

    override def receive: Receive = {
      case l: Long => sender() ! Try { Source.single(l).to(matValue("any-name-works-see-impl")).run() }
    }
  }
}
