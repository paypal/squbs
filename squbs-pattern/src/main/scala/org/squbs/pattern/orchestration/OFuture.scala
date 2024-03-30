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

import scala.collection.compat._
import scala.collection.mutable.ArrayBuffer
import scala.reflect.ClassTag
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}
import scala.language.higherKinds

/** The trait that represents futures like scala.concurrent.Future BUT works in a single threaded environment.
  * It does not have blocking calls to the future and a future cannot be created from passing a closure.
  * The future is obtained from the corresponding Promise or translated from one or more other Futures.
  *
  *  Asynchronous computations that yield futures are created with the `future` call:
  *
  *  {{{
  *  val s = "Hello"
  *  val f: Future[String] = future {
  *    s + " future!"
  *  }
  *  f onSuccess {
  *    case msg => println(msg)
  *  }
  *  }}}
  *
  *  @author  Philipp Haller, Heather Miller, Aleksandar Prokopec, Viktor Klang
  *
  *  @define multipleCallbacks
  *  Multiple callbacks may be registered; there is no guarantee that they will be
  *  executed in a particular order.
  *
  *  @define caughtThrowables
  *  The future may contain a throwable object and this means that the future failed.
  *  Futures obtained through combinators have the same exception as the future they were obtained from.
  *  The following throwable objects are not contained in the future:
  *  - `Error` - errors are not contained within futures
  *  - `InterruptedException` - not contained within futures
  *  - all `scala.util.control.ControlThrowable` except `NonLocalReturnControl` - not contained within futures
  *
  *  Instead, the future is completed with a ExecutionException with one of the exceptions above
  *  as the cause.
  *  If a future is failed with a `scala.runtime.NonLocalReturnControl`,
  *  it is completed with a value from that throwable instead.
  *
  *  @define nonDeterministic
  *  Note: using this method yields nondeterministic dataflow programs.
  *
  *  @define forComprehensionExamples
  *  Example:
  *
  *  {{{
  *  val f = future { 5 }
  *  val g = future { 3 }
  *  val h = for {
  *    x: Int <- f // returns Future(5)
  *    y: Int <- g // returns Future(5)
  *  } yield x + y
  *  }}}
  *
  *  is translated to:
  *
  *  {{{
  *  f flatMap { (x: Int) => g map { (y: Int) => x + y } }
  *  }}}
  *
  * @define callbackInContext
  * The provided callback always runs in the provided implicit
  *`ExecutionContext`, though there is no guarantee that the
  * `execute()` method on the `ExecutionContext` will be called once
  * per callback or that `execute()` will be called in the current
  * thread. That is, the implementation may run multiple callbacks
  * in a batch within a single `execute()` and it may run
  * `execute()` either immediately or asynchronously.
  */
@deprecated("The Orchestration module is deprecated. Please use Pekko streams for safer orchestration instead.",
  since = "0.15.0")
trait OFuture[+T] {

  /* Callbacks */

  /** When this future is completed successfully (i.e. with a value),
    *  apply the provided partial function to the value if the partial function
    *  is defined at that value.
    *
    *  If the future has already been completed with a value,
    *  this will either be applied immediately or be scheduled asynchronously.
    *
    *  $multipleCallbacks
    *  $callbackInContext
    */
  def onSuccess[U](pf: PartialFunction[T, U]): Unit = onComplete {
    case Success(v) if pf isDefinedAt v => pf(v)
    case _ =>
  }

  /** When this future is completed with a failure (i.e. with a throwable),
    *  apply the provided callback to the throwable.
    *
    *  $caughtThrowables
    *
    *  If the future has already been completed with a failure,
    *  this will either be applied immediately or be scheduled asynchronously.
    *
    *  Will not be called in case that the future is completed with a value.
    *
    *  $multipleCallbacks
    *  $callbackInContext
    */
  def onFailure[U](callback: PartialFunction[Throwable, U]): Unit = onComplete {
    case Failure(t) if NonFatal(t) && callback.isDefinedAt(t) => callback(t)
    case _ =>
  }

  /** When this future is completed, either through an exception, or a value,
    *  apply the provided function.
    *
    *  If the future has already been completed,
    *  this will either be applied immediately or be scheduled asynchronously.
    *
    *  $multipleCallbacks
    *  $callbackInContext
    */
  def onComplete[U](func: Try[T] => U): Unit


  /* Miscellaneous */

  /** Returns whether the future has already been completed with
    *  a value or an exception.
    *
    *  $nonDeterministic
    *
    *  @return    `true` if the future is already completed, `false` otherwise
    */
  def isCompleted: Boolean

  /** The value of this `Future`.
    *
    *  If the future is not completed the returned value will be `None`.
    *  If the future is completed the value will be `Some(Success(t))`
    *  if it contains a valid result, or `Some(Failure(error))` if it contains
    *  an exception.
    */
  def value: Option[Try[T]]


  /* Projections */

  /** Returns a failed projection of this future.
    *
    *  The failed projection is a future holding a value of type `Throwable`.
    *
    *  It is completed with a value which is the throwable of the original future
    *  in case the original future is failed.
    *
    *  It is failed with a `NoSuchElementException` if the original future is completed successfully.
    *
    *  Blocking on this future returns a value if the original future is completed with an exception
    *  and throws a corresponding exception if the original future fails.
    */
  def failed: OFuture[Throwable] = {
    val p = OPromise[Throwable]()

    onComplete {
      case Failure(t) => p success t
      case Success(v) => p failure new NoSuchElementException("Future.failed not completed with a throwable.")
    }

    p.future
  }

  /** Returns the successful projection of this future.
    *
    * If the future has not been completed, a NoSuchElementException is thrown.
    * If the future failed, the exception causing the failure is thrown.
    * @return The value of this future, provided it is completed.
    */
  def apply(): T = {
    value match {
      case Some(Failure(t)) => throw t
      case Some(Success(v)) => v
      case None => throw new NoSuchElementException("Future not completed.")
    }
  }


  /* Monadic operations */

  /** Asynchronously processes the value in the future once the value becomes available.
    *
    *  Will not be called if the future fails.
    */
  def foreach[U](f: T => U): Unit = onComplete {
    case Success(r) => f(r)
    case _  => // do nothing
  }

  /** Creates a new future by applying the 's' function to the successful result of
    *  this future, or the 'f' function to the failed result. If there is any non-fatal
    *  exception thrown when 's' or 'f' is applied, that exception will be propagated
    *  to the resulting future.
    *
    *  @param  s  function that transforms a successful result of the receiver into a
    *             successful result of the returned future
    *  @param  f  function that transforms a failure of the receiver into a failure of
    *             the returned future
    *  @return    a future that will be completed with the transformed value
    */
  def transform[S](s: T => S, f: Throwable => Throwable): OFuture[S] = {
    val p = OPromise[S]()

    onComplete {
      case result =>
        try {
          result match {
            case Failure(t)  => p failure f(t)
            case Success(r) => p success s(r)
          }
        } catch {
          case NonFatal(t) => p failure t
        }
    }

    p.future
  }

  /** Creates a new future by applying a function to the successful result of
    *  this future. If this future is completed with an exception then the new
    *  future will also contain this exception.
    *
    *  $forComprehensionExamples
    */
  def map[S](f: T => S): OFuture[S] = { // transform(f, identity)
  val p = OPromise[S]()

    onComplete {
      case result =>
        try {
          result match {
            case Success(r) => p success f(r)
            case f: Failure[_] => p complete f.asInstanceOf[Failure[S]]
          }
        } catch {
          case NonFatal(t) => p failure t
        }
    }

    p.future
  }

  /** Creates a new future by applying a function to the successful result of
    *  this future, and returns the result of the function as the new future.
    *  If this future is completed with an exception then the new future will
    *  also contain this exception.
    *
    *  $forComprehensionExamples
    */
  def flatMap[S](f: T => OFuture[S]): OFuture[S] = {
    val p = OPromise[S]()

    onComplete {
      case f: Failure[_] => p complete f.asInstanceOf[Failure[S]]
      case Success(v) =>
        try {
          f(v).onComplete({
            case f: Failure[_] => p complete f.asInstanceOf[Failure[S]]
            case Success(v0) => p success v0
          })
        } catch {
          case NonFatal(t) => p failure t
        }
    }

    p.future
  }

  /** Creates a new future by filtering the value of the current future with a predicate.
    *
    *  If the current future contains a value which satisfies the predicate, the new future will also hold that value.
    *  Otherwise, the resulting future will fail with a `NoSuchElementException`.
    *
    *  If the current future fails, then the resulting future also fails.
    *
    *  Example:
    *  {{{
    *  val f = future { 5 }
    *  val g = f filter { _ % 2 == 1 }
    *  val h = f filter { _ % 2 == 0 }
    *  Await.result(g, Duration.Zero) // evaluates to 5
    *  Await.result(h, Duration.Zero) // throw a NoSuchElementException
    *  }}}
    */
  def filter(pred: T => Boolean): OFuture[T] = {
    val p = OPromise[T]()

    onComplete {
      case f: Failure[_] => p complete f.asInstanceOf[Failure[T]]
      case Success(v) =>
        try {
          if (pred(v)) p success v
          else p failure new NoSuchElementException("Future.filter predicate is not satisfied")
        } catch {
          case NonFatal(t) => p failure t
        }
    }

    p.future
  }

  /** Used by for-comprehensions.
    */
  final def withFilter(p: T => Boolean): OFuture[T] = filter(p)
  // final def withFilter(p: T => Boolean) = new FutureWithFilter[T](this, p)

  // final class FutureWithFilter[+S](self: Future[S], p: S => Boolean) {
  //   def foreach(f: S => Unit): Unit = self filter p foreach f
  //   def map[R](f: S => R) = self filter p map f
  //   def flatMap[R](f: S => Future[R]) = self filter p flatMap f
  //   def withFilter(q: S => Boolean): FutureWithFilter[S] = new FutureWithFilter[S](self, x => p(x) && q(x))
  // }

  /** Creates a new future by mapping the value of the current future, if the given partial function is defined at that value.
    *
    *  If the current future contains a value for which the partial function is defined, the new future will also hold that value.
    *  Otherwise, the resulting future will fail with a `NoSuchElementException`.
    *
    *  If the current future fails, then the resulting future also fails.
    *
    *  Example:
    *  {{{
    *  val f = future { -5 }
    *  val g = f collect {
    *    case x if x < 0 => -x
    *  }
    *  val h = f collect {
    *    case x if x > 0 => x * 2
    *  }
    *  Await.result(g, Duration.Zero) // evaluates to 5
    *  Await.result(h, Duration.Zero) // throw a NoSuchElementException
    *  }}}
    */
  def collect[S](pf: PartialFunction[T, S]): OFuture[S] = {
    val p = OPromise[S]()

    onComplete {
      case f: Failure[_] => p complete f.asInstanceOf[Failure[S]]
      case Success(v) =>
        try {
          if (pf.isDefinedAt(v)) p success pf(v)
          else p failure new NoSuchElementException("Future.collect partial function is not defined at: " + v)
        } catch {
          case NonFatal(t) => p failure t
        }
    }

    p.future
  }

  /** Creates a new future that will handle any matching throwable that this
    *  future might contain. If there is no match, or if this future contains
    *  a valid result then the new future will contain the same.
    *
    *  Example:
    *
    *  {{{
    *  future (6 / 0) recover { case e: ArithmeticException => 0 } // result: 0
    *  future (6 / 0) recover { case e: NotFoundException   => 0 } // result: exception
    *  future (6 / 2) recover { case e: ArithmeticException => 0 } // result: 3
    *  }}}
    */
  def recover[U >: T](pf: PartialFunction[Throwable, U]): OFuture[U] = {
    val p = OPromise[U]()

    onComplete { case tr => p.complete(tr recover pf) }

    p.future
  }

  /** Creates a new future that will handle any matching throwable that this
    *  future might contain by assigning it a value of another future.
    *
    *  If there is no match, or if this future contains
    *  a valid result then the new future will contain the same result.
    *
    *  Example:
    *
    *  {{{
    *  val f = future { Int.MaxValue }
    *  future (6 / 0) recoverWith { case e: ArithmeticException => f } // result: Int.MaxValue
    *  }}}
    */
  def recoverWith[U >: T](pf: PartialFunction[Throwable, OFuture[U]]): OFuture[U] = {
    val p = OPromise[U]()

    onComplete {
      case Failure(t) if pf isDefinedAt t =>
        try {
          p completeWith pf(t)
        } catch {
          case NonFatal(t0) => p failure t0
        }
      case otherwise => p complete otherwise
    }

    p.future
  }

  /** Zips the values of `this` and `that` future, and creates
    *  a new future holding the tuple of their results.
    *
    *  If `this` future fails, the resulting future is failed
    *  with the throwable stored in `this`.
    *  Otherwise, if `that` future fails, the resulting future is failed
    *  with the throwable stored in `that`.
    */
  def zip[U](that: OFuture[U]): OFuture[(T, U)] = {
    val p = OPromise[(T, U)]()

    this onComplete {
      case f: Failure[_] => p complete f.asInstanceOf[Failure[(T, U)]]
      case Success(r) =>
        that onSuccess {
          case r2 => p success ((r, r2))
        }
        that onFailure {
          case f => p failure f
        }
    }

    p.future
  }

  /** Creates a new future which holds the result of this future if it was completed successfully, or, if not,
    *  the result of the `that` future if `that` is completed successfully.
    *  If both futures are failed, the resulting future holds the throwable object of the first future.
    *
    *  Using this method will not cause concurrent programs to become nondeterministic.
    *
    *  Example:
    *  {{{
    *  val f = future { sys.error("failed") }
    *  val g = future { 5 }
    *  val h = f fallbackTo g
    *  Await.result(h, Duration.Zero) // evaluates to 5
    *  }}}
    */
  def fallbackTo[U >: T](that: OFuture[U]): OFuture[U] = {
    val p = OPromise[U]()
    onComplete {
      case s @ Success(_) => p complete s
      case _ => p completeWith that
    }
    p.future
  }

  /** Creates a new `Future[S]` which is completed with this `Future`'s result if
    *  that conforms to `S`'s erased type or a `ClassCastException` otherwise.
    */
  def mapTo[S](implicit tag: ClassTag[S]): OFuture[S] = {
    def boxedType(c: Class[_]): Class[_] = {
      if (c.isPrimitive) OFuture.toBoxed(c) else c
    }

    val p = OPromise[S]()

    onComplete {
      case f: Failure[_] => p complete f.asInstanceOf[Failure[S]]
      case Success(t) =>
        p complete (try {
          Success(boxedType(tag.runtimeClass).cast(t).asInstanceOf[S])
        } catch {
          case e: ClassCastException => Failure(e)
        })
    }

    p.future
  }

  /** Applies the side-effecting function to the result of this future, and returns
    *  a new future with the result of this future.
    *
    *  This method allows one to enforce that the callbacks are executed in a
    *  specified order.
    *
    *  Note that if one of the chained `andThen` callbacks throws
    *  an exception, that exception is not propagated to the subsequent `andThen`
    *  callbacks. Instead, the subsequent `andThen` callbacks are given the original
    *  value of this future.
    *
    *  The following example prints out `5`:
    *
    *  {{{
    *  val f = future { 5 }
    *  f andThen {
    *    case r => sys.error("runtime exception")
    *  } andThen {
    *    case Failure(t) => println(t)
    *    case Success(v) => println(v)
    *  }
    *  }}}
    */
  def andThen[U](pf: PartialFunction[Try[T], U]): OFuture[T] = {
    val p = OPromise[T]()

    onComplete {
      case r => try if (pf isDefinedAt r) pf(r) finally p complete r
    }

    p.future
  }

  /**
   * Converts this orchestration future to a scala.concurrent.Future.
   * @return A scala.concurrent.Future representing this future.
   */
  def toFuture: scala.concurrent.Future[T] = {
    import scala.concurrent.{Promise => CPromise}
    val cPromise = CPromise[T]()
    onComplete {
      case Success(v) => cPromise success v
      case Failure(t) => cPromise failure t
    }
    cPromise.future
  }

}

/** Future companion object.
  *
  *  @define nonDeterministic
  *  Note: using this method yields nondeterministic dataflow programs.
  */
@deprecated("The Orchestration module is deprecated. Please use Pekko streams for safer orchestration instead.",
since = "0.15.0")
object OFuture {

  private[orchestration] val toBoxed = Map[Class[_], Class[_]](
    classOf[Boolean] -> classOf[java.lang.Boolean],
    classOf[Byte]    -> classOf[java.lang.Byte],
    classOf[Char]    -> classOf[java.lang.Character],
    classOf[Short]   -> classOf[java.lang.Short],
    classOf[Int]     -> classOf[java.lang.Integer],
    classOf[Long]    -> classOf[java.lang.Long],
    classOf[Float]   -> classOf[java.lang.Float],
    classOf[Double]  -> classOf[java.lang.Double],
    classOf[Unit]    -> classOf[scala.runtime.BoxedUnit]
  )

  /** Creates an already completed Future with the specified exception.
    *
    *  @tparam T       the type of the value in the future
    *  @return         the newly created `Future` object
    */
  def failed[T](exception: Throwable): OFuture[T] = OPromise.failed(exception).future

  /** Creates an already completed Future with the specified result.
    *
    *  @tparam T       the type of the value in the future
    *  @return         the newly created `Future` object
    */
  def successful[T](result: T): OFuture[T] = OPromise.successful(result).future

  /** Simple version of `Futures.traverse`. Transforms a `IterableOnce[Future[A]]` into a `Future[IterableOnce[A]]`.
    *  Useful for reducing many `Future`s into a single `Future`.
    */
  def sequence[A, M[X] <: IterableOnce[X]](in: M[OFuture[A]])
                                          (implicit bf: BuildFrom[M[OFuture[A]], A, M[A]]): OFuture[M[A]] = {
    in.iterator.foldLeft(OPromise.successful(bf.newBuilder(in)).future) {
      (fr, fa) => for (r <- fr; a <- fa.asInstanceOf[OFuture[A]]) yield r += a
    } map (_.result())
  }

  /** Returns a `Future` to the result of the first future in the list that is completed.
    */
  def firstCompletedOf[T](futures: IterableOnce[OFuture[T]]): OFuture[T] = {
    val p = OPromise[T]()

    val completeFirst: Try[T] => Unit = p tryComplete _
    futures.iterator.foreach(_ onComplete completeFirst)

    p.future
  }

  /** Returns a `Future` that will hold the optional result of the first `Future` with a result that matches the predicate.
    */
  def find[T](futurestravonce: IterableOnce[OFuture[T]])(predicate: T => Boolean): OFuture[Option[T]] = {
    val futures = futurestravonce.iterator.to(ArrayBuffer)
    if (futures.isEmpty) OPromise.successful[Option[T]](None).future
    else {
      val result = OPromise[Option[T]]()
      var ref = futures.size
      val search: Try[T] => Unit = v => try {
        v match {
          case Success(r) => if (predicate(r)) result tryComplete Success(Some(r))
          case _ =>
        }
      } finally {
        ref -= 1
        if (ref == 0) {
          result tryComplete Success(None)
        }
      }

      futures.foreach(_ onComplete search)

      result.future
    }
  }

  /** A non-blocking fold over the specified futures, with the start value of the given zero.
    *  The fold is performed on the thread where the last future is completed,
    *  the result will be the first failure of any of the futures, or any failure in the actual fold,
    *  or the result of the fold.
    *
    *  Example:
    *  {{{
    *    val result = Await.result(Future.fold(futures)(0)(_ + _), 5 seconds)
    *  }}}
    */
  def fold[T, R](futures: IterableOnce[OFuture[T]])(zero: R)(foldFun: (R, T) => R): OFuture[R] = {
    if (futures.iterator.isEmpty) OPromise.successful(zero).future
    else sequence(futures)(ArrayBuffer).map(_.iterator.foldLeft(zero)(foldFun))
  }

  /** Initiates a fold over the supplied futures where the fold-zero is the result value of the `Future` that's completed first.
    *
    *  Example:
    *  {{{
    *    val result = Await.result(Future.reduce(futures)(_ + _), 5 seconds)
    *  }}}
    */
  def reduce[T, R >: T](futures: IterableOnce[OFuture[T]])(op: (R, T) => R): OFuture[R] = {
    if (futures.iterator.isEmpty)
      OPromise[R]().failure(new NoSuchElementException("reduce attempted on empty collection")).future
    else
      sequence(futures)(ArrayBuffer).map(a => a.iterator.reduceLeft(op))
  }

  /** Transforms a `IterableOnce[A]` into a `Future[IterableOnce[B]]` using the provided function `A => Future[B]`.
    *  This is useful for performing a parallel map. For example, to apply a function to all items of a list
    *  in parallel:
    *
    *  {{{
    *    val myFutureList = Future.traverse(myList)(x => Future(myFunc(x)))
    *  }}}
    */
  def traverse[A, B, M[_] <: IterableOnce[_]](in: M[A])(fn: A => OFuture[B])
                                             (implicit bf: BuildFrom[M[A], B, M[B]]): OFuture[M[B]] =
    in.foldLeft(OPromise.successful(bf.newBuilder(in)).future) { (fr, a) =>
      val fb = fn(a.asInstanceOf[A])
      for (r <- fr; b <- fb) yield r += b
    }.map(_.result())
}

/** A marker indicating that a `java.lang.Runnable` provided to `scala.concurrent.ExecutionContext`
  * wraps a callback provided to `Future.onComplete`.
  * All callbacks provided to a `Future` end up going through `onComplete`, so this allows an
  * `ExecutionContext` to special-case callbacks that were executed by `Future` if desired.
  */
@deprecated("The Orchestration module is deprecated. Please use Pekko streams for safer orchestration instead.",
  since = "0.15.0")
trait OnCompleteRunnable {
  self: Runnable =>
}
