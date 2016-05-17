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
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import org.squbs.testkit.Timeouts._

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}
import scala.language.postfixOps
import scala.util.Try

class PersistentBufferSpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  implicit val system = ActorSystem("PersistentBufferSpec")
  implicit val mat = ActorMaterializer()
  import system.dispatcher

  override def afterAll = {
    Await.ready(system.terminate(), awaitMax)
  }

  def delete(file: File) {
    if (file.isDirectory)
      Option(file.listFiles).map(_.toList).getOrElse(Nil).foreach(delete(_))
    file.delete
  }


  it should "buffer a stream of one million elements" in {
    val tempPath = Files.createTempDirectory("persistent_queue")
    val in = Source(1 to 1000000)
    val transform = Flow[Int].map { n => ByteString(s"Hello $n") }
    val buffer = new PersistentBuffer(tempPath.toFile)
    val counter = Flow[Any].map( _ => 1L).reduce(_ + _).toMat(Sink.head)(Keep.right)
    val countFuture = in.via(transform).via(buffer).runWith(counter)
    val count = Await.result(countFuture, awaitMax)
    count shouldBe 1000000
    delete(tempPath.toFile)
  }

  it should "buffer a stream of one million elements using GraphDSL" in {
    val tempPath = Files.createTempDirectory("persistent_queue")
    val in = Source(1 to 1000000)
    val transform = Flow[Int].map { n => ByteString(s"Hello $n") }
    val buffer = new PersistentBuffer(tempPath.toFile)
    val counter = Flow[Any].map( _ => 1L).reduce(_ + _).toMat(Sink.head)(Keep.right)

    val streamGraph = RunnableGraph.fromGraph(GraphDSL.create(counter) { implicit builder =>
      sink =>
        import GraphDSL.Implicits._
        in ~> transform ~> buffer ~> sink
        ClosedShape
    })
    val countFuture = streamGraph.run()
    val count = Await.result(countFuture, awaitMax)
    count shouldBe 1000000
    delete(tempPath.toFile)
  }

  it should "buffer for a throttled stream" in {
    var t1, t2 = Long.MinValue
    val tempPath = Files.createTempDirectory("persistent_queue")
    val in = Source(1 to 1000000)
    val transform = Flow[Int].map { n => ByteString(s"Hello $n") }
    val buffer = new PersistentBuffer(tempPath.toFile)
    val throttle = Flow[ByteString].throttle(100000, 1 second, 50000, ThrottleMode.shaping)
    val t0 = System.nanoTime
    def counter(recordFn: Long => Unit) = Flow[Any].map( _ => 1L).reduce(_ + _).map { s =>
      recordFn(System.nanoTime - t0)
      s
    }.toMat(Sink.head)(Keep.right)

    val streamGraph = RunnableGraph.fromGraph(GraphDSL.create(counter(t1 = _)) { implicit builder =>
      sink =>
        import GraphDSL.Implicits._
        val bc = builder.add(Broadcast[ByteString](2))
        in ~> transform ~> bc ~> buffer ~> throttle ~> sink
                           bc ~> counter(t2 = _)
        ClosedShape
    })
    val countF = streamGraph.run()
    val count = Await.result(countF, awaitMax)

    println("Time difference (ms): " + (t1 - t2) / 1000000d)
    count shouldBe 1000000
    t1 should be > t2 // Give 6 seconds difference. In fact, it should be closer to 9 seconds.
    delete(tempPath.toFile)
  }

  it should "recover from unexpected stream shutdown" in {
    val mat2 = ActorMaterializer()
    var t = Long.MinValue
    val recordCount = new AtomicInteger(0)
    val finishedGenerating = Promise[Done]
    val tempPath = Files.createTempDirectory("persistent_queue")
    val in = Source(1 to 1000000)
    val transform = Flow[Int].map { n => ByteString(s"Hello $n") }
    val throttle = Flow[ByteString].throttle(100000, 1 second, 50000, ThrottleMode.shaping)
    val t0 = System.nanoTime

    val buffer = new PersistentBuffer(tempPath.toFile)

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
        val bc = builder.add(Broadcast[ByteString](2))
        in ~> transform ~> bc ~> buffer ~> throttle ~> sink
                           bc ~> fireFinished()
        ClosedShape
    })
    graph1.run()(mat2)
    Await.ready(shutdownF, awaitMax)

    Thread.sleep(1000) // Wait a sec to make sure the shutdown completed. shutdownF only says shutdown started.
    println("Records processed before shutdown: " + recordCount.get)
    val beforeCrashRecordCount = recordCount.get
    beforeCrashRecordCount should be < 1000000

    val buffer2 = new PersistentBuffer(tempPath.toFile)
    val printFirst = Sink.head[ByteString]
    val graph2 = RunnableGraph.fromGraph(
      GraphDSL.create(updateCounter(), printFirst)((a, b) => (a, b)) { implicit builder =>
        (sink, first) =>
          import GraphDSL.Implicits._
          val bc = builder.add(Broadcast[ByteString](2))
          Source(1000001 to 1000100) ~> transform ~> buffer2 ~> bc ~> sink
                                                                bc ~> first
          ClosedShape
      })
    val (doneF, firstF) = graph2.run()(ActorMaterializer())
    Await.ready(doneF, awaitMax)
    val firstRec = Await.result(firstF, awaitMax)
    println("First record after recovery: " + firstRec.utf8String)
    val recordsLost = 1000100 - recordCount.get
    println(s"Number of records lost due to unexpected shutdown: $recordsLost")
    recordsLost should be < 3
    delete(tempPath.toFile)
  }

  it should "recover from downstream failure" in {
    val mat2 = ActorMaterializer()
    val inCount = new AtomicInteger(0)
    val outCount = new AtomicInteger(0)
    val tempPath = Files.createTempDirectory("persistent_queue")
    val in = Source(1 to 1000000).map { i =>
      inCount.incrementAndGet()
      i
    }
    val transform = Flow[Int].map { n => ByteString(s"Hello $n") }
    val throttle = Flow[ByteString].throttle(100000, 1 second, 50000, ThrottleMode.shaping)
    val injectError = Flow[ByteString].map { n =>
      if (n == ByteString("Hello 300000")) throw new NumberFormatException("This is a fake exception")
      else n
    }
    val t0 = System.nanoTime

    val buffer = new PersistentBuffer(tempPath.toFile)

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
    beforeCrashRecordCount should be < 1000000

    val buffer2 = new PersistentBuffer(tempPath.toFile)
    val printFirst = Sink.head[ByteString]
    val graph2 = RunnableGraph.fromGraph(
      GraphDSL.create(updateCounter(), printFirst)((a, b) => (a, b)) { implicit builder =>
        (sink, first) =>
          import GraphDSL.Implicits._
          val bc = builder.add(Broadcast[ByteString](2))
          Source(1000001 to 1000100) ~> transform ~> buffer2 ~> bc ~> sink
                                                                bc ~> first
          ClosedShape
      })
    val (thisCountF, firstF) = graph2.run()(ActorMaterializer())
    val firstRec = Await.result(firstF, awaitMax)
    println("First record after recovery: " + firstRec.utf8String)
    firstRec shouldBe ByteString("Hello 300001")

    val thisCount = Await.result(thisCountF, awaitMax)
    println("Count processed after recovery: " + thisCount)
    val discrepancy = Math.abs(inCount.get + 100 - 300000 - thisCount)
    discrepancy should be < 3L

    val recordsLost = inCount.get + 100 - outCount.get
    println(s"Number of records lost due to unexpected shutdown: $recordsLost")
    recordsLost should be < 3
    delete(tempPath.toFile)
  }

  it should "recover from upstream failure" in {
    val mat2 = ActorMaterializer()
    val recordCount = new AtomicInteger(0)
    val tempPath = Files.createTempDirectory("persistent_queue")
    val in = Source(1 to 1000000)
    val transform = Flow[Int].map { n => ByteString(s"Hello $n") }
    val throttle = Flow[ByteString].throttle(100000, 1 second, 50000, ThrottleMode.shaping)
    val injectError = Flow[Int].map { n =>
      if (n == 300000) throw new NumberFormatException("This is a fake exception")
      else n
    }
    val t0 = System.nanoTime

    val buffer = new PersistentBuffer(tempPath.toFile)

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
    beforeCrashRecordCount should be < 1000000

    val buffer2 = new PersistentBuffer(tempPath.toFile)
    val printFirst = Sink.head[ByteString]
    val graph2 = RunnableGraph.fromGraph(
      GraphDSL.create(updateCounter(), printFirst)((a, b) => (a, b)) { implicit builder =>
        (sink, first) =>
          import GraphDSL.Implicits._
          val bc = builder.add(Broadcast[ByteString](2))
          Source(300000 to 1000000) ~> transform ~> buffer2 ~> bc ~> sink
          bc ~> first
          ClosedShape
      })
    val (doneF, firstF) = graph2.run()(ActorMaterializer())
    Await.ready(doneF, awaitMax)
    val firstRec = Await.result(firstF, awaitMax)
    println("First record after recovery: " + firstRec.utf8String)
    val recordsLost = 1000000 - recordCount.get
    println(s"Number of records lost due to unexpected shutdown: $recordsLost")
    recordsLost should be < 3
    delete(tempPath.toFile)
  }
}
