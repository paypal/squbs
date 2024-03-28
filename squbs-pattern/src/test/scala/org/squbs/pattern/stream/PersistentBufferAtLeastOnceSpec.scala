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
package org.squbs.pattern.stream

import org.apache.pekko.Done
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, RunnableGraph, Sink, Source}
import org.apache.pekko.stream.{AbruptTerminationException, ClosedShape, Materializer}
import org.apache.pekko.util.ByteString
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}
import org.squbs.testkit.Timeouts._

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{Await, Promise}
import scala.reflect._

abstract class PersistentBufferAtLeastOnceSpec[T: ClassTag, Q <: QueueSerializer[T]: Manifest]
(typeName: String) extends AnyFlatSpec with Matchers with BeforeAndAfterAll with Eventually {

  implicit val system = ActorSystem(s"Persistent${typeName}BufferAtLeastOnceSpec", PersistentBufferSpec.testConfig)
  implicit val serializer = QueueSerializer[T]()
  implicit override val patienceConfig = PatienceConfig(timeout = Span(3, Seconds)) // extend eventually timeout for CI
  import StreamSpecUtil._
  import system.dispatcher

  val transform = Flow[Int] map createElement

  override def afterAll(): Unit = {
    Await.ready(system.terminate(), awaitMax)
  }

  def createElement(n: Int): T

  def format(element: T): String

  it should s"buffer a stream of $elementCount elements" in {
    val util = new StreamSpecUtil[T, Event[T]]
    import util._
    val buffer = PersistentBufferAtLeastOnce[T](config)
    buffer.queue.serializer shouldBe a [Q]
    val commit = buffer.commit[T] // makes a dummy flow if autocommit is set to false
    val countFuture = in.via(transform).via(buffer.async).via(commit).runWith(flowCounter)
    val count = Await.result(countFuture, awaitMax)
    count shouldBe elementCount
    eventually { buffer.queue shouldBe 'closed }
    clean()
  }

  it should s"buffer a stream of $elementCount elements using GraphDSL and custom config" in {
    val util = new StreamSpecUtil[T, Event[T]]
    import util._
    val buffer = PersistentBufferAtLeastOnce[T](config)
    val commit = buffer.commit[T] // makes a dummy flow if autocommit is set to false

    val streamGraph = RunnableGraph.fromGraph(GraphDSL.createGraph(flowCounter) { implicit builder =>
      sink =>
        import GraphDSL.Implicits._
        in ~> transform ~> buffer.async ~> commit ~> sink
        ClosedShape
    })
    val countFuture = streamGraph.run()
    val count = Await.result(countFuture, awaitMax)
    count shouldBe elementCount
    eventually { buffer.queue shouldBe 'closed }
    clean()
  }

  it should "buffer for a throttled stream" in {
    var t1, t2 = Long.MinValue
    val util = new StreamSpecUtil[T, Event[T]]
    import util._
    val buffer = PersistentBufferAtLeastOnce[T](config)
    val t0 = System.nanoTime
    def counter(recordFn: Long => Unit) = Flow[Any].map( _ => 1L).reduce(_ + _).map { s =>
      recordFn(System.nanoTime - t0)
      s
    }.toMat(Sink.head)(Keep.right)

    val streamGraph = RunnableGraph.fromGraph(
      GraphDSL.createGraph(counter(t1 = _), flowCounter)((_,_)) { implicit builder =>
      (sink, total) =>
        import GraphDSL.Implicits._
        val bc1 = builder.add(Broadcast[T](2))
        val bc2 = builder.add(Broadcast[Event[T]](2))
        val commit = buffer.commit[T] // makes a dummy flow if autocommit is set to false
        in ~> transform ~> bc1 ~> buffer.async ~> throttle ~> commit ~> bc2 ~> sink
        bc2 ~> total
        bc1 ~> counter(t2 = _)
        ClosedShape
    })
    val (countF, totalF) = streamGraph.run()
    val count = Await.result(countF, awaitMax)
    val totalProcessed = Await.result(totalF, awaitMax)
    eventually { buffer.queue shouldBe 'closed }

    println("Time difference (ms): " + (t1 - t2) / 1000000d)
    println(s"Total count $count vs total processed $totalProcessed")
    count shouldBe elementCount
    totalProcessed shouldBe elementCount
    t1 should be > t2 // Give 6 seconds difference. In fact, it should be closer to 9 seconds.
    clean()
  }

  it should "recover from unexpected stream shutdown" in {
    implicit val util = new StreamSpecUtil[T, Event[T]]
    import util._

    val mat = Materializer(system)
    var t = Long.MinValue
    val pBufferInCount = new AtomicInteger(0)
    val commitCount = new AtomicInteger(0)
    val finishedGenerating = Promise[Done]()
    val counter = new AtomicInteger(0)

    def fireFinished() = Flow[T].map { e =>
      if(counter.incrementAndGet() == failTestAt) finishedGenerating success Done
      e
    }.toMat(Sink.ignore)(Keep.right)

    val shutdownF = finishedGenerating.future map { d => mat.shutdown(); d }

    val graph = RunnableGraph.fromGraph(GraphDSL.createGraph(Sink.ignore) { implicit builder =>
      sink =>
        import GraphDSL.Implicits._
        val buffer = PersistentBufferAtLeastOnce[T](config).withOnPushCallback(() => pBufferInCount.incrementAndGet()).withOnCommitCallback(() =>  commitCount.incrementAndGet())
        val commit = buffer.commit[T] // makes a dummy flow if autocommit is set to false
      val bc = builder.add(Broadcast[T](2))

        in ~> transform ~> bc ~> buffer.async ~> throttle ~> commit ~> sink
        bc ~> fireFinished()

        ClosedShape
    })
    val sinkF = graph.run()(mat)
    Await.result(shutdownF, awaitMax)
    Await.result(sinkF.failed, awaitMax) shouldBe an[AbruptTerminationException]

    val restartFrom = pBufferInCount.incrementAndGet()
    println(s"Restart from count $restartFrom")

    resumeGraphAndDoAssertion(commitCount.get, restartFrom)
    clean()
  }

  it should "recover from downstream failure" in {
    implicit val util = new StreamSpecUtil[T, Event[T]]
    import util._

    val outCount = new AtomicInteger(0)
    val injectCounter = new AtomicInteger(0)
    val inCounter = new AtomicInteger(0)

    val injectError = Flow[Event[T]].map { n =>
      val count = injectCounter.incrementAndGet()
      if (count == failTestAt) throw new NumberFormatException("This is a fake exception")
      else n
    }

    val graph = RunnableGraph.fromGraph(GraphDSL.createGraph(Sink.ignore) { implicit builder =>
      sink =>
        import GraphDSL.Implicits._
        val buffer = PersistentBufferAtLeastOnce[T](config).withOnPushCallback(() => inCounter.incrementAndGet()).withOnCommitCallback(() => outCount.incrementAndGet())
        val commit = buffer.commit[T] // makes a dummy flow if autocommit is set to false
        in ~> transform ~> buffer.async ~> throttle ~> injectError ~> commit ~> sink
        ClosedShape
    })
    val sinkF = graph.run()
    Await.result(sinkF.failed, awaitMax) shouldBe an[NumberFormatException]
    val restartFrom = inCounter.incrementAndGet()
    println(s"Restart from count $restartFrom")
    resumeGraphAndDoAssertion(outCount.get, restartFrom)
    clean()
  }

  it should "recover from upstream failure" in {
    implicit val util = new StreamSpecUtil[T, Event[T]]
    import util._
    val recordCount = new AtomicInteger(0)

    val injectError = Flow[Int].map { n =>
      if (n == failTestAt) throw new NumberFormatException("This is a fake exception")
      else n
    }

    def updateCounter() = Sink.foreach[Any] { _ => recordCount.incrementAndGet() }

    val buffer = PersistentBufferAtLeastOnce[T](config)
    val graph = RunnableGraph.fromGraph(GraphDSL.createGraph(updateCounter()) { implicit builder =>
      sink =>
        import GraphDSL.Implicits._
        val commit = buffer.commit[T] // makes a dummy flow if autocommit is set to false
        in ~> injectError ~> transform ~> buffer.async ~> throttle ~> commit ~> sink
        ClosedShape
    })
    val countF = graph.run()
    Await.result(countF, awaitMax)
    eventually { buffer.queue shouldBe 'closed }
    resumeGraphAndDoAssertion(recordCount.get, failTestAt)
    clean()
  }

  it should "not lose elements" in {
    implicit val util = new StreamSpecUtil[T, Event[T]]
    import util._

    val firstGroup = List(createElement(0), createElement(1), createElement(2))
    val secondGroup = List(createElement(3), createElement(4), createElement(5))

    {
      // first, run some elements through the buffer, but ensure none are committed
      val (collector, collectorSink) = Sink.seq[Event[T]].preMaterialize()
      val buffer = PersistentBufferAtLeastOnce[T](config)

      Source(firstGroup)
        .via(buffer)
        .filter(_ => false)
        .via(buffer.commit)
        .runWith(collectorSink)

      Await.result(collector, awaitMax) shouldBe Seq.empty
    }

    {
      // next, run some more elements, and ensure that all six are properly read
      val (collector, collectorSink) = Sink.seq[Event[T]].preMaterialize()
      val buffer = PersistentBufferAtLeastOnce[T](config)

      Source(secondGroup)
        .via(buffer)
        .via(buffer.commit)
        .runWith(collectorSink)

      Await.result(collector, awaitMax).map(_.entry) shouldBe firstGroup ++ secondGroup
    }

    {
      // finally, read from the buffer again, and ensure none are read given all committed

      val (collector, collectorSink) = Sink.seq[Event[T]].preMaterialize()
      val buffer = PersistentBufferAtLeastOnce[T](config)

      Source
        .empty
        .via(buffer)
        .via(buffer.commit)
        .runWith(collectorSink)

      Await.result(collector, awaitMax).map(_.entry) shouldBe Seq.empty
    }

    clean()
  }

  private def resumeGraphAndDoAssertion(beforeShutDown: Long, restartFrom: Int)(implicit util: StreamSpecUtil[T, Event[T]]) = {
    import util._
    val buffer = PersistentBufferAtLeastOnce[T](config)
    val graph = RunnableGraph.fromGraph(
      GraphDSL.createGraph(flowCounter, head)((_,_)) { implicit builder =>
        (sink, first) =>
          import GraphDSL.Implicits._
          val commit = buffer.commit[T] // makes a dummy flow if autocommit is set to false
        val bc = builder.add(Broadcast[Event[T]](2))
          Source(restartFrom to (elementCount + elementsAfterFail)) ~> transform ~> buffer.async ~> commit ~> bc ~> sink
          bc ~> first
          ClosedShape
      })
    val (countF, firstF) = graph.run()
    val afterRecovery = Await.result(countF, awaitMax)
    val first = Await.result(firstF, awaitMax)
    eventually { buffer.queue shouldBe 'closed }
    println(s"First record processed after shutdown => ${format(first.entry)}")
    assertions(beforeShutDown, afterRecovery, totalProcessed)
  }

  private def assertions(beforeShutDown: Long, afterRecovery: Long, totalRecords: Long) = {
    println(s"Last record processed before shutdown => $beforeShutDown")
    println(s"Records processed after recovery => $afterRecovery")
    val processedRecords = beforeShutDown + afterRecovery
    val lostRecords = totalRecords - processedRecords
    println(s"Total records lost due to unexpected shutdown => $lostRecords")
    println(s"Total records processed => $processedRecords")
    processedRecords should be >= totalRecords
  }
}

class PersistentByteStringBufferNoAutoCommitSpec extends PersistentBufferAtLeastOnceSpec[ByteString, ByteStringSerializer]("ByteString") {

  def createElement(n: Int): ByteString = ByteString(s"Hello $n")

  def format(element: ByteString): String = element.utf8String
}

class PersistentStringBufferNoAutoCommitSpec extends PersistentBufferAtLeastOnceSpec[String, ObjectSerializer[String]]("Object") {

  def createElement(n: Int): String = s"Hello $n"

  def format(element: String): String = element
}

class PersistentLongBufferNoAutoCommitSpec extends PersistentBufferAtLeastOnceSpec[Long, LongSerializer]("Long") {

  def createElement(n: Int): Long = n

  def format(element: Long): String = element.toString
}

class PersistentIntBufferNoAutoCommitSpec extends PersistentBufferAtLeastOnceSpec[Int, IntSerializer]("Int") {

  def createElement(n: Int): Int = n

  def format(element: Int): String = element.toString
}

class PersistentShortBufferNoAutoCommitSpec extends PersistentBufferAtLeastOnceSpec[Short, ShortSerializer]("Short") {

  def createElement(n: Int): Short = n.toShort

  def format(element: Short): String = element.toString
}

class PersistentByteBufferNoAutoCommitSpec extends PersistentBufferAtLeastOnceSpec[Byte, ByteSerializer]("Byte") {

  def createElement(n: Int): Byte = n.toByte

  def format(element: Byte): String = element.toString
}

class PersistentCharBufferNoAutoCommitSpec extends PersistentBufferAtLeastOnceSpec[Char, CharSerializer]("Char") {

  def createElement(n: Int): Char = n.toChar

  def format(element: Char): String = element.toString
}

class PersistentDoubleBufferNoAutoCommitSpec extends PersistentBufferAtLeastOnceSpec[Double, DoubleSerializer]("Double") {

  def createElement(n: Int): Double = n.toDouble

  def format(element: Double): String = element.toString
}

class PersistentFloatBufferNoAutoCommitSpec extends PersistentBufferAtLeastOnceSpec[Float, FloatSerializer]("Float") {

  def createElement(n: Int): Float = n.toFloat

  def format(element: Float): String = element.toString
}

class PersistentBooleanBufferNoAutoCommitSpec extends PersistentBufferAtLeastOnceSpec[Boolean, BooleanSerializer]("Boolean") {

  def createElement(n: Int): Boolean = n % 2 == 0

  def format(element: Boolean): String = element.toString
}

class PersistentPersonBufferNoAutoCommitSpec extends PersistentBufferAtLeastOnceSpec[Person, PersonSerializer]("Person") {

  override implicit val serializer = new PersonSerializer()

  def createElement(n: Int): Person = Person(s"John Doe $n", 20)

  def format(element: Person): String = element.toString
}
