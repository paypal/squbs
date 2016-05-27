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

import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

import akka.Done
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ClosedShape, ThrottleMode}
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import net.openhft.chronicle.wire.{WireIn, WireOut}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import org.squbs.testkit.Timeouts._

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}
import scala.language.postfixOps
import scala.reflect._
import scala.util.Try

abstract class PersistentBufferSpec[T: ClassTag, Q <: QueueSerializer[T]: Manifest](typeName: String) extends FlatSpec
  with Matchers with BeforeAndAfterAll {

  implicit val system = ActorSystem(s"Persistent${typeName}BufferSpec")
  implicit val mat = ActorMaterializer()
  implicit val serializer = QueueSerializer[T]()
  import system.dispatcher

  val elementCount = 50000
  val failTestAt = elementCount * 3 / 10
  val elementsAfterFail = 100
  val flowRate = 1000
  val flowUnit = 10 millisecond
  val burstSize = 500

  override def afterAll = {
    Await.ready(system.terminate(), awaitMax)
  }

  def delete(file: File) {
    if (file.isDirectory)
      Option(file.listFiles).map(_.toList).getOrElse(Nil).foreach(delete(_))
    file.delete
  }

  def createElement(n: Int): T

  def format(element: T): String

  it should "buffer a stream of one million elements" in {
    val tempPath = Files.createTempDirectory("persistent_queue")
    val in = Source(1 to elementCount)
    val transform = Flow[Int] map createElement
    val buffer = new PersistentBuffer[T](tempPath.toFile)
    buffer.queue.serializer shouldBe a [Q]
    val counter = Flow[Any].map( _ => 1L).reduce(_ + _).toMat(Sink.head)(Keep.right)
    val countFuture = in.via(transform).via(buffer).runWith(counter)
    val count = Await.result(countFuture, awaitMax)
    count shouldBe elementCount
    delete(tempPath.toFile)
  }

  it should "buffer a stream of one million elements using GraphDSL and custom config" in {
    val tempPath = Files.createTempDirectory("persistent_queue").toFile
    val config = ConfigFactory.parseString(s"persist-dir = ${tempPath.getAbsolutePath}")
    val in = Source(1 to elementCount)
    val transform = Flow[Int] map createElement
    val buffer = new PersistentBuffer[T](config)
    val counter = Flow[Any].map( _ => 1L).reduce(_ + _).toMat(Sink.head)(Keep.right)

    val streamGraph = RunnableGraph.fromGraph(GraphDSL.create(counter) { implicit builder =>
      sink =>
        import GraphDSL.Implicits._
        in ~> transform ~> buffer ~> sink
        ClosedShape
    })
    val countFuture = streamGraph.run()
    val count = Await.result(countFuture, awaitMax)
    count shouldBe elementCount
    delete(tempPath)
  }

  it should "buffer for a throttled stream" in {
    var t1, t2 = Long.MinValue
    val tempPath = Files.createTempDirectory("persistent_queue")
    val in = Source(1 to elementCount)
    val transform = Flow[Int] map createElement
    val buffer = new PersistentBuffer[T](tempPath.toFile)
    val throttle = Flow[T].throttle(flowRate, flowUnit, burstSize, ThrottleMode.shaping)
    val t0 = System.nanoTime
    def counter(recordFn: Long => Unit) = Flow[Any].map( _ => 1L).reduce(_ + _).map { s =>
      recordFn(System.nanoTime - t0)
      s
    }.toMat(Sink.head)(Keep.right)

    val streamGraph = RunnableGraph.fromGraph(GraphDSL.create(counter(t1 = _)) { implicit builder =>
      sink =>
        import GraphDSL.Implicits._
        val bc = builder.add(Broadcast[T](2))
        in ~> transform ~> bc ~> buffer ~> throttle ~> sink
                           bc ~> counter(t2 = _)
        ClosedShape
    })
    val countF = streamGraph.run()
    val count = Await.result(countF, awaitMax)

    println("Time difference (ms): " + (t1 - t2) / 1000000d)
    count shouldBe elementCount
    t1 should be > t2 // Give 6 seconds difference. In fact, it should be closer to 9 seconds.
    delete(tempPath.toFile)
  }

  it should "recover from unexpected stream shutdown" in {
    val mat2 = ActorMaterializer()
    var t = Long.MinValue
    val recordCount = new AtomicInteger(0)
    val finishedGenerating = Promise[Done]
    val tempPath = Files.createTempDirectory("persistent_queue")
    val in = Source(1 to elementCount)
    val transform = Flow[Int] map createElement
    val throttle = Flow[T].throttle(flowRate, flowUnit, burstSize, ThrottleMode.shaping)
    val t0 = System.nanoTime

    val buffer = new PersistentBuffer[T](tempPath.toFile)

    def fireFinished() = Flow[Any].map( _ => 1L).reduce(_ + _).map { s =>
      t = System.nanoTime - t0
      finishedGenerating success Done
      s
    }.toMat(Sink.head)(Keep.right)

    def updateCounter() = Sink.foreach[Any] { _ => recordCount.incrementAndGet() }

    val shutdownF = finishedGenerating.future map { d => mat2.shutdown(); d }

    val graph1 = RunnableGraph.fromGraph(GraphDSL.create(updateCounter()) { implicit builder =>
      sink =>
        import GraphDSL.Implicits._
        val bc = builder.add(Broadcast[T](2))
        in ~> transform ~> bc ~> buffer ~> throttle ~> sink
                           bc ~> fireFinished()
        ClosedShape
    })
    graph1.run()(mat2)
    Await.ready(shutdownF, awaitMax)

    Thread.sleep(1000) // Wait a sec to make sure the shutdown completed. shutdownF only says shutdown started.
    println("Records processed before shutdown: " + recordCount.get)
    val beforeCrashRecordCount = recordCount.get
    beforeCrashRecordCount should be < elementCount

    val buffer2 = new PersistentBuffer[T](tempPath.toFile)
    val printFirst = Sink.head[T]
    val graph2 = RunnableGraph.fromGraph(
      GraphDSL.create(updateCounter(), printFirst)((a, b) => (a, b)) { implicit builder =>
        (sink, first) =>
          import GraphDSL.Implicits._
          val bc = builder.add(Broadcast[T](2))
          Source((elementCount + 1) to (elementCount + elementsAfterFail)) ~> transform ~> buffer2 ~> bc ~> sink
                                                                                                      bc ~> first
          ClosedShape
      })
    val (doneF, firstF) = graph2.run()(ActorMaterializer())
    Await.ready(doneF, awaitMax)
    val firstRec = Await.result(firstF, awaitMax)
    println("First record after recovery: " + format(firstRec))
    val recordsLost = elementCount + elementsAfterFail - recordCount.get
    println(s"Number of records lost due to unexpected shutdown: $recordsLost")
    recordsLost should be < 3
    delete(tempPath.toFile)
  }

  it should "recover from downstream failure" in {
    val mat2 = ActorMaterializer()
    val inCount = new AtomicInteger(0)
    val outCount = new AtomicInteger(0)
    val injectCounter = new AtomicInteger(0)
    val tempPath = Files.createTempDirectory("persistent_queue")
    val in = Source(1 to elementCount).map { i =>
      inCount.incrementAndGet()
      i
    }
    val transform = Flow[Int] map createElement
    val throttle = Flow[T].throttle(flowRate, flowUnit, burstSize, ThrottleMode.shaping)
    val injectError = Flow[T].map { n =>
      val count = injectCounter.incrementAndGet()
      if (count == failTestAt) throw new NumberFormatException("This is a fake exception")
      else n
    }

    val buffer = new PersistentBuffer[T](tempPath.toFile)

    def updateCounter() = Flow[Any].map{ _ =>
      outCount.incrementAndGet()
      1L
    }.reduce(_ + _).toMat(Sink.head)(Keep.right)

    val graph1 = RunnableGraph.fromGraph(GraphDSL.create(updateCounter()) { implicit builder =>
      sink =>
        import GraphDSL.Implicits._
        in ~> transform ~> buffer ~> throttle ~> injectError ~> sink
        ClosedShape
    })
    val countF = graph1.run()(mat2)
    Try { Await.ready(countF, awaitMax) }

    Thread.sleep(1000) // Wait a sec to make sure the shutdown completed. shutdownF only says shutdown started.
    println("Records processed before shutdown: " + outCount.get)
    val beforeCrashRecordCount = outCount.get
    beforeCrashRecordCount should be < elementCount

    val buffer2 = new PersistentBuffer[T](tempPath.toFile)
    val printFirst = Sink.head[T]
    val graph2 = RunnableGraph.fromGraph(
      GraphDSL.create(updateCounter(), printFirst)((a, b) => (a, b)) { implicit builder =>
        (sink, first) =>
          import GraphDSL.Implicits._
          val bc = builder.add(Broadcast[T](2))
          Source((elementCount + 1) to (elementCount + elementsAfterFail)) ~> transform ~> buffer2 ~> bc ~> sink
                                                                                                      bc ~> first
          ClosedShape
      })
    val (thisCountF, firstF) = graph2.run()(ActorMaterializer())
    val firstRec = Await.result(firstF, awaitMax)
    println("First record after recovery: " + format(firstRec))
    firstRec shouldBe createElement(failTestAt + 1)

    val thisCount = Await.result(thisCountF, awaitMax)
    println("Count processed after recovery: " + thisCount)
    val discrepancy = Math.abs(inCount.get + elementsAfterFail - failTestAt - thisCount)
    discrepancy should be < 3L

    val recordsLost = inCount.get + elementsAfterFail - outCount.get
    println(s"Number of records lost due to unexpected shutdown: $recordsLost")
    recordsLost should be < 3
    delete(tempPath.toFile)
  }

  it should "recover from upstream failure" in {
    val mat2 = ActorMaterializer()
    val recordCount = new AtomicInteger(0)
    val tempPath = Files.createTempDirectory("persistent_queue")
    val in = Source(1 to elementCount)
    val transform = Flow[Int] map createElement
    val throttle = Flow[T].throttle(flowRate, flowUnit, burstSize, ThrottleMode.shaping)
    val injectError = Flow[Int].map { n =>
      if (n == failTestAt) throw new NumberFormatException("This is a fake exception")
      else n
    }

    val buffer = new PersistentBuffer[T](tempPath.toFile)

    def updateCounter() = Sink.foreach[Any] { _ => recordCount.incrementAndGet() }

    val graph1 = RunnableGraph.fromGraph(GraphDSL.create(updateCounter()) { implicit builder =>
      sink =>
        import GraphDSL.Implicits._
        in ~> injectError ~> transform ~> buffer ~> throttle ~> sink
        ClosedShape
    })
    val countF = graph1.run()(mat2)
    Try { Await.ready(countF, awaitMax) }

    Thread.sleep(1000) // Wait a sec to make sure the shutdown completed. shutdownF only says shutdown started.
    println("Records processed before shutdown: " + recordCount.get)
    val beforeCrashRecordCount = recordCount.get
    beforeCrashRecordCount should be < elementCount

    val buffer2 = new PersistentBuffer[T](tempPath.toFile)
    val printFirst = Sink.head[T]
    val graph2 = RunnableGraph.fromGraph(
      GraphDSL.create(updateCounter(), printFirst)((a, b) => (a, b)) { implicit builder =>
        (sink, first) =>
          import GraphDSL.Implicits._
          val bc = builder.add(Broadcast[T](2))
          Source(failTestAt to elementCount) ~> transform ~> buffer2 ~> bc ~> sink
                                                                        bc ~> first
          ClosedShape
      })
    val (doneF, firstF) = graph2.run()(ActorMaterializer())
    Await.ready(doneF, awaitMax)
    val firstRec = Await.result(firstF, awaitMax)
    println("First record after recovery: " + format(firstRec))
    val recordsLost = elementCount - recordCount.get
    println(s"Number of records lost due to unexpected shutdown: $recordsLost")
    recordsLost should be < 3
    delete(tempPath.toFile)
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


case class Person(name: String, age: Int)

class PersonSerializer extends QueueSerializer[Person] {

  override def readElement(wire: WireIn): Option[Person] = {
    for {
      name <- Option(wire.read().`object`(classOf[String]))
      age <- Option(wire.read().int32)
    } yield { Person(name, age) }
  }

  override def writeElement(element: Person, wire: WireOut): Unit = {
    wire.write().`object`(classOf[String], element.name)
    wire.write().int32(element.age)
  }
}

class PersistentPersonBufferSpec extends PersistentBufferSpec[Person, PersonSerializer]("Person") {

  override implicit val serializer = new PersonSerializer()

  def createElement(n: Int): Person = Person(s"John Doe $n", 20)

  def format(element: Person): String = element.toString
}
