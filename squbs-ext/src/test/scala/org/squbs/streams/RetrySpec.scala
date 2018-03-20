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
import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.Attributes.inputBuffer
import akka.stream.{ActorMaterializer, OverflowStrategy, ThrottleMode}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.TestKit
import org.scalatest.{AsyncFlatSpecLike, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

class RetrySpec extends TestKit(ActorSystem("RetryBidiSpec")) with AsyncFlatSpecLike with Matchers {

  implicit val materializer = ActorMaterializer()
  val failure = Failure(new Exception("failed"))

  it should "require failure retryCount > 0" in {
    an[IllegalArgumentException] should be thrownBy
      Retry[String, String, NotUsed](-1)
  }

  it should "require expbackoff >= 0" in {
    an[IllegalArgumentException] should be thrownBy
      Retry(RetrySettings[String, String, NotUsed](1, exponentialBackoffFactor = -0.5))
  }

  it should "retry settings failure decider should default to None" in {
    assert(RetrySettings(1).failureDecider.equals(None))
  }

  it should "return all expected elements if no failures" in {
    val flow = Flow[(String, Long)].map {
      case (elem, ctx) => (Success(elem), ctx)
    }
    val retryBidi = Retry[String, String, Long](1)

    var context = 0L
    val result = Source("a" :: "b" :: "c" :: Nil)
      .map { s => context += 1; (s, context) }
      .via(retryBidi.join(flow))
      .runWith(Sink.seq)

    val expected = (Success("a"), 1) :: (Success("b"), 2) :: (Success("c"), 3) :: Nil
    result map {
      _ should contain theSameElementsInOrderAs expected
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
      .via(Retry[String, String, Long](3).join(flow))
      .runWith(Sink.seq)

    val expected = (failure, 1) :: (Success("b"), 2) :: (Success("c"), 3) :: Nil
    result map {
      _ should contain theSameElementsInOrderAs expected
    }
  }

  it should "return failure if middle element failure exhausts retries" in {
    val flow = Flow[(String, Long)].map {
      case ("e", ctx) => (failure, ctx)
      case (elem, ctx) => (Success(elem), ctx)
    }

    val retryBidi = Retry[String, String, Long](3)

    var context = 0L
    val result = Source("d" :: "e" :: "f" :: Nil)
      .map { s => context += 1; (s, context) }
      .via(retryBidi.join(flow))
      .runWith(Sink.seq)

    val expected = (Success("d"), 1) :: (failure, 2) :: (Success("f"), 3) :: Nil
    result map {
      _ should contain theSameElementsInOrderAs expected
    }
  }

  it should "return failure if last failure exhausts retries" in {
    val flow = Flow[(String, Long)].map {
      case ("f", ctx) => (failure, ctx)
      case (elem, ctx) => (Success(elem), ctx)
    }

    val retryBidi = Retry[String, String, Long](1)

    var context = 0L
    val result = Source("d" :: "e" :: "f" :: Nil)
      .map { s => context += 1; (s, context) }
      .via(retryBidi.join(flow))
      .runWith(Sink.seq)

    val expected = (Success("d"), 1) :: (Success("e"), 2) :: (failure, 3) :: Nil
    result map {
      _ should contain theSameElementsInOrderAs expected
    }
  }

  it should "return exhausted failures in expected FIFO order" in {
    val flow = Flow[(String, Long)].map {
      case ("f", ctx) => (failure, ctx)
      case ("e", ctx) => (failure, ctx)
      case (elem, ctx) => (Success(elem), ctx)
    }

    val retryBidi = Retry[String, String, Long](1)

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

    val maxRetry = 10
    val retryBidi = Retry[String, String, Long](maxRetry)

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

    val retryBidi = Retry[String, String, Long](2)

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
    val retry = Retry(RetrySettings[String, String, MyContext](2).withUniqueIdMapper((context: MyContext) => context.uniqueId))

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

  it should "allow a uniqueid mapper via retrySettings" in {
    case class MyContext(s: String, id: Long)

    val retrySettings =
      RetrySettings[String, String, MyContext](2)
        .withUniqueIdMapper(context => context.id)

    val flow = Flow[(String, MyContext)].map {
      case (elem, ctx) => (Success(elem), ctx)
    }
    val retry = Retry[String, String, MyContext](retrySettings)

    var counter = 0L
    val result = Source("a" :: "b" :: "c" :: Nil)
      .map { s => counter += 1; (s, MyContext(s, counter)) }
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
    val retry = Retry(RetrySettings[String, String, Long](max = 5))
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
    val retry = Retry[String, String, Long](5)
    var context = 0L
    val (source, sink) = TestSource.probe[String]
      .map { s => context += 1; (s, context) }
      .via(retry.join(bottom))
      .toMat(TestSink.probe)(Keep.both).run()

    sink.request(1)
    source.sendNext("a").sendNext("b").sendComplete()
    val next = sink.requestNext(3.seconds)
    assert((failure, 1) == next)
  }

  it should "decide on failures based on the provided function" in {
    val bottom = Flow[(String, Long)].map {
      case (elem, ctx) => (Success(elem), ctx)
    }
    val failureDecider = (out: Try[String]) => out.isFailure || out.equals(Success("a")) // treat "a" as a failure for retry
    val settings = RetrySettings[String, String, Long](2, failureDecider = Option(failureDecider))
    val retry = Retry(settings)

    var context = 0L
    val (source, sink) = TestSource.probe[String]
      .map { s => context += 1; (s, context) }
      .via(retry.join(bottom))
      .toMat(TestSink.probe)(Keep.both).run()

    source.sendNext("a").sendNext("b")
    assert((Success("a"), 1) == sink.request(1).requestNext())
  }

  it should "Allow failure decider provided function in setting" in {
    val bottom = Flow[(String, Long)].map {
      case (elem, ctx) => (Success(elem), ctx)
    }
    val failureDecider = (out: Try[String]) => out.isFailure || out.equals(Success("a")) // treat "a" as a failure for retry
    val settings =
      RetrySettings[String, String, Long](2).withFailureDecider(failureDecider)
    val retry = Retry(settings)

    var context = 0L
    val (source, sink) = TestSource.probe[String]
      .map { s => context += 1; (s, context) }
      .via(retry.join(bottom))
      .toMat(TestSink.probe)(Keep.both).run()

    source.sendNext("a").sendNext("b")
    assert((Success("a"), 1) == sink.request(1).requestNext())
  }

  it should "drain all elements when upstream finishes" in {
    val bottom = Flow[(String, Long)].map {
      case ("1", ctx) => (Success("1"), ctx)
      case (_, ctx) => (failure, ctx)
    }
    val retry = Retry[String, String, Long](10)

    val (source, sink) = TestSource.probe[String]
      .map(x => (x.toString, x.toLong))
      .via(retry.join(bottom))
      .toMat(TestSink.probe)(Keep.both).run()

    source.sendNext("1").sendNext("2").sendNext("3").sendComplete()
    sink.request(3).expectNext((Success("1"), 1L)).expectNextUnordered((failure, 2L), (failure, 3L)).expectComplete()
    succeed
  }

  it should "backpressure when retryQ size reaches internal buffer size" in {
    val bottom = Flow[(String, Long)].map {
      case (elem, ctx) => if(ctx == 1) (failure, ctx) else (Success(elem), ctx)
    }

    val settings = RetrySettings[String, String, Long](1).withDelay(100 milliseconds)
    val retry = Retry[String, String, Long](settings).withAttributes(inputBuffer(initial = 1, max = 1))

    val sink = Source(1 to 3)
      .map(x => (x.toString, x.toLong))
      .via(retry.join(bottom))
      .runWith(TestSink.probe)

    sink.request(3)
      .expectNoMessage(100.millis)
      .expectNext((failure, 1L))
      .expectNext((Success("2"), 2L))
      .expectNext((Success("3"), 3L))
    succeed
  }

  it should "backpressure when retryQ size reaches configured threshold" in {
    val bottom = Flow[(String, Long)].map {
      case (elem, ctx) => if(ctx == 1) (failure, ctx) else (Success(elem), ctx)
    }

    val settings = RetrySettings[String, String, Long](1).withDelay(100 milliseconds).withMaxWaitingRetries(1)
    val retry = Retry[String, String, Long](settings)

    val sink = Source(1 to 3)
      .map(x => (x.toString, x.toLong))
      .via(retry.join(bottom))
      .runWith(TestSink.probe)

    sink.request(3)
      .expectNoMessage(100.millis)
      .expectNext((failure, 1L))
      .expectNext((Success("2"), 2L))
      .expectNext((Success("3"), 3L))
    succeed
  }

  it should "retry with a large data set" in {
    val bottom = Flow[(Long, Long)].map {
      case (elem, ctx) => if (ctx % 7 == 0) (failure, ctx) else (Success(elem), ctx) // fail every 7'th element
    }
    val retry = Retry[Long, Long, Long](1)

    val result = Source(1L to 10000)
      .map(x => (x, x))
      .via(retry.join(bottom))
      .runWith(Sink.seq)

    val expected = 1 to 10000 map (x => if (x % 7 == 0) (failure, x) else (Success(x), x))
    result map {
      _ should contain theSameElementsAs expected
    }
  }

  it should "delay each retried element by 1s when delay is duration is 1s" in {
    val bottom = Flow[(String, Long)].map {
      case (elem, ctx) => if (ctx % 2 == 0) (failure, ctx) else (Success(elem), ctx) // fail even elements
    }
    val retry = Retry(RetrySettings[String, String, Long](2, delay = 1 second))
    val testSink = Source(1L to 5L)
      .map(x => (x.toString, x))
      .via(retry.join(bottom))
      .runWith(TestSink.probe)

    testSink.request(5)
      .expectNextN((Success("1"), 1L) :: (Success("3"), 3L) :: (Success("5"), 5L) :: Nil)
      .expectNoMessage(2 second) // 2 x (1s delay)
      .expectNext((failure, 2L))
      .expectNext((failure, 4L))
      .expectComplete()
    succeed
  }

  it should "increase retry delay with backoff" in {
    val bottom = Flow[(String, Long)].map {
      case (elem, ctx) => if (ctx % 2 == 0) (failure, ctx) else (Success(elem), ctx)
    }
    val retry = Retry(RetrySettings[String, String, Long](max = 3, delay = 1 second, exponentialBackoffFactor = 2))
    val sink = Source(1L to 5L)
      .map(x => (x.toString, x))
      .via(retry.join(bottom))
      .runWith(TestSink.probe)

    sink.request(5)
      .expectNextN((Success("1"), 1L) :: (Success("3"), 3L) :: (Success("5"), 5L) :: Nil)
      .expectNoMessage(14.seconds) // (1s delay + 4s delay + 9s)
      .expectNext((failure, 2L))
      .expectNext((failure, 4L))
      .expectComplete()
    succeed
  }

  it should "backoff until maxDelay" in {
    val bottom = Flow[(String, Long)].map {
      case (elem, ctx) => if (ctx % 2 == 0) (failure, ctx) else (Success(elem), ctx)
    }
    val retry = Retry(RetrySettings[String, String, Long](max = 3, delay = 1 second, exponentialBackoffFactor = 2,
      maxDelay = 4.seconds))
    val sink = Source(1L to 5L)
      .map(x => (x.toString, x))
      .via(retry.join(bottom))
      .runWith(TestSink.probe)

    sink.request(5)
      .expectNextN((Success("1"), 1L) :: (Success("3"), 3L) :: (Success("5"), 5L) :: Nil)
      .expectNoMessage(9.seconds) // (1s + 4s + 4s maxDelay)
      .expectNext((failure, 2L))
      .expectNext((failure, 4L))
      .expectComplete()
    succeed
  }

  it should "retry with backoff and a delay using settings" in {
    val bottom = Flow[(String, Long)].map {
      case (elem, ctx) => if (ctx % 2 == 0) (failure, ctx) else (Success(elem), ctx)
    }
    val retrySettings = RetrySettings[String, String, Long](2)
      .withDelay(1 second)
      .withMaxDelay(5.seconds)
      .withExponentialBackoff(2)

    val retry = Retry[String, String, Long](retrySettings)

    val sink = Source(1L to 5L)
      .map(x => (x.toString, x))
      .via(retry.join(bottom))
      .runWith(TestSink.probe)

    sink.request(5)
      .expectNext((Success("1"), 1L), (Success("3"), 3L), (Success("5"), 5L))
      .expectNoMessage(5.seconds)
      .expectNext((failure, 2L))
      .expectNext((failure, 4L))
      .expectComplete()
    succeed
  }

  it should "retry with a joined flow that buffers" in {
    val bottom = Flow[(Long, Long)].map {
      case (elem, ctx) => if (ctx % 2 == 0) (failure, ctx) else (Success(elem), ctx) // fail every even element
    }
      .buffer(50, OverflowStrategy.backpressure)

    val retry = Retry[Long, Long, Long](2)
    val result = Source(1L to 100)
      .map(x => (x, x))
      .via(retry.join(bottom))
      .runWith(Sink.seq)

    val expected = 1 to 100 map (x => if (x % 2 == 0) (failure, x) else (Success(x), x))
    result map {
      _ should contain theSameElementsAs expected
    }
  }

  it should "retry when a downstream flow throttles" in {
    val bottom = Flow[(Long, Long)].map {
      case (elem, ctx) => if (ctx % 2 == 0) (failure, ctx) else (Success(elem), ctx) // fail every even element
    }

    val retry = Retry[Long, Long, Long](2)
    val result = Source(1L to 100)
      .map(x => (x, x))
      .via(retry.join(bottom))
      .throttle(1, 10.millis, 10, ThrottleMode.shaping)
      .runWith(Sink.seq)

    val expected = 1 to 100 map (x => if (x % 2 == 0) (failure, x) else (Success(x), x))
    result map {
      _ should contain theSameElementsAs expected
    }
  }

  it should "not backpressure if downstream demands more and retryQ is not growing" in {
    // https://github.com/paypal/squbs/issues/623
    val delayActor = system.actorOf(Props[RetryDelayActor])
    import akka.pattern.ask
    implicit val askTimeout = akka.util.Timeout(10 seconds)

    val delayFlow =
      Flow[(Long, Long)]
        .mapAsyncUnordered(100)(elem => (delayActor ? elem).mapTo[(Long, Long)])
        .map { case (elem, ctx) => (Success(elem), ctx) }

    val retry = Retry[Long, Long, Long](2).withAttributes(inputBuffer(initial = 1, max = 1))
    val stream = Source(1L to 500)
      .map(x => (x, x))
      .via(retry.join(delayFlow))
      .runWith(Sink.seq)

    // It should not impact the throughput when the stream is happy.  If it were to back pressure, it would take
    // 500 seconds, because internal buffer size is 1 and each takes 1 second.  The current setup with concurrency
    // level 100, it should take 500 / 100 = 5 seconds.  Setting it to 10 seconds to prevent Travis CI problems.
    val result = Await.result(stream, 10 seconds)
    result should contain theSameElementsAs (1L to 500 map(elem => (Success(elem), elem)))
  }

  it should "not backpressure if downstream demands more and retryQ is not growing with larger internal buffer size" in {
    // https://github.com/paypal/squbs/issues/623
    val delayActor = system.actorOf(Props[RetryDelayActor])
    import akka.pattern.ask
    implicit val askTimeout = akka.util.Timeout(10 seconds)

    val delayFlow =
      Flow[(Long, Long)]
        .mapAsyncUnordered(100)(elem => (delayActor ? elem).mapTo[(Long, Long)])
        .map { case (elem, ctx) => (Success(elem), ctx) }

    val retry = Retry[Long, Long, Long](2).withAttributes(inputBuffer(initial = 16, max = 16))
    val stream = Source(1L to 500)
      .map(x => (x, x))
      .via(retry.join(delayFlow))
      .runWith(Sink.seq)

    // It should not impact the throughput when the stream is happy.  If it were to back pressure, it would take
    // 500 seconds, because internal buffer size is 1 and each takes 1 second.  The current setup with concurrency
    // level 100, it should take 500 / 100 = 5 seconds.  Setting it to 10 seconds to prevent Travis CI problems.
    val result = Await.result(stream, 10 seconds)
    result should contain theSameElementsAs (1L to 500 map(elem => (Success(elem), elem)))
  }
}

class RetryDelayActor extends Actor {

  import context.dispatcher

  def receive = {
    case element => context.system.scheduler.scheduleOnce(1 seconds, sender(), element)
  }
}