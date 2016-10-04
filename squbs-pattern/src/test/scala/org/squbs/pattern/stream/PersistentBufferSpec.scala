/*
 *  Copyright 2015 PayPal
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

import java.util.concurrent.atomic.AtomicInteger

import akka.Done
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, RunnableGraph, Sink, Source}
import akka.stream.{AbruptTerminationException, ActorMaterializer, ClosedShape}
import akka.util.ByteString
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import org.squbs.testkit.Timeouts._

import scala.concurrent.{Await, Promise}
import scala.reflect._

abstract class PersistentBufferSpec[T: ClassTag, Q <: QueueSerializer[T]: Manifest]
(typeName: String, autoCommit: Boolean = true) extends FlatSpec with Matchers with BeforeAndAfterAll with Eventually {

  implicit val system = ActorSystem(s"Persistent${typeName}BufferSpec")
  implicit val mat = ActorMaterializer()
  implicit val serializer = QueueSerializer[T]()
  import StreamSpecUtil._
  import system.dispatcher

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(30, Seconds)), interval = scaled(Span(500, Millis)))

  val transform = Flow[Int] map createElement

  override def afterAll = {
    Await.ready(system.terminate(), awaitMax)
  }

  def createElement(n: Int): T

  def format(element: T): String

  it should s"buffer a stream of $elementCount elements" in {
    val util = new StreamSpecUtil[T](autoCommit = autoCommit)
    import util._
    val buffer = new PersistentBuffer[T](config)
    buffer.queue.serializer shouldBe a [Q]
    val commit = buffer.commit[T] // makes a dummy flow if autocommit is set to false
    val countFuture = in.via(transform).via(buffer.async).via(commit).runWith(flowCounter)
    val count = Await.result(countFuture, awaitMax)
    count shouldBe elementCount
    eventually { buffer.queue shouldBe 'closed }
    clean()
  }

  it should s"buffer a stream of $elementCount elements using GraphDSL and custom config" in {
    val util = new StreamSpecUtil[T](autoCommit = autoCommit)
    import util._
    val buffer = new PersistentBuffer[T](config)
    val commit = buffer.commit[T] // makes a dummy flow if autocommit is set to false

    val streamGraph = RunnableGraph.fromGraph(GraphDSL.create(flowCounter) { implicit builder =>
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
    val util = new StreamSpecUtil[T](autoCommit = autoCommit)
    import util._
    val buffer = new PersistentBuffer[T](config)
    val t0 = System.nanoTime
    def counter(recordFn: Long => Unit) = Flow[Any].map( _ => 1L).reduce(_ + _).map { s =>
      recordFn(System.nanoTime - t0)
      s
    }.toMat(Sink.head)(Keep.right)

    val streamGraph = RunnableGraph.fromGraph(GraphDSL.create(counter(t1 = _), flowCounter)((_,_)) { implicit builder =>
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
    implicit val util = new StreamSpecUtil[T](autoCommit = autoCommit)
    import util._

    val mat = ActorMaterializer()
    var t = Long.MinValue
    val pBufferInCount = new AtomicInteger(0)
    val commitCount = new AtomicInteger(0)
    val finishedGenerating = Promise[Done]
    val counter = new AtomicInteger(0)

    def fireFinished() = Flow[T].map { e =>
      if(counter.incrementAndGet() == failTestAt) finishedGenerating success Done
      e
    }.toMat(Sink.ignore)(Keep.right)

    val shutdownF = finishedGenerating.future map { d => mat.shutdown(); d }

    val graph = RunnableGraph.fromGraph(GraphDSL.create(Sink.ignore) { implicit builder =>
      sink =>
        import GraphDSL.Implicits._
        val buffer = new PersistentBuffer[T](config).withOnPushCallback(() => pBufferInCount.incrementAndGet()).withOnCommitCallback(() =>  commitCount.incrementAndGet())
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
    implicit val util = new StreamSpecUtil[T](autoCommit = autoCommit)
    import util._

    val mat = ActorMaterializer()
    val outCount = new AtomicInteger(0)
    val injectCounter = new AtomicInteger(0)
    val inCounter = new AtomicInteger(0)

    val injectError = Flow[Event[T]].map { n =>
      val count = injectCounter.incrementAndGet()
      if (count == failTestAt) throw new NumberFormatException("This is a fake exception")
      else n
    }

    val graph = RunnableGraph.fromGraph(GraphDSL.create(Sink.ignore) { implicit builder =>
      sink =>
        import GraphDSL.Implicits._
        val buffer = new PersistentBuffer[T](config).withOnPushCallback(() => inCounter.incrementAndGet()).withOnCommitCallback(() => outCount.incrementAndGet())
        val commit = buffer.commit[T] // makes a dummy flow if autocommit is set to false
        in ~> transform ~> buffer.async ~> throttle ~> injectError ~> commit ~> sink
        ClosedShape
    })
    val sinkF = graph.run()(mat)
    Await.result(sinkF.failed, awaitMax) shouldBe an[NumberFormatException]
    val restartFrom = inCounter.incrementAndGet()
    println(s"Restart from count $restartFrom")
    resumeGraphAndDoAssertion(outCount.get, restartFrom)
    clean()
  }

  it should "recover from upstream failure" in {
    implicit val util = new StreamSpecUtil[T](autoCommit = autoCommit)
    import util._
    val mat = ActorMaterializer()
    val recordCount = new AtomicInteger(0)

    val injectError = Flow[Int].map { n =>
      if (n == failTestAt) throw new NumberFormatException("This is a fake exception")
      else n
    }

    def updateCounter() = Sink.foreach[Any] { _ => recordCount.incrementAndGet() }

    val buffer = new PersistentBuffer[T](config)
    val graph = RunnableGraph.fromGraph(GraphDSL.create(updateCounter()) { implicit builder =>
      sink =>
        import GraphDSL.Implicits._
        val commit = buffer.commit[T] // makes a dummy flow if autocommit is set to false
        in ~> injectError ~> transform ~> buffer.async ~> throttle ~> commit ~> sink
        ClosedShape
    })
    val countF = graph.run()(mat)
    Await.result(countF, awaitMax)
    eventually { buffer.queue shouldBe 'closed }
    resumeGraphAndDoAssertion(recordCount.get, failTestAt)
    clean()
  }

  private def resumeGraphAndDoAssertion(beforeShutDown: Long, restartFrom: Int)(implicit util: StreamSpecUtil[T]) = {
    import util._
    val buffer = new PersistentBuffer[T](config)
    val graph = RunnableGraph.fromGraph(
      GraphDSL.create(flowCounter, head)((_,_)) { implicit builder =>
        (sink, first) =>
          import GraphDSL.Implicits._
          val commit = buffer.commit[T] // makes a dummy flow if autocommit is set to false
        val bc = builder.add(Broadcast[Event[T]](2))
          Source(restartFrom to (elementCount + elementsAfterFail)) ~> transform ~> buffer.async ~> commit ~> bc ~> sink
                                                                                                              bc ~> first
          ClosedShape
      })
    val (countF, firstF) = graph.run()(ActorMaterializer())
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

class PersistentByteStringBufferSpec extends PersistentBufferSpec[ByteString, ByteStringSerializer]("ByteString") {

  def createElement(n: Int): ByteString = ByteString(s"Hello $n")

  def format(element: ByteString): String = element.utf8String
}

class PersistentStringBufferSpec extends PersistentBufferSpec[String, ObjectSerializer[String]]("Object") {

  def createElement(n: Int): String = s"Hello $n"

  def format(element: String): String = element
}

class PersistentLongBufferSpec extends PersistentBufferSpec[Long, LongSerializer]("Long") {

  def createElement(n: Int): Long = n

  def format(element: Long): String = element.toString
}

class PersistentIntBufferSpec extends PersistentBufferSpec[Int, IntSerializer]("Int") {

  def createElement(n: Int): Int = n

  def format(element: Int): String = element.toString
}

class PersistentShortBufferSpec extends PersistentBufferSpec[Short, ShortSerializer]("Short") {

  def createElement(n: Int): Short = n.toShort

  def format(element: Short): String = element.toString
}

class PersistentByteBufferSpec extends PersistentBufferSpec[Byte, ByteSerializer]("Byte") {

  def createElement(n: Int): Byte = n.toByte

  def format(element: Byte): String = element.toString
}

class PersistentCharBufferSpec extends PersistentBufferSpec[Char, CharSerializer]("Char") {

  def createElement(n: Int): Char = n.toChar

  def format(element: Char): String = element.toString
}

class PersistentDoubleBufferSpec extends PersistentBufferSpec[Double, DoubleSerializer]("Double") {

  def createElement(n: Int): Double = n.toDouble

  def format(element: Double): String = element.toString
}

class PersistentFloatBufferSpec extends PersistentBufferSpec[Float, FloatSerializer]("Float") {

  def createElement(n: Int): Float = n.toFloat

  def format(element: Float): String = element.toString
}

class PersistentBooleanBufferSpec extends PersistentBufferSpec[Boolean, BooleanSerializer]("Boolean") {

  def createElement(n: Int): Boolean = n % 2 == 0

  def format(element: Boolean): String = element.toString
}

class PersistentPersonBufferSpec extends PersistentBufferSpec[Person, PersonSerializer]("Person") {

  override implicit val serializer = new PersonSerializer()

  def createElement(n: Int): Person = Person(s"John Doe $n", 20)

  def format(element: Person): String = element.toString
}

class PersistentByteStringBufferNoAutoCommitSpec extends PersistentBufferSpec[ByteString, ByteStringSerializer]("ByteString", false) {

  def createElement(n: Int): ByteString = ByteString(s"Hello $n")

  def format(element: ByteString): String = element.utf8String
}

class PersistentStringBufferNoAutoCommitSpec extends PersistentBufferSpec[String, ObjectSerializer[String]]("Object", false) {

  def createElement(n: Int): String = s"Hello $n"

  def format(element: String): String = element
}

class PersistentLongBufferNoAutoCommitSpec extends PersistentBufferSpec[Long, LongSerializer]("Long", false) {

  def createElement(n: Int): Long = n

  def format(element: Long): String = element.toString
}

class PersistentIntBufferNoAutoCommitSpec extends PersistentBufferSpec[Int, IntSerializer]("Int", false) {

  def createElement(n: Int): Int = n

  def format(element: Int): String = element.toString
}

class PersistentShortBufferNoAutoCommitSpec extends PersistentBufferSpec[Short, ShortSerializer]("Short", false) {

  def createElement(n: Int): Short = n.toShort

  def format(element: Short): String = element.toString
}

class PersistentByteBufferNoAutoCommitSpec extends PersistentBufferSpec[Byte, ByteSerializer]("Byte", false) {

  def createElement(n: Int): Byte = n.toByte

  def format(element: Byte): String = element.toString
}

class PersistentCharBufferNoAutoCommitSpec extends PersistentBufferSpec[Char, CharSerializer]("Char", false) {

  def createElement(n: Int): Char = n.toChar

  def format(element: Char): String = element.toString
}

class PersistentDoubleBufferNoAutoCommitSpec extends PersistentBufferSpec[Double, DoubleSerializer]("Double", false) {

  def createElement(n: Int): Double = n.toDouble

  def format(element: Double): String = element.toString
}

class PersistentFloatBufferNoAutoCommitSpec extends PersistentBufferSpec[Float, FloatSerializer]("Float", false) {

  def createElement(n: Int): Float = n.toFloat

  def format(element: Float): String = element.toString
}

class PersistentBooleanBufferNoAutoCommitSpec extends PersistentBufferSpec[Boolean, BooleanSerializer]("Boolean", false) {

  def createElement(n: Int): Boolean = n % 2 == 0

  def format(element: Boolean): String = element.toString
}

class PersistentPersonBufferNoAutoCommitSpec extends PersistentBufferSpec[Person, PersonSerializer]("Person", false) {

  override implicit val serializer = new PersonSerializer()

  def createElement(n: Int): Person = Person(s"John Doe $n", 20)

  def format(element: Person): String = element.toString
}
