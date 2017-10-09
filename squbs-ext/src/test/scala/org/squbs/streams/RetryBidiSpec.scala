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

package org.squbs.streams

import java.util.concurrent.atomic.AtomicLong

import akka.NotUsed
import akka.actor.{Actor, ActorSystem}
import akka.stream.Attributes.inputBuffer
import akka.stream.{ActorMaterializer, BufferOverflowException, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.TestKit
import org.scalatest.{AsyncFlatSpecLike, Matchers}

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

class RetryBidiSpec extends TestKit(ActorSystem("RetryBidiSpec")) with AsyncFlatSpecLike with Matchers {

  implicit val materializer = ActorMaterializer()
  val failure = Failure(new Exception("failed"))
  val failingBottom = Flow[(String, Long)].map {
    case (_, ctx) => (failure, ctx)
  }

  it should "require failure retryCount > 0" in {
    an[IllegalArgumentException] should be thrownBy
      RetryBidi[String, String, NotUsed](-1)
  }

  it should "return all expected elements if no failures" in {
    val flow = Flow[(String, Long)].map {
      case (elem, ctx) => (Success(elem), ctx)
    }
    val retryBidi = RetryBidi[String, String, Long](1)

    var context = 0L
    val result = Source("a" :: "b" :: "c" :: Nil)
      .map { s => context += 1; (s, context) }
      .via(retryBidi.join(flow))
      .runWith(Sink.seq)

    val expected = (Success("a"), 1) :: (Success("b"), 2) :: (Success("c"), 3) :: Nil
    result map {
      _ should contain theSameElementsAs expected
    }
  }

  it should "emit when first fail retries are exhausted" in {
    val flow = Flow[(String, Long)].map {
      case ("a", ctx) => (failure, ctx)
      case (elem, ctx) => (Success(elem), ctx)
    }
    var context = 0L
    val result = Source("a" :: "b" :: "c" :: Nil)
      .map { e => context += 1; (e, context) }
      .via(RetryBidi[String, String, Long](3).join(flow))
      .runWith(Sink.seq)

    val expected = (Success("b"), 2) :: (Success("c"), 3) :: (failure, 1) :: Nil
    result map {
      _ should contain theSameElementsAs expected
    }
  }

  it should "return failure if middle element failure exhausts retries" in {
    val flow = Flow[(String, Long)].map {
      case ("e", ctx) => (failure, ctx)
      case (elem, ctx) => (Success(elem), ctx)
    }

    val retryBidi = RetryBidi[String, String, Long](3)

    var context = 0L
    val result = Source("d" :: "e" :: "f" :: Nil)
      .map { s => context += 1; (s, context) }
      .via(retryBidi.join(flow))
      .runWith(Sink.seq)

    val expected = (Success("d"), 1) :: (failure, 2) :: (Success("f"), 3) :: Nil
    result map {
      _ should contain theSameElementsAs expected
    }
  }

  it should "return failure if last failure exhausts retries" in {
    val flow = Flow[(String, Long)].map {
      case ("f", ctx) => (failure, ctx)
      case (elem, ctx) => (Success(elem), ctx)
    }

    val retryBidi = RetryBidi[String, String, Long](1)

    var context = 0L
    val result = Source("d" :: "e" :: "f" :: Nil)
      .map { s => context += 1; (s, context) }
      .via(retryBidi.join(flow))
      .runWith(Sink.seq)

    val expected = (Success("d"), 1) :: (Success("e"), 2) :: (failure, 3) :: Nil
    result map {
      _ should contain theSameElementsAs expected
    }
  }

  it should "return exhausted failures in expected FIFO order" in {
    val flow = Flow[(String, Long)].map {
      case ("f", ctx) => (failure, ctx)
      case ("e", ctx) => (failure, ctx)
      case (elem, ctx) => (Success(elem), ctx)
    }

    val retryBidi = RetryBidi[String, String, Long](1)

    var context = 0L
    val result = Source("d" :: "e" :: "f" :: Nil)
      .map { s => context += 1; (s, context) }
      .via(retryBidi.join(flow))
      .runWith(Sink.seq)

    val expected = (Success("d"), 1) :: (failure, 2) :: (failure, 3) :: Nil
    result map {
      _ should contain theSameElementsInOrderAs expected
    }
  }

  it should "perform the correct number of retries" in {
    val count = new AtomicLong(0)
    val flow = Flow[(String, Long)].map {
      case (_, ctx) => count.getAndIncrement(); (failure, ctx)
    }

    val maxRetry = 10L
    val retryBidi = RetryBidi[String, String, Long](maxRetry)

    val context = 42L
    val result = Source("x" :: Nil)
      .map { s => (s, context) }
      .via(retryBidi.join(flow))
      .runWith(Sink.seq)

    result map { r =>
      r should contain theSameElementsAs (failure, 42) :: Nil
      count.get shouldEqual maxRetry + 1
    }
  }

  it should "return Success when a failure is retried successfully" in {
    var first = true
    val flow = Flow[(String, Long)].map {
      case ("y", ctx) if first =>
        first = false
        (failure, ctx)
      case (elem, ctx) => (Success(elem), ctx)
    }

    val retryBidi = RetryBidi[String, String, Long](2)

    var context = 0L
    val result = Source("x" :: "y" :: "z" :: Nil)
      .map { s => context += 1; (s, context) }
      .via(retryBidi.join(flow))
      .runWith(Sink.seq)

    val expected = (Success("x"), 1) :: (Success("z"), 3) :: (Success("y"), 2) :: Nil
    result map {
      _ should contain theSameElementsAs expected
    }
  }

  it should "allow a uniqueid mapper via UniqueId.Provider" in {

    case class MyContext(id: Long) extends UniqueId.Provider {
      override def uniqueId: Any = id
    }

    val flow = Flow[(String, MyContext)].map {
      case (elem, ctx) => (Success(elem), ctx)
    }
    val retry = RetryBidi[String, String, MyContext](2, uniqueIdMapper = (context: MyContext) => Some(context.uniqueId))

    var counter = 0L
    val result = Source("a" :: "b" :: "c" :: Nil)
      .map { s => counter += 1; (s, MyContext(counter)) }
      .via(retry.join(flow))
      .map { case (s, _) => s }
      .runWith(Sink.seq)

    val expected = Success("a") :: Success("b") :: Success("c") :: Nil
    result map {
      _ should contain theSameElementsAs expected
    }
  }

  it should "cancel upstream if downstream cancels" in {
    val bottom = Flow[(String, Long)].map {
      case (_, ctx) => (failure, ctx)
    }
    val retry = RetryBidi[String, String, Long](5)
    var context = 0L
    val (source, sink) = TestSource.probe[String]
      .map { s => context += 1; (s, context) }
      .via(retry.join(bottom))
      .toMat(TestSink.probe)(Keep.both).run()

    sink.request(2)
    source.sendNext("a")
    sink.cancel()
    source.expectCancellation()
    succeed
  }

  it should "keep retrying after upstream completes" in {
    val bottom = Flow[(String, Long)].map {
      case (_, ctx) => (failure, ctx)
    }
    val retry = RetryBidi[String, String, Long](5)
    var context = 0L
    val (source, sink) = TestSource.probe[String]
      .map { s => context += 1; (s, context) }
      .via(retry.join(bottom))
      .toMat(TestSink.probe)(Keep.both).run()

    sink.request(1)
    source.sendNext("a").sendNext("b").sendComplete()
    val next = sink.requestNext(3 seconds)
    assert((failure, 1) == next)
  }

  it should "decide on failures based on the provided function" in {
    val bottom = Flow[(String, Long)].map {
      case (elem, ctx) => (Success(elem), ctx)
    }
    val failureDecider = (out: Try[String]) => out.isFailure || out.equals(Success("a")) // treat "a" as a failure for retry
    val retry = RetryBidi[String, String, Long](2, failureDecider = Option(failureDecider))

    var context = 0L
    val (source, sink) = TestSource.probe[String]
      .map { s => context += 1; (s, context) }
      .via(retry.join(bottom))
      .toMat(TestSink.probe)(Keep.both).run()

    source.sendNext("a").sendNext("b")
    val nextA = sink.request(1).requestNext()
    assert((Success("a"), 1) == nextA)
  }

  it should "drain all elements when upstream finishes" in {
    val bottom = Flow[(String, Long)].map {
      case ("1", ctx) => (Success("1"), ctx)
      case (_, ctx) => (failure, ctx)
    }
    val retry = RetryBidi[String, String, Long](10)

    val (source, sink) = TestSource.probe[String]
      .map(x => (x.toString, x.toLong))
      .via(retry.join(bottom))
      .toMat(TestSink.probe)(Keep.both).run()

    source.sendNext("1").sendNext("2").sendNext("3").sendComplete()
    sink.request(3).expectNext((Success("1"), 1L)).expectNextUnordered((failure, 2L), (failure, 3L))
    succeed
  }

  it should "drop head elements and emit them when dropHead buffer mode" in {
    val bottom = Flow[(String, Long)].delay(10.millis).map {
      case (_, ctx) => (failure, ctx)
    }
    val retry = RetryBidi[String, String, Long](10, overflowStrategy = OverflowStrategy.dropHead)
      .withAttributes(inputBuffer(initial = 1, max = 3))

    val sink = Source(1 to 5)
      .map(x => (x.toString, x.toLong))
      .via(retry.join(bottom))
      .runWith(TestSink.probe)

    // 1 and 2 element are emitted after being dropped from buffer, 3-5 after exhausting all retries
    sink
      .request(5)
      .expectNoMsg(10.millis)
      .expectNext((failure, 1L), (failure, 2L))
      .expectNoMsg(100.millis)
      .expectNext((failure, 3L), (failure, 4L), (failure, 5L))
    succeed
  }

  it should "drop tail elements and emit them when dropTail buffer mode" in {
    val bottom = Flow[(String, Long)].delay(10.millis).map {
      case (_, ctx) => (failure, ctx)
    }
    val retry = RetryBidi[String, String, Long](10, overflowStrategy = OverflowStrategy.dropTail)
      .withAttributes(inputBuffer(initial = 1, max = 3))

    val sink = Source(1 to 5)
      .map(x => (x.toString, x.toLong))
      .via(retry.join(bottom))
      .runWith(TestSink.probe)

    // element 3 and 4 are emitted after being dropped from buffer
    sink.request(5)
      .expectNoMsg(10.millis)
      .expectNext((failure, 3L), (failure, 4L))
      .expectNoMsg(100.millis)
      .expectNext((failure, 1L), (failure, 2L), (failure, 5L))
    succeed
  }

  it should "drop new elements when dropNew buffer mode" in {
    val bottom = Flow[(String, Long)].delay(10.millis).map {
      case (_, ctx) => (failure, ctx)
    }
    val retry = RetryBidi[String, String, Long](2, overflowStrategy = OverflowStrategy.dropNew)
      .withAttributes(inputBuffer(initial = 1, max = 3))

    val sink = Source(1 to 5)
      .map(x => (x.toString, x.toLong))
      .via(retry.join(bottom))
      .runWith(TestSink.probe)

    sink.request(5)
      .expectNext((failure, 1L), (failure, 2L), (failure, 3L))
      .expectComplete() // element 4 and 5 are dropped on buffer full
    succeed
  }

  it should "drop all elements in buffer when dropBuffer mode" in {
    val bottom = Flow[(String, Long)].delay(10.millis).map {
      case (_, ctx) => (failure, ctx)
    }
    val retry = RetryBidi[String, String, Long](10, overflowStrategy = OverflowStrategy.dropBuffer)
      .withAttributes(inputBuffer(initial = 1, max = 4))

    val sink = Source(1 to 8)
      .map(x => (x.toString, x.toLong))
      .via(retry.join(bottom))
      .runWith(TestSink.probe)

    sink.request(8) // first 16 elements are dropped and immitted when buffer is full the rest after exhausting retries
      .expectNoMsg(10.millis)
      .expectNext((failure, 1L), (failure, 2L), (failure, 3L), (failure, 4L))
      .expectNoMsg(100.millis)
      .expectNext((failure, 5L), (failure, 6L), (failure, 7L), (failure, 8L))
    succeed
  }

  it should "backpressures upstream when buffer full" in {
    val bottom = Flow[(String, Long)].delay(10.millis).map {
      case (_, ctx) => (failure, ctx)
    }
    val retry = RetryBidi[String, String, Long](1).withAttributes(inputBuffer(initial = 1, max = 1))

    val sink = Source(1 to 3)
      .map(x => (x.toString, x.toLong))
      .via(retry.join(bottom))
      .runWith(TestSink.probe)

    sink.request(3)
      .expectNoMsg(10.millis)
      .expectNext((failure, 1L))
      .expectNoMsg(10.millis)
    succeed
  }

  it should "fail when buffer full on fail mode" in {
    val bottom = Flow[(String, Long)].delay(10.millis).map {
      case (_, ctx) => (failure, ctx)
    }
    val retry = RetryBidi[String, String, Long](1, overflowStrategy = OverflowStrategy.fail)
      .withAttributes(inputBuffer(initial = 1, max = 1))

    val sink = Source(1 to 3)
      .map(x => (x.toString, x.toLong))
      .via(retry.join(bottom))
      .runWith(TestSink.probe)

    sink.request(3).expectError(new BufferOverflowException("Retry buffer overflow for retry stage (max capacity was: 1)!"))
    succeed
  }

  it should "retry with a large data set" in {
    val bottom = Flow[(Long, Long)].map {
      case (elem, ctx) => if (ctx % 7 == 0) (failure, ctx) else (Success(elem), ctx) // fail every 7'th element
    }
    val retry = RetryBidi[Long, Long, Long](1)

    val result = Source(1L to 10000)
      .map(x => (x, x))
      .via(retry.join(bottom))
      .runWith(Sink.seq)

    val expected = 1 to 10000 map (x => if (x % 7 == 0) (failure, x) else (Success(x), x))
    result map {
      _ should contain theSameElementsAs expected
    }
  }

  it should "retry with a 1s delay should delay each retried element by 1s" in {
    val retry = RetryBidi[String, Long, Long](2, delay = 1 second)

    val testSink = Source(1 to 3)
      .map(x => (x.toString, x.toLong))
      .via(retry.join(failingBottom))
      .runWith(TestSink.probe)

    testSink.request(3)
      .expectNoMsg(2 second)
      .expectNext((failure, 1L))
      .expectNoMsg(2 second)
      .expectNext((failure, 2L))
    //.expectComplete()
    succeed
  }

  it should "retry with delay and backoff should increase retry delay" in {
    val retry = RetryBidi[String, Long, Long](2, delay = 1 second, expBackoffFactor = 1)

    val sink = Source(1 to 3)
      .map(x => (x.toString, x.toLong))
      .via(retry.join(failingBottom))
      .runWith(TestSink.probe)

    sink.request(3)
      .expectNoMsg(3 seconds)
      .expectNext(5 seconds, (failure, 1L))
      .expectNoMsg(3 seconds)
      .expectNext(5 seconds, (failure, 2L))
      .expectNoMsg(3 seconds)
      .expectNext(5 seconds, (failure, 3L))
    succeed
  }

}
