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

package org.squbs.pattern.orchestration

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.util.NoSuchElementException
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

class OFutureSpec extends AnyFunSpec with Matchers {

  //the key test case follows, as `threadless` indicates, the callbacks (success/failure) should be run within the same thread
  //other than common `scala.concurrent.Future`

  it("should run success callbacks in the same thread") {

    val threadIdentity = Thread.currentThread
    val accuracy = 1024

    val p = OPromise[String]()
    val f = p.future

    var ack = Seq[Boolean]()

    1 to accuracy foreach(_ => f.onSuccess({
        case v:String => ack = ack :+ (Thread.currentThread == threadIdentity)
        case _ => throw new IllegalStateException
      }))

    p.success("value")

    f.value should equal(Some(Success("value")))

    ack should equal(Seq.fill[Boolean](accuracy)(true))
  }

  it("should run failure callbacks in the same thread"){

    val threadIdentity = Thread.currentThread
    val accuracy = 1024

    val p = OPromise[String]()
    val f = p.future

    var ack = Seq[Boolean]()

    1 to accuracy foreach(_ => f.onFailure({
      case t:Throwable => ack = ack :+ (Thread.currentThread == threadIdentity)
      case _ => throw new IllegalStateException
    }))

    val cause = new IllegalArgumentException
    p.failure(cause)

    f.value should equal(Some(Failure(cause)))

    ack should equal(Seq.fill[Boolean](accuracy)(true))
  }

  //now we borrow some test cases from: https://github.com/scala/scala/blob/master/test/files/jvm/future-spec/FutureTests.scala
  //for completeness and orchestration functions.

  it("should compose with for-comprehensions") {

    def async(x: Int) = OFuture.successful((x * 2).toString)

    val future0 = OFuture.successful("five!".length)

    val future1 = for {
      a <- future0.mapTo[Int]  // returns 5
      b <- async(a)            // returns "10"
      c <- async(7)            // returns "14"
    } yield b + "-" + c

    future1 should not be null
    future1.isCompleted should equal(true)
    future1.value should equal(Some(Success("10-14")))

    val future2 = for {
      a <- future0.mapTo[Int]
      b <- OFuture.successful((a * 2).toString).mapTo[Int]
      c <- OFuture.successful((7 * 2).toString)
    } yield b + "-" + c

    future2 should not be null
    future2.isCompleted should equal(true)
    future2.value match {
      case Some(Failure(ex)) => ex.getClass should equal(classOf[ClassCastException])
      case _ => fail("value should be a class cast exception due to `b <- Future.successful((a * 2).toString).mapTo[Int]`")
    }
  }

  it("should be able to recover from exceptions") {

    val future1 = OFuture.successful(5)
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

    val r0 = OFuture.failed[String](o) recoverWith {
      case _ if false => OFuture.successful("yay")
    }
    r0.isCompleted should equal(true)
    r0.value should equal(Some(Failure(o)))

    val recovered = OFuture.failed[String](o) recoverWith {
      case _ => OFuture.successful("yay!")
    }
    recovered.isCompleted should equal(true)
    recovered.value should equal(Some(Success("yay!")))

    val reFailed = OFuture.failed[String](o) recoverWith {
      case _ => OFuture.failed[String](r)
    }
    reFailed.isCompleted should equal(true)
    reFailed.value should equal(Some(Failure(r)))
  }

  it("andThen should work as expected") {
      val q = new java.util.concurrent.LinkedBlockingQueue[Int]
      for (i <- 1 to 1000) {
        val chained = OFuture.successful({
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

    def futures = Vector.fill[OFuture[Int]](10) {
      OPromise[Int]().future
    } :+ OFuture.successful[Int](5)

    OFuture.firstCompletedOf(futures).value should equal (Some(Success(5)))
    OFuture.firstCompletedOf(futures.iterator).value should equal (Some(Success(5)))
  }

  it("should find the future") {
    val futures = for (i <- 1 to 10) yield OFuture.successful(i)

    val result = OFuture.find[Int](futures)(_ == 3)
    result.value should equal (Some(Success(Some(3))))

    val notFound = OFuture.find[Int](futures.iterator)(_ == 11)
    notFound.value should equal(Some(Success(None)))
  }

  it("should support zip function") {
    val f = new IllegalStateException("test")

    val zip0 = OFuture.failed[String](f) zip OFuture.successful("foo")
    zip0.value should equal(Some(Failure(f)))

    val zip1 = OFuture.successful("foo") zip OFuture.failed[String](f)
    zip1.value should equal(Some(Failure(f)))

    val zip2 = OFuture.failed[String](f) zip OFuture.failed[String](f)
    zip2.value should equal(Some(Failure(f)))

    val zip3 = OFuture.successful("foo") zip OFuture.successful("foo")
    zip3.value should equal(Some(Success(("foo", "foo"))))
  }

  it("should support fold function") {

    def async(add: Int) = OFuture.successful(add)

    val futures = (0 to 9) map {
      idx => async(idx)
    }

    val folded = OFuture.fold(futures)(0)(_ + _)
    folded.value should equal(Some(Success(45)))
  }

  it("should support fold by composing") {

    def futures = (0 to 9) map {
      idx => OFuture.successful(idx)
    }
    val folded = futures.foldLeft(OFuture.successful(0)) {
      case (fr, fa) => for (r <- fr; a <- fa) yield r + a
    }

    folded.value should equal(Some(Success(45)))
  }

  it("should show exception in the folding process") {

    def async(add: Int):OFuture[Int] =
      if (add == 6)
        OFuture.failed(new IllegalArgumentException("shouldFoldResultsWithException: expected"))
      else
        OFuture.successful(add)

    def futures = (0 to 9) map {
      idx => async(idx)
    }

    val folded = OFuture.fold(futures)(0)(_ + _)
    folded.value match {
      case Some(Failure(ex)) =>
        ex.getMessage should equal("shouldFoldResultsWithException: expected")
      case _ =>
        fail("value should be exception with message above due to `if (add == 6)\n Future.failed(new IllegalArgumentException(\"shouldFoldResultsWithException: expected\"))`")
    }
  }

  it("should return zero when the folding list is empty") {

    val zero = OFuture.fold(List[OFuture[Int]]())(0)(_ + _)

    zero.value should equal(Some(Success(0)))
  }

  it("should support reduce function") {

    val futures = (0 to 9) map OFuture.successful
    val reduced = OFuture.reduce(futures)(_ + _)

    reduced.value should equal(Some(Success(45)))
  }

  it("should show exception in the reducing process") {

    def async(add: Int):OFuture[Int] =
      if (add == 6)
        OFuture.failed(new IllegalArgumentException("shouldReduceResultsWithException: expected"))
      else
        OFuture.successful(add)

    def futures = (0 to 9) map {
      idx => async(idx)
    }

    val folded = OFuture.reduce(futures)(_ + _)
    folded.value match {
      case Some(Failure(ex)) =>
        ex.getMessage should equal("shouldReduceResultsWithException: expected")
      case _ =>
        fail("value should be exception with message above due to `if (add == 6)\n Future.failed(new IllegalArgumentException(\"shouldReduceResultsWithException: expected\"))`")
    }
  }

  it("should throw exception when reducing an empty list") {

    val reduced = OFuture.reduce(List[OFuture[Int]]())(_ + _)
    reduced.value match {
      case Some(Failure(ex)) => ex.getClass should equal(classOf[NoSuchElementException])
      case _ => fail("should have got failure due to empty list reducing")
    }
  }

  it("should support functions: filter, collect, fallback") {
    val p = OPromise[String]()
    val f = p.future

    p.success("abc")

    val newFuture = f.filter(s => s.equals("abc"))
    newFuture.value.get.get should be("abc")
    val newFuture2 = f.withFilter(s => s.equals("abc"))
    newFuture2.value.get.get should be("abc")

    val newFuture3 = f.filter(s => s.equals("abcd"))
    newFuture3.value.get.failed.get shouldBe a[NoSuchElementException]
    newFuture3.value.get.failed.get.getMessage should be("Future.filter predicate is not satisfied")

    val newFuture4 = f.collect{
      case "abc" => "OK"
    }
    newFuture4.value.get.get should be("OK")
    val newFuture5 = f.collect {
      case "abcd" => "OK"
    }
    newFuture5.value.get.failed.get shouldBe a[NoSuchElementException]
    newFuture5.value.get.failed.get.getMessage should be("Future.collect partial function is not defined at: abc")

    val newFuture6 = f.fallbackTo(OFuture.successful("haha"))
    newFuture6.value.get.get should be("abc")

    val p2 = OPromise[String]()
    val f2 = p2.future

    p2.failure(new RuntimeException("BadMan"))
    val newFuture7 = f2.filter(s => s.equals("abc"))
    newFuture7.value.get.failed.get shouldBe a[RuntimeException]
    newFuture7.value.get.failed.get.getMessage should be("BadMan")

    val newFuture8 = f2.collect{
      case "abcd" => "OK"
    }
    newFuture8.value.get.failed.get shouldBe a[RuntimeException]
    newFuture8.value.get.failed.get.getMessage should be("BadMan")

    val newFuture9 = f2.fallbackTo(OFuture.successful("haha"))
    newFuture9.value.get.get should be("haha")
  }


  it("should support functions: failed ,apply ,foreach, transform") {
    val p = OPromise[String]()
    val f = p.future

    val func : Try[Throwable] => String = {
      case Success(v) => v.getMessage
      case Failure(t) => t.getMessage
    }

    p.success("abc")


    var result = ""
    f.foreach {
      a => result = a.toUpperCase
    }
    result should be("ABC")

    val transFuture = f.transform(s => s + "def", t => new IllegalArgumentException(t))
    transFuture.value.get should be (Success("abcdef"))

    f.failed.value.map(func).get should be ("Future.failed not completed with a throwable.")
    f() should be ("abc")


    val p1 = OPromise[String]()
    val f1 = p1.future

    the[NoSuchElementException] thrownBy {
      f1()
    } should have message "Future not completed."

    p1.failure(new RuntimeException("BadMan"))

    result = "aaa"
    f1.foreach {
      a => result = a.toUpperCase
    }
    result should be("aaa")

    val transFuture1 = f1.transform(s => s + "def", t => new IllegalArgumentException(t))
    transFuture1.value.get.failed.get shouldBe a[IllegalArgumentException]
    transFuture1.value.get.failed.get.getCause shouldBe a[RuntimeException]

    a [RuntimeException] should be thrownBy f1()
    f1.failed.value map func getOrElse "" should be ("BadMan")

  }

  it("should support traversal") {
      object counter {
        var count = -1
        def incAndGet() = counter.synchronized {
          count += 2
          count
        }
      }

      val oddFutures = List.fill(100)(OFuture.successful(counter.incAndGet())).iterator
      val traversed = OFuture.sequence(oddFutures)
      traversed.value match {
        case Some(Success(list:Iterator[Int])) => list.sum should equal(10000)
        case _ => fail("should have got a list of integers")
      }

      val list = (1 to 100).toList
      val traversedList = OFuture.traverse(list)(x => OFuture.successful(x * 2 - 1))
      traversedList.value match {
        case Some(Success(list:List[Int])) => list.sum should equal(10000)
        case _ => fail("should have got a list of integers")
      }

      val iterator = (1 to 100).toList.iterator
      val traversedIterator = OFuture.traverse(iterator)(x => OFuture.successful(x * 2 - 1))
      traversedIterator.value match {
        case Some(Success(list:Iterator[Int])) => list.sum should equal(10000)
        case _ => fail("should have got a list of integers")
      }
  }
}
