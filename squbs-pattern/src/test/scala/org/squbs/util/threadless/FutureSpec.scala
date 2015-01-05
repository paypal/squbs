/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the AUTHORS file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.squbs.util.threadless

import org.scalatest.{FunSpec, Matchers}
import scala.util.{Try, Failure, Success}
import scala.language.postfixOps
import java.util.NoSuchElementException
import java.lang.{RuntimeException, IllegalArgumentException}
import scala.IllegalArgumentException
import scala.RuntimeException

class FutureSpec extends FunSpec with Matchers {

  //the key test case follows, as `threadless` indicates, the callbacks (success/failure) should be run within the same thread
  //other than common `scala.concurrent.Future`

  it("should run success callbacks in the same thread") {

    val threadIdentity = Thread.currentThread
    val accuracy = 1024

    val p = Promise[String]()
    val f = p.future

    var acks = Seq[Boolean]()

    1 to accuracy foreach(_ => f.onSuccess({
        case v:String => acks = acks :+ (Thread.currentThread == threadIdentity)
        case _ => throw new IllegalStateException
      }))

    p.success("value")

    f.value should equal(Some(Success("value")))

    acks should equal(Seq.fill[Boolean](accuracy)(true))
  }

  it("should run failure callbacks in the same thread"){

    val threadIdentity = Thread.currentThread
    val accuracy = 1024

    val p = Promise[String]()
    val f = p.future

    var acks = Seq[Boolean]()

    1 to accuracy foreach(_ => f.onFailure({
      case t:Throwable => acks = acks :+ (Thread.currentThread == threadIdentity)
      case _ => throw new IllegalStateException
    }))

    val cause = new IllegalArgumentException
    p.failure(cause)

    f.value should equal(Some(Failure(cause)))

    acks should equal(Seq.fill[Boolean](accuracy)(true))
  }

  //now we borrow some test cases from: https://github.com/scala/scala/blob/master/test/files/jvm/future-spec/FutureTests.scala
  //for completeness and orchestration functions.

  it("should compose with for-comprehensions") {

    def async(x: Int) = Future.successful((x * 2).toString)

    val future0 = Future.successful("five!".length)

    val future1 = for {
      a <- future0.mapTo[Int]  // returns 5
      b <- async(a)            // returns "10"
      c <- async(7)            // returns "14"
    } yield b + "-" + c

    future1 shouldNot be(null)
    future1.isCompleted should equal(true)
    future1.value should equal(Some(Success("10-14")))

    val future2 = for {
      a <- future0.mapTo[Int]
      b <- Future.successful((a * 2).toString).mapTo[Int]
      c <- Future.successful((7 * 2).toString)
    } yield b + "-" + c

    future2 shouldNot be(null)
    future2.isCompleted should equal(true)
    future2.value match {
      case Some(Failure(ex)) => ex.getClass should equal(classOf[ClassCastException])
      case _ => fail("value should be a class cast exception due to `b <- Future.successful((a * 2).toString).mapTo[Int]`")
    }
  }

  it("should be able to recover from exceptions") {

    val future1 = Future.successful(5)
    val future2 = future1 map (_ / 0)
    val future3 = future2 map (_.toString)

    val future4 = future1 recover {
      case e: ArithmeticException => 0
    } map (_.toString)

    val future5 = future2 recover {
      case e: ArithmeticException => 0
    } map (_.toString)

    val future6 = future2 recover {
      case e: MatchError => 0
    } map (_.toString)

    val future7 = future3 recover {
      case e: ArithmeticException => "You got ERROR"
    }

    future1.value should equal(Some(Success(5)))
    future2.value match {
      case Some(Failure(ex)) => ex.getClass should equal(classOf[ArithmeticException])
      case _ => fail("value should be an arithmetic exception due to `future1 map (_ / 0)`")
    }
    future3.value match {
      case Some(Failure(ex)) => ex.getClass should equal(classOf[ArithmeticException])
      case _ => fail("value should be an arithmetic exception due to `future2`")
    }
    future4.value should equal(Some(Success("5")))
    future5.value should equal(Some(Success("0")))
    future6.value match {
      case Some(Failure(ex)) => ex.getClass should equal(classOf[ArithmeticException])
      case _ => fail("value should be an arithmetic exception due to `future2`")
    }
    future7.value should equal(Some(Success("You got ERROR")))
  }

  it("should recoverWith from exceptions") {
    val o = new IllegalStateException("original")
    val r = new IllegalStateException("recovered")

    val r0 = Future.failed[String](o) recoverWith {
      case _ if false == true => Future.successful("yay")
    }
    r0 shouldNot be(null)
    r0.isCompleted should equal(true)
    r0.value should equal(Some(Failure(o)))

    val recovered = Future.failed[String](o) recoverWith {
      case _ => Future.successful("yay!")
    }
    recovered.isCompleted should equal(true)
    recovered.value should equal(Some(Success("yay!")))

    val refailed = Future.failed[String](o) recoverWith {
      case _ => Future.failed[String](r)
    }
    refailed.isCompleted should equal(true)
    refailed.value should equal(Some(Failure(r)))
  }

  it("andThen should work as expected") {
      val q = new java.util.concurrent.LinkedBlockingQueue[Int]
      for (i <- 1 to 1000) {
        val chained = Future.successful({
          q.add(1); 3
        }) andThen {
          case _ => q.add(2)
        } andThen {
          case Success(0) => q.add(Int.MaxValue)
        } andThen {
          case _ => q.add(3);
        }

        chained.value should equal(Some(Success(3)))
        q.poll() should equal (1)
        q.poll() should equal (2)
        q.poll() should equal (3)
        q.clear()
      }
  }

  it("should get firstCompletedOf") {

    def futures = Vector.fill[Future[Int]](10) {
      Promise[Int]().future
    } :+ Future.successful[Int](5)

    Future.firstCompletedOf(futures).value should equal (Some(Success(5)))
    Future.firstCompletedOf(futures.iterator).value should equal (Some(Success(5)))
  }

  it("should find the future") {
    val futures = for (i <- 1 to 10) yield Future.successful(i)

    val result = Future.find[Int](futures)(_ == 3)
    result.value should equal (Some(Success(Some(3))))

    val notFound = Future.find[Int](futures.iterator)(_ == 11)
    notFound.value should equal(Some(Success(None)))
  }

  it("should support zip function") {
    val f = new IllegalStateException("test")

    val zip0 = Future.failed[String](f) zip Future.successful("foo")
    zip0.value should equal(Some(Failure(f)))

    val zip1 = Future.successful("foo") zip Future.failed[String](f)
    zip1.value should equal(Some(Failure(f)))

    val zip2 = Future.failed[String](f) zip Future.failed[String](f)
    zip2.value should equal(Some(Failure(f)))

    val zip3 = Future.successful("foo") zip Future.successful("foo")
    zip3.value should equal(Some(Success(("foo", "foo"))))
  }

  it("should support fold function") {

    def async(add: Int) = Future.successful(add)

    val futures = (0 to 9) map {
      idx => async(idx)
    }

    val folded = Future.fold(futures)(0)(_ + _)
    folded.value should equal(Some(Success(45)))
  }

  it("should support fold by composing") {

    def futures = (0 to 9) map {
      idx => Future.successful(idx)
    }
    val folded = futures.foldLeft(Future.successful(0)) {
      case (fr, fa) => for (r <- fr; a <- fa) yield (r + a)
    }

    folded.value should equal(Some(Success(45)))
  }

  it("should show exception in the folding process") {

    def async(add: Int):Future[Int] =
      if (add == 6)
        Future.failed(new IllegalArgumentException("shouldFoldResultsWithException: expected"))
      else
        Future.successful(add)

    def futures = (0 to 9) map {
      idx => async(idx)
    }

    val folded = Future.fold(futures)(0)(_ + _)
    folded.value match {
      case Some(Failure(ex)) =>
        ex.getMessage should equal("shouldFoldResultsWithException: expected")
      case _ =>
        fail("value should be exception with message above due to `if (add == 6)\n Future.failed(new IllegalArgumentException(\"shouldFoldResultsWithException: expected\"))`")
    }
  }

  it("should return zero when the folding list is empty") {

    val zero = Future.fold(List[Future[Int]]())(0)(_ + _)

    zero.value should equal(Some(Success(0)))
  }

  it("should support reduce function") {

    val futures = (0 to 9) map {Future.successful(_)}
    val reduced = Future.reduce(futures)(_ + _)

    reduced.value should equal(Some(Success(45)))
  }

  it("should show exception in the reducing process") {

    def async(add: Int):Future[Int] =
      if (add == 6)
        Future.failed(new IllegalArgumentException("shouldReduceResultsWithException: expected"))
      else
        Future.successful(add)

    def futures = (0 to 9) map {
      idx => async(idx)
    }

    val folded = Future.reduce(futures)(_ + _)
    folded.value match {
      case Some(Failure(ex)) =>
        ex.getMessage should equal("shouldReduceResultsWithException: expected")
      case _ =>
        fail("value should be exception with message above due to `if (add == 6)\n Future.failed(new IllegalArgumentException(\"shouldReduceResultsWithException: expected\"))`")
    }
  }

  it("should throw exception when reducing an empty list") {

    val reduced = Future.reduce(List[Future[Int]]())(_ + _)
    reduced.value match {
      case Some(Failure(ex)) => ex.getClass should equal(classOf[NoSuchElementException])
      case _ => fail("should have got failure due to empty list reducing")
    }
  }

  it("should support functions: filter, collect, fallback") {
    var p = Promise[String]()
    var f = p.future

    p.success("abc")

    var newFuture = f.filter(s => s.equals("abc"))
    newFuture.value.get.get should be("abc")
    newFuture = f.withFilter(s => s.equals("abc"))
    newFuture.value.get.get should be("abc")

    newFuture = f.filter(s => s.equals("abcd"))
    newFuture.value.get.failed.get shouldBe a[NoSuchElementException]
    newFuture.value.get.failed.get.getMessage should be("Future.filter predicate is not satisfied")

    newFuture = f.collect{
      case "abc" => "OK"
    }
    newFuture.value.get.get should be("OK")
    newFuture = f.collect{
      case "abcd" => "OK"
    }
    newFuture.value.get.failed.get shouldBe a[NoSuchElementException]
    newFuture.value.get.failed.get.getMessage should be("Future.collect partial function is not defined at: abc")

    newFuture = f.fallbackTo(Future.successful("haha"))
    newFuture.value.get.get should be("abc")

    p = Promise[String]()
    f = p.future

    p.failure(new RuntimeException("BadMan"))
    newFuture = f.filter(s => s.equals("abc"))
    newFuture.value.get.failed.get shouldBe a[RuntimeException]
    newFuture.value.get.failed.get.getMessage should be("BadMan")

    newFuture = f.collect{
      case "abcd" => "OK"
    }
    newFuture.value.get.failed.get shouldBe a[RuntimeException]
    newFuture.value.get.failed.get.getMessage should be("BadMan")

    newFuture = f.fallbackTo(Future.successful("haha"))
    newFuture.value.get.get should be("haha")

  }


  it("should support functions: failed ,apply ,foreach, transform") {
    var p = Promise[String]()
    var f = p.future

    val func : Try[Throwable] => String = {
      case Success(v) => v.getMessage
      case Failure(t) => t.getMessage
    }

    p.success("abc")


    var result : String = null
    f.foreach {
      a => result =a.toUpperCase
    }
    result should be("ABC")

    var transFuture = f.transform(s => s + "def", t => new IllegalArgumentException(t))
    transFuture.value.get should be(Success("abcdef"))

    f.failed.value.map(func).get should be ("Future.failed not completed with a throwable.")
    f() should be("abc")


    p = Promise[String]()
    f = p.future

    the[NoSuchElementException] thrownBy {
      f()
    } should have message("Future not completed.")

    p.failure(new RuntimeException("BadMan"))

    result = "aaa"
    f.foreach {
      a => result =a.toUpperCase
    }
    result should be("aaa")

    transFuture = f.transform(s => s + "def", t => new IllegalArgumentException(t))
    transFuture.value.get.failed.get shouldBe a[IllegalArgumentException]
    transFuture.value.get.failed.get.getCause shouldBe a[RuntimeException]

    an [RuntimeException] should be thrownBy f()
    f.failed.value.map(func).get should be ("BadMan")

  }

  it("should support traversal") {
      object counter {
        var count = -1
        def incAndGet() = counter.synchronized {
          count += 2
          count
        }
      }

      val oddFutures = List.fill(100)(Future.successful(counter.incAndGet())).iterator
      val traversed = Future.sequence(oddFutures)
      traversed.value match {
        case Some(Success(list:Iterator[Int])) => list.sum should equal(10000)
        case _ => fail("should have got a list of integers")
      }

      val list = (1 to 100).toList
      val traversedList = Future.traverse(list)(x => Future.successful(x * 2 - 1))
      traversedList.value match {
        case Some(Success(list:List[Int])) => list.sum should equal(10000)
        case _ => fail("should have got a list of integers")
      }

      val iterator = (1 to 100).toList.iterator
      val traversedIterator = Future.traverse(iterator)(x => Future.successful(x * 2 - 1))
      traversedIterator.value match {
        case Some(Success(list:Iterator[Int])) => list.sum should equal(10000)
        case _ => fail("should have got a list of integers")
      }
  }
}
