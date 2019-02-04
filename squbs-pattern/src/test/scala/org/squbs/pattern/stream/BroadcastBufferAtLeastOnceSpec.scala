/*
 * Copyright 2017 PayPal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.squbs.pattern.stream

import java.util.concurrent.atomic.AtomicInteger

import akka.Done
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, RunnableGraph, Sink, Source}
import akka.stream.{AbruptTerminationException, ActorMaterializer, ClosedShape, ThrottleMode}
import akka.util.ByteString
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import org.squbs.testkit.Timeouts._

import scala.concurrent.{Await, Promise}
import scala.reflect._

abstract class BroadcastBufferAtLeastOnceSpec[T: ClassTag, Q <: QueueSerializer[T] : Manifest]
(typeName: String) extends FlatSpec with Matchers with BeforeAndAfterAll with Eventually {

  implicit val system = ActorSystem(s"Broadcast${typeName}BufferAtLeastOnceSpec", PersistentBufferSpec.testConfig)
  implicit val mat = ActorMaterializer()
  implicit val serializer = QueueSerializer[T]()
  import StreamSpecUtil._
  import system.dispatcher

  def createElement(n: Int): T

  def format(element: T): String

  val transform = Flow[Int] map createElement

  override def afterAll = {
    Await.ready(system.terminate(), awaitMax)
  }

  it should s"buffer a stream of $elementCount elements using GraphDSL" in {
    val util = new StreamSpecUtil[T, Event[T]](2)
    import util._

    val buffer = BroadcastBufferAtLeastOnce[T](config)
    val streamGraph = RunnableGraph.fromGraph(GraphDSL.create(flowCounter) { implicit builder =>
      sink =>
        import GraphDSL.Implicits._
        val commit = buffer.commit[T] // makes a dummy flow if autocommit is set to false
      val bcBuffer = builder.add(buffer.async)
        val mr = builder.add(merge)
        in ~> transform ~> bcBuffer ~> commit ~> mr ~> sink
        bcBuffer ~> commit ~> mr
        ClosedShape
    })
    val countFuture = streamGraph.run()
    val count = Await.result(countFuture, awaitMax)
    eventually { buffer.queue shouldBe 'closed }
    count shouldBe (elementCount * outputPorts)
    println(s"Total records processed $count")
    clean()
  }

  it should "buffer for a throttled stream" in {
    val util = new StreamSpecUtil[T, Event[T]](2)
    import util._
    val throttleShape = Flow[Event[T]].throttle(flowRate * 10, flowUnit, burstSize * 10, ThrottleMode.shaping)

    var t1, t2 = Long.MinValue
    val t0 = System.nanoTime

    def counter(recordFn: Long => Unit) = Flow[Any].map(_ => 1L).reduce(_ + _).map { s =>
      recordFn(System.nanoTime - t0)
      s
    }.toMat(Sink.head)(Keep.right)

    val buffer = BroadcastBufferAtLeastOnce[T](config)
    val streamGraph = RunnableGraph.fromGraph(GraphDSL.create(counter(t1 = _)) { implicit builder =>
      sink =>
        import GraphDSL.Implicits._
        val commit = buffer.commit[T] // makes a dummy flow if autocommit is set to false
      val bc = builder.add(Broadcast[T](2))
        val bcBuffer = builder.add(buffer.async)
        val mr = builder.add(merge)
        val throttle = builder.add(throttleShape)
        in ~> transform ~> bc ~> bcBuffer ~> commit ~> mr ~> throttle ~> sink
        bcBuffer ~> commit ~> mr
        bc ~> counter(t2 = _)
        ClosedShape
    })
    val countF = streamGraph.run()
    val count = Await.result(countF, awaitMax)
    eventually { buffer.queue shouldBe 'closed }
    println("Time difference (ms): " + (t1 - t2) / 1000000d)
    count shouldBe (elementCount * outputPorts)
    println(s"Total records processed $count")
    t1 should be > t2 // Give 6 seconds difference. In fact, it should be closer to 9 seconds.
    clean()
  }

  it should "recover from unexpected stream shutdown" in {
    implicit val util = new StreamSpecUtil[T, Event[T]](2)
    import util._

    val mat = ActorMaterializer()
    val finishedGenerating = Promise[Done]
    val bBufferInCount = new AtomicInteger(0)
    val counter = new AtomicInteger(0)

    def fireFinished() = Flow[T].map { e =>
      if(counter.incrementAndGet() == failTestAt) finishedGenerating success Done
      e
    }.toMat(Sink.ignore)(Keep.right)

    val shutdownF = finishedGenerating.future map { d => mat.shutdown(); d }

    val graph = RunnableGraph.fromGraph(GraphDSL.create(
      Sink.ignore, Sink.ignore, fireFinished())((_,_,_)) { implicit builder =>
      (sink1, sink2, sink3) =>
        import GraphDSL.Implicits._
        val buffer = BroadcastBufferAtLeastOnce[T](config).withOnPushCallback(() => bBufferInCount.incrementAndGet()).withOnCommitCallback(i => commitCounter(i))
        val commit = buffer.commit[T] // makes a dummy flow if autocommit is set to false
      val bcBuffer = builder.add(buffer.async)
        val bc = builder.add(Broadcast[T](2))

        in ~> transform ~> bc ~> bcBuffer ~> throttle ~> commit ~> sink1
        bcBuffer ~> throttle ~> commit ~> sink2
        bc ~> sink3

        ClosedShape
    })
    val (sink1F, sink2F, _) = graph.run()(mat)

    Await.result(sink1F.failed, awaitMax) shouldBe an[AbruptTerminationException]
    Await.result(sink2F.failed, awaitMax) shouldBe an[AbruptTerminationException]

    val restartFrom = bBufferInCount.incrementAndGet()
    println(s"Restart from count $restartFrom")

    val beforeShutDown = SinkCounts(atomicCounter(0).get, atomicCounter(1).get)
    resumeGraphAndDoAssertion(beforeShutDown, restartFrom)
    clean()
  }

  it should "recover from downstream failure" in {
    implicit val util = new StreamSpecUtil[T, Event[T]](2)
    import util._

    val mat = ActorMaterializer()
    val injectCounter = new AtomicInteger(0)
    val inCounter = new AtomicInteger(0)

    val injectError = Flow[Event[T]].map { n =>
      val count = injectCounter.incrementAndGet()
      if (count == failTestAt) throw new NumberFormatException("This is a fake exception")
      else n
    }

    val graph = RunnableGraph.fromGraph(
      GraphDSL.create(Sink.ignore, Sink.ignore)((_, _)) { implicit builder => (sink1, sink2) =>
        import GraphDSL.Implicits._
        val buffer = BroadcastBufferAtLeastOnce[T](config).withOnPushCallback(() => inCounter.incrementAndGet()).withOnCommitCallback(i => commitCounter(i))
        val commit = buffer.commit[T] // makes a dummy flow if autocommit is set to false
      val bcBuffer = builder.add(buffer.async)

        in ~> transform ~> bcBuffer ~> throttle ~> injectError ~> commit ~> sink1
        bcBuffer ~> throttle                ~> commit ~> sink2

        ClosedShape
      })

    val (sink1F, sink2F) = graph.run()(mat)

    Await.result(sink1F.failed, awaitMax) shouldBe an[NumberFormatException]
    Await.result(sink2F, awaitMax) shouldBe Done

    val beforeShutDown = SinkCounts(atomicCounter(0).get, atomicCounter(1).get)
    val restartFrom = inCounter.incrementAndGet()
    println(s"Restart from count $restartFrom")
    resumeGraphAndDoAssertion(beforeShutDown, restartFrom)
    clean()
  }

  it should "recover from upstream failure" in {
    implicit val util = new StreamSpecUtil[T, Event[T]](2)
    import util._

    val mat = ActorMaterializer()
    val injectError = Flow[Int].map { n =>
      if (n == failTestAt) throw new NumberFormatException("This is a fake exception")
      else n
    }

    val buffer = BroadcastBufferAtLeastOnce[T](config).withOnCommitCallback(i => commitCounter(i))
    val graph1 = RunnableGraph.fromGraph(
      GraphDSL.create(Sink.ignore, Sink.ignore)((_,_)) { implicit builder =>
        (sink1, sink2) =>
          import GraphDSL.Implicits._
          val commit = buffer.commit[T] // makes a dummy flow if autocommit is set to false
        val bcBuffer = builder.add(buffer.async)

          in ~> injectError ~> transform ~> bcBuffer ~> throttle ~> commit ~> sink1
          bcBuffer ~> throttle ~> commit ~> sink2

          ClosedShape
      })
    val (sink1F, sink2F) = graph1.run()(mat)
    Await.result(for {a <- sink1F; b <- sink2F} yield (a, b), awaitMax)
    eventually { buffer.queue shouldBe 'closed }

    val beforeShutDown  = SinkCounts(atomicCounter(0).get, atomicCounter(1).get)
    resumeGraphAndDoAssertion(beforeShutDown, failTestAt)
    clean()
  }

  case class SinkCounts(sink1: Long, sink2: Long)

  private def resumeGraphAndDoAssertion(beforeShutDown: SinkCounts, restartFrom: Int)(implicit util: StreamSpecUtil[T, Event[T]]) = {
    import util._
    val buffer = BroadcastBufferAtLeastOnce[T](config)
    val graph = RunnableGraph.fromGraph(
      GraphDSL.create(head, head,
        flowCounter, flowCounter)((_,_,_,_)) { implicit builder =>
        (first1, first2, last1, last2) =>
          import GraphDSL.Implicits._
          val bcBuffer = builder.add(buffer.async)
          val commit = buffer.commit[T] // makes a dummy flow if autocommit is set to false
        val bc1 = builder.add(Broadcast[Event[T]](2))
          val bc2 = builder.add(Broadcast[Event[T]](2))
          Source(restartFrom to (elementCount + elementsAfterFail)) ~> transform ~> bcBuffer ~> commit ~> bc1 ~> first1
                                                                                                          bc1 ~> last1
                                                                                    bcBuffer ~> commit ~> bc2 ~> first2
                                                                                                          bc2 ~> last2
          ClosedShape
      })
    val (head1F, head2F, last1F, last2F) = graph.run()(ActorMaterializer())
    val head1 = Await.result(head1F, awaitMax)
    val head2 = Await.result(head2F, awaitMax)
    println(s"First record processed after shutdown => ${(format(head1.entry), format(head2.entry))}")
    val last1 = Await.result(last1F, awaitMax)
    val last2 = Await.result(last2F, awaitMax)
    eventually { buffer.queue shouldBe 'closed }
    assertions(beforeShutDown, SinkCounts(last1, last2), SinkCounts(totalProcessed, totalProcessed))
  }

  private def assertions(beforeShutDown: SinkCounts, afterRecovery: SinkCounts, totalRecords: SinkCounts) = {
    println(s"Last record processed before shutdown => $beforeShutDown")
    println(s"Records processed after recovery => $afterRecovery")
    val processedRecords = (beforeShutDown.sink1 + afterRecovery.sink1, beforeShutDown.sink2 + afterRecovery.sink2)
    val lostRecords = (totalRecords.sink1 - processedRecords._1 , totalRecords.sink2 - processedRecords._2)
    println(s"Total records lost due to unexpected shutdown => $lostRecords")
    println(s"Total records processed => $processedRecords")

    processedRecords._1 should be >= totalRecords.sink1
    processedRecords._2 should be >= totalRecords.sink2
  }
}

class BroadcastByteStringBufferNoAutoCommitSpec extends BroadcastBufferAtLeastOnceSpec[ByteString, ByteStringSerializer]("ByteString") {

  def createElement(n: Int): ByteString = ByteString(s"Hello $n")

  def format(element: ByteString): String = element.utf8String
}

class BroadcastStringBufferNoAutoCommitSpec extends BroadcastBufferAtLeastOnceSpec[String, ObjectSerializer[String]]("Object") {

  def createElement(n: Int): String = s"Hello $n"

  def format(element: String): String = element
}

class BroadcastLongBufferNoAutoCommitSpec extends BroadcastBufferAtLeastOnceSpec[Long, LongSerializer]("Long") {

  def createElement(n: Int): Long = n

  def format(element: Long): String = element.toString
}

class BroadcastIntBufferNoAutoCommitSpec extends BroadcastBufferAtLeastOnceSpec[Int, IntSerializer]("Int") {

  def createElement(n: Int): Int = n

  def format(element: Int): String = element.toString
}

class BroadcastShortBufferNoAutoCommitSpec extends BroadcastBufferAtLeastOnceSpec[Short, ShortSerializer]("Short") {

  def createElement(n: Int): Short = n.toShort

  def format(element: Short): String = element.toString
}

class BroadcastByteBufferNoAutoCommitSpec extends BroadcastBufferAtLeastOnceSpec[Byte, ByteSerializer]("Byte") {

  def createElement(n: Int): Byte = n.toByte

  def format(element: Byte): String = element.toString
}

class BroadcastCharBufferNoAutoCommitSpec extends BroadcastBufferAtLeastOnceSpec[Char, CharSerializer]("Char") {

  def createElement(n: Int): Char = n.toChar

  def format(element: Char): String = element.toString
}

class BroadcastDoubleBufferNoAutoCommitSpec extends BroadcastBufferAtLeastOnceSpec[Double, DoubleSerializer]("Double") {

  def createElement(n: Int): Double = n.toDouble

  def format(element: Double): String = element.toString
}

class BroadcastFloatBufferNoAutoCommitSpec extends BroadcastBufferAtLeastOnceSpec[Float, FloatSerializer]("Float") {

  def createElement(n: Int): Float = n.toFloat

  def format(element: Float): String = element.toString
}

class BroadcastBooleanBufferNoAutoCommitSpec extends BroadcastBufferAtLeastOnceSpec[Boolean, BooleanSerializer]("Boolean") {

  def createElement(n: Int): Boolean = n % 2 == 0

  def format(element: Boolean): String = element.toString
}

class BroadcastPersonBufferNoAutoCommitSpec extends BroadcastBufferAtLeastOnceSpec[Person, PersonSerializer]("Person") {

  override implicit val serializer = new PersonSerializer()

  def createElement(n: Int): Person = Person(s"John Doe $n", 20)

  def format(element: Person): String = element.toString
}
