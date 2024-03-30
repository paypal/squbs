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

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.{Actor, ActorRef, ActorRefFactory, ActorSystem, Props, Status}
import org.apache.pekko.pattern._
import org.apache.pekko.stream.ClosedShape
import org.apache.pekko.stream.scaladsl.{Flow, GraphDSL, Keep, MergeHub, RunnableGraph, Sink, Source}
import org.apache.pekko.util.Timeout
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}
import org.squbs.stream.PerpetualStreamMatValueSpecHelper.PerpStreamActors
import org.squbs.unicomplex._

import java.util.concurrent.LinkedBlockingQueue
import scala.concurrent.{Await, Future}
import scala.reflect.{ClassTag, classTag}
import scala.util.{Failure, Success, Try}

class PerpetualStreamMatValueSpec extends AnyFunSpec with Matchers with BeforeAndAfterAll with Eventually {

  import PerpStreamActors._
  import PerpetualStreamMatValueSpecHelper._


  implicit val system = ActorSystem(this.getClass.getSimpleName)

  private val timeout = Timeout(PerpetualStreamMatValueSpecHelper.timeoutDuration)
  implicit override val patienceConfig = PatienceConfig(timeout = Span(3, Seconds))

  override def afterAll(): Unit = {
    system.terminate()
  }

  describe("PerpetualStreamMatValue matValue") {
    describe("Successful cases") {

      it("Sink[T, NotUsed]") {
        implicit val to = timeout
        useSystem[SinkMaterializingStream] {
          case Success(actor) =>
            eventually {
              val actorState = Await.result((actor ? StateRequest).mapTo[List[Long]], timeoutDuration)
              actorState.last should be(PerpetualStreamMatValueSpecHelper.payload)
            }
          case Failure(e) => fail("Expected a Success", e)
        }
      }

      describe("Cases where we examine the 'first' element, and it is a Sink[T, NotUsed]") {

        List(
          "Product"        -> classTag[GoodProductSinkMaterializingStream],
          "org.apache.pekko.japi.Pair" -> classTag[GoodJapiPairSinkMaterializingStream],
          "java.util.List" -> classTag[GoodJavaListSinkMaterializingStream]
        ).foreach { case (testName, ct) =>
          implicit val to = timeout
          implicit val ict = ct
          it(testName) {
            useSystem {
              case Success(actor) =>
                eventually {
                  val actorState = Await.result((actor ? StateRequest).mapTo[List[Long]], timeoutDuration)
                  actorState.last should be(PerpetualStreamMatValueSpecHelper.payload)
                }
              case Failure(e) => fail("Expected a success", e)
            }
          }
        }
      }
    }

    describe("Error cases that throw ClassCastException") {

      describe("Cases where we examine the 'first' element, and it is NOT a Sink[T, NotUsed]") {
        List(
          "Products"       -> classTag[BadProductSinkMaterializingStream],
          "org.apache.pekko.japi.Pair" -> classTag[BadJapiPairSinkMaterializingStream],
          "java.util.List" -> classTag[BadJavaListSinkMaterializingStream]
        ).foreach { case (testName, ct) =>
          implicit val ict = ct
          it (testName) {
            useSystem {
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
        useSystem[EmptyJavaListSinkMaterializingStream] {
          case Failure(e) =>
            e shouldBe a[ClassCastException]
            e.getMessage should be(
              s"Materialized value mismatch. First element should be a Sink. Found an empty java.util.List."
            )
          case _ => fail("Expected a failure")
        }
      }

      it("All other cases") {
        useSystem[IntProducingStream] {
          case Failure(e) =>
            e shouldBe a[ClassCastException]
            e.getMessage should be(
              "Materialized value mismatch. Should be a Sink or a Product/org.apache.pekko.japi.Pair/java.util.List " +
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
  case object StateRequest

  val timeoutDuration = 1.second
  implicit val timeout = Timeout(timeoutDuration)

  def useSystem[PC <: PerpStream[_] : ClassTag](fn: Try[ActorRef] => Unit)
    (implicit system: ActorSystem): Unit = {

    val perpRef = system.actorOf(Props[PC]())
    val someRef = system.actorOf(Props(new SomeActor(perpRef)))

    Try(Await.result(someRef ? payload, timeoutDuration)) match {
      case Success(l) => fn(Success(perpRef))
      case Failure(e) => fn(Failure(e))
    }
    system.stop(someRef)
    system.stop(perpRef)
  }

  trait PerpStream[T] extends PerpetualStream[T] {

    private val stateQueue = new LinkedBlockingQueue[Long]()

    // https://stackoverflow.com/a/18469420
    override def receive= ({
      case StateRequest => sender() ! stateQueue.toArray.toList
    }: Receive) orElse super.receive

    def addToBatch: Flow[Long, Long, NotUsed] = Flow[Long].map { l => stateQueue.add(l); l }

  }

  object PerpStreamActors {
    // TODO Can I not put these within the test:(:(
    // Seems like I can't due to some Pekko actor construction rule.

    class SinkMaterializingStream extends PerpStream[Sink[Long, NotUsed]] {
      self ! Active
      override def streamGraph =
        MergeHub.source[Long].via(addToBatch).to(Sink.ignore)
    }

    /**
      * This has Sink[T, NotUsed] as the materialized value's first element.
      */
    class GoodProductSinkMaterializingStream extends PerpStream[(Sink[Long, NotUsed], Future[org.apache.pekko.Done])] {
      self ! Active
      override def streamGraph = {
        RunnableGraph.fromGraph(GraphDSL.createGraph(MergeHub.source[Long], Sink.ignore)((_, _)) { implicit builder =>
          (mergeHubSource, sink) =>
            import GraphDSL.Implicits._
            mergeHubSource ~> addToBatch ~> sink
            ClosedShape
        })
      }
    }

    /**
      * This has Sink[T, NotUsed] as the materialized value's first element of a [[org.apache.pekko.japi.Pair]].
      */
    class GoodJapiPairSinkMaterializingStream
      extends PerpStream[org.apache.pekko.japi.Pair[Sink[Long, NotUsed], _]] {
      self ! Active
      override def streamGraph = {
        // an alternative to all the effort it took to do GoodProduct above..
        val sink: Sink[Long, NotUsed] = Flow[Long].via(addToBatch).to(Sink.ignore)
        Source.single(1).toMat(Sink.ignore)((_, _) => org.apache.pekko.japi.Pair(sink, 1))
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
      * This materializes an org.apache.pekko.japi.Pair that does NOT have Sink[T, NotUsed] as its first element.
      */
    class BadJapiPairSinkMaterializingStream extends PerpStream[org.apache.pekko.japi.Pair[Int, Any]] {
      self ! Active
      override def streamGraph =
        Source.single(1).toMat(Sink.ignore)((_, _) => org.apache.pekko.japi.Pair(1, 2))
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

  class SomeActor(perpStream: ActorRef)
  extends Actor
  with PerpetualStreamMatValue[Long] {

    import context.{dispatcher, system}

    override def actorLookup(name: String)(implicit refFactory: ActorRefFactory, timeout: Timeout) =
      perpStream

    override def receive: Receive = {
      case l: Long =>
        Try {
          Source.single(l)
            .alsoTo(matValue("any-name-works-see-impl"))
            .toMat(Sink.ignore)(Keep.right)
            .run()
        } match {
          case Success(f) => f pipeTo sender()
          case Failure(t) => sender() ! Status.Failure(t)
        }
    }
  }
}
