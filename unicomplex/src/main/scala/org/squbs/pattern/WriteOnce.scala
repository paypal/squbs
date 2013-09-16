/*
 * Copyright (c) 2013 eBay, Inc.
 * All rights reserved.
 *
 * Contributors:
 * asucharitakul
 */
package org.squbs.pattern

import scala.util.{Try, Success, Failure}
import scala.util.control.NonFatal
import scala.reflect.ClassTag
import scala.collection.generic.CanBuildFrom
import scala.annotation.tailrec

object WriteOnce {
  def apply[T]: WriteOnce[T] = new WriteOnceImpl[T]

  /** Creates an already completed WriteOnce with the specified result.
    *
    *  @tparam T       the type of the value in the WriteOnce
    *  @return         the newly created `WriteOnce` object
    */
  def successful[T](result: T): WriteOnce[T] = null


  /** Simple version of `WriteOnce.traverse`. Transforms a `TraversableOnce[WriteOnce[A]]` into a `WriteOnce[TraversableOnce[A]]`.
    *  Useful for reducing many `WriteOnce`s into a single `WriteOnce`.
    */
  def sequence[A, M[_] <: TraversableOnce[_]](in: M[WriteOnce[A]])(implicit cbf: CanBuildFrom[M[WriteOnce[A]], A, M[A]]): WriteOnce[M[A]] = {
    in.foldLeft(WriteOnce.successful(cbf(in))) {
      (fr, fa) => for (r <- fr; a <- fa.asInstanceOf[WriteOnce[A]]) yield r += a
    } map (_.result())
  }

  /** Returns a `WriteOnce` to the result of the first WriteOnce in the list that is completed.
    */
  def firstCompletedOf[T](writeOnces: TraversableOnce[WriteOnce[T]]): WriteOnce[T] = {
    val p = WriteOnce[T]

    val completeFirst: Try[T] => Unit = { p.tryComplete(_) }
    writeOnces.foreach(_ onComplete completeFirst)

    p
  }

  /** Returns a `WriteOnce` that will hold the optional result of the first `WriteOnce` with a result that matches the predicate.
    */
  def find[T](writeOncesTravOnce: TraversableOnce[WriteOnce[T]])(predicate: T => Boolean): WriteOnce[Option[T]] = {
    val writeOnces = writeOncesTravOnce.toBuffer
    if (writeOnces.isEmpty) WriteOnce.successful[Option[T]](None)
    else {
      val result = WriteOnce[Option[T]]
      var ref = writeOnces.size
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

      writeOnces.foreach(_ onComplete search)

      result
    }
  }

  /** A non-blocking fold over the specified writeOnces, with the start value of the given zero.
    *  The fold is performed on the thread where the last WriteOnce is completed,
    *  the result will be the first failure of any of the writeOnces, or any failure in the actual fold,
    *  or the result of the fold.
    *
    *  Example:
    *  {{{
    *    val result = Await.result(WriteOnce.fold(writeOnces)(0)(_ + _), 5 seconds)
    *  }}}
    */
  def fold[T, R](writeOnces: TraversableOnce[WriteOnce[T]])(zero: R)(foldFun: (R, T) => R): WriteOnce[R] = {
    if (writeOnces.isEmpty) WriteOnce.successful(zero)
    else sequence(writeOnces).map(_.foldLeft(zero)(foldFun))
  }

  /** Initiates a fold over the supplied writeOnces where the fold-zero is the result value of the `WriteOnce` that's completed first.
    *
    *  Example:
    *  {{{
    *    val result = Await.result(WriteOnce.reduce(writeOnces)(_ + _), 5 seconds)
    *  }}}
    */
  def reduce[T, R >: T](writeOnces: TraversableOnce[WriteOnce[T]])(op: (R, T) => R): WriteOnce[R] = {
    if (writeOnces.isEmpty) WriteOnce[R].failure(new NoSuchElementException("reduce attempted on empty collection"))
    else sequence(writeOnces).map(_ reduceLeft op)
  }

  /** Transforms a `TraversableOnce[A]` into a `WriteOnce[TraversableOnce[B]]` using the provided function `A => WriteOnce[B]`.
    *  This is useful for performing a parallel map. For example, to apply a function to all items of a list
    *  in parallel:
    *
    *  {{{
    *    val myFutureList = WriteOnce.traverse(myList)(x => WriteOnce(myFunc(x)))
    *  }}}
    */
  def traverse[A, B, M[_] <: TraversableOnce[_]](in: M[A])(fn: A => WriteOnce[B])(implicit cbf: CanBuildFrom[M[A], B, M[B]]): WriteOnce[M[B]] =
    in.foldLeft(WriteOnce.successful(cbf(in))) { (fr, a) =>
      val fb = fn(a.asInstanceOf[A])
      for (r <- fr; b <- fb) yield r += b
    }.map(_.result())

  private[pattern] val toBoxed = Map[Class[_], Class[_]](
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

  private[pattern] def resolveTry[T](source: Try[T]): Try[T] = source match {
    case Failure(t) => resolver(t)
    case _          => source
  }

  private def resolver[T](throwable: Throwable): Try[T] = throwable match {
    case t: scala.runtime.NonLocalReturnControl[_] => Success(t.value.asInstanceOf[T])
    case t                                         => Failure(t)
  }
}

/* Precondition: `executor` is prepared, i.e., `executor` has been returned from invocation of `prepare` on some other `ExecutionContext`.
 */
private class CallbackRunnable[T](val onComplete: Try[T] => Any, errorReporter: Throwable => Unit) {
  // must be filled in before running it
  var value: Try[T] = null

  def executeWithValue(v: Try[T]): Unit = {
    require(value eq null) // can't complete it twice
    require(v ne null)
    value = v
    try onComplete(value) catch { case NonFatal(t) => errorReporter(t) }
  }
}


trait WriteOnce[T] {

  def apply() = {
    if (value.isEmpty)
      throw new IllegalStateException("Uninitialized value")
    else value.get
  }

  /**
   * Completes the WriteOnce with a final value and not an exception. Shortcut for success.
   * @param finalValue The final value of this WriteOnce.
   */
  def :=(finalValue: T) {
    success(finalValue)
  }

  /** Completes the WriteOnce with either an exception or a value.
    *
    *  @param result     Either the value or the exception to complete the WriteOnce with.
    *
    *  $WriteOnceCompletion
    */
  def complete(result: Try[T]): this.type =
    if (tryComplete(result)) this else throw new IllegalStateException("WriteOnce already completed.")

  /** Tries to complete the WriteOnce with either a value or the exception.
    *
    *  $nonDeterministic
    *
    *  @return    If the WriteOnce has already been completed returns `false`, or `true` otherwise.
    */
  def tryComplete(result: Try[T]): Boolean

  /** Completes this WriteOnce with the specified WriteOnce, once that WriteOnce is completed.
    *
    *  @return   This WriteOnce
    */
  final def completeWith(other: WriteOnce[T]): this.type = {
    other onComplete { this.complete }
    this
  }

  /** Attempts to complete this WriteOnce with the specified WriteOnce, once that WriteOnce is completed.
    *
    *  @return   This WriteOnce
    */
  final def tryCompleteWith(other: WriteOnce[T]): this.type = {
    other onComplete { this.tryComplete }
    this
  }

  /** Completes the WriteOnce with a value.
    *
    *  @param v    The value to complete the WriteOnce with.
    *
    *  $WriteOnceCompletion
    */
  def success(v: T): this.type = complete(Success(v))

  /** Tries to complete the WriteOnce with a value.
    *
    *  $nonDeterministic
    *
    *  @return    If the WriteOnce has already been completed returns `false`, or `true` otherwise.
    */
  def trySuccess(value: T): Boolean = tryComplete(Success(value))

  /** Completes the WriteOnce with an exception.
    *
    *  @param t        The throwable to complete the WriteOnce with.
    *
    *  $allowedThrowables
    *
    *  $WriteOnceCompletion
    */
  def failure(t: Throwable): this.type = complete(Failure(t))

  /** Tries to complete the WriteOnce with an exception.
    *
    *  $nonDeterministic
    *
    *  @return    If the WriteOnce has already been completed returns `false`, or `true` otherwise.
    */
  def tryFailure(t: Throwable): Boolean = tryComplete(Failure(t))


  /* Callbacks */

  /** When this WriteOnce is completed successfully (i.e. with a value),
    *  apply the provided partial function to the value if the partial function
    *  is defined at that value.
    *
    *  If the WriteOnce has already been completed with a value,
    *  this will either be applied immediately or be scheduled asynchronously.
    *
    *  $multipleCallbacks
    *  $callbackInContext
    */
  def onSuccess[U](pf: PartialFunction[T, U]): Unit = onComplete {
    case Success(v) if pf isDefinedAt v => pf(v)
    case _ =>
  }

  /** When this WriteOnce is completed with a failure (i.e. with a throwable),
    *  apply the provided callback to the throwable.
    *
    *  $caughtThrowables
    *
    *  If the WriteOnce has already been completed with a failure,
    *  this will either be applied immediately or be scheduled asynchronously.
    *
    *  Will not be called in case that the WriteOnce is completed with a value.
    *
    *  $multipleCallbacks
    *  $callbackInContext
    */
  def onFailure[U](callback: PartialFunction[Throwable, U]): Unit = onComplete {
    case Failure(t) if NonFatal(t) && callback.isDefinedAt(t) => callback(t)
    case _ =>
  }

  /** When this WriteOnce is completed, either through an exception, or a value,
    *  apply the provided function.
    *
    *  If the WriteOnce has already been completed,
    *  this will either be applied immediately or be scheduled asynchronously.
    *
    *  $multipleCallbacks
    *  $callbackInContext
    */
  def onComplete[U](func: Try[T] => U): Unit


  /* Miscellaneous */

  /** Returns whether the WriteOnce has already been completed with
    *  a value or an exception.
    *
    *  $nonDeterministic
    *
    *  @return    `true` if the WriteOnce is already completed, `false` otherwise
    */
  def isCompleted: Boolean

  /** The value of this `WriteOnce`.
    *
    *  If the WriteOnce is not completed the returned value will be `None`.
    *  If the WriteOnce is completed the value will be `Some(Success(t))`
    *  if it contains a valid result, or `Some(Failure(error))` if it contains
    *  an exception.
    */
  def value: Option[Try[T]]


  /* Projections */

  /** Returns a failed projection of this WriteOnce.
    *
    *  The failed projection is a WriteOnce holding a value of type `Throwable`.
    *
    *  It is completed with a value which is the throwable of the original WriteOnce
    *  in case the original WriteOnce is failed.
    *
    *  It is failed with a `NoSuchElementException` if the original WriteOnce is completed successfully.
    *
    *  Blocking on this WriteOnce returns a value if the original WriteOnce is completed with an exception
    *  and throws a corresponding exception if the original WriteOnce fails.
    */
  def failed: WriteOnce[Throwable] = {
    val p = WriteOnce[Throwable]

    onComplete {
      case Failure(t) => p success t
      case Success(v) => p failure new NoSuchElementException("WriteOnce.failed not completed with a throwable.")
    }
    p
  }


  /* Monadic operations */

  /** Asynchronously processes the value in the WriteOnce once the value becomes available.
    *
    *  Will not be called if the WriteOnce fails.
    */
  def foreach[U](f: T => U): Unit = onComplete {
    case Success(r) => f(r)
    case _  => // do nothing
  }

  /** Creates a new WriteOnce by applying the 's' function to the successful result of
    *  this WriteOnce, or the 'f' function to the failed result. If there is any non-fatal
    *  exception thrown when 's' or 'f' is applied, that exception will be propagated
    *  to the resulting WriteOnce.
    *
    *  @param  s  function that transforms a successful result of the receiver into a
    *             successful result of the returned WriteOnce
    *  @param  f  function that transforms a failure of the receiver into a failure of
    *             the returned WriteOnce
    *  @return    a WriteOnce that will be completed with the transformed value
    */
  def transform[S](s: T => S, f: Throwable => Throwable): WriteOnce[S] = {
    val p = WriteOnce[S]

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
    p
  }

  /** Creates a new WriteOnce by applying a function to the successful result of
    *  this WriteOnce. If this WriteOnce is completed with an exception then the new
    *  WriteOnce will also contain this exception.
    *
    *  $forComprehensionExamples
    */
  def map[S](f: T => S): WriteOnce[S] = { // transform(f, identity)
  val p = WriteOnce[S]

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
    p
  }

  /** Creates a new WriteOnce by applying a function to the successful result of
    *  this WriteOnce, and returns the result of the function as the new WriteOnce.
    *  If this WriteOnce is completed with an exception then the new WriteOnce will
    *  also contain this exception.
    *
    *  $forComprehensionExamples
    */
  def flatMap[S](f: T => WriteOnce[S]): WriteOnce[S] = {
    val p = WriteOnce[S]

    onComplete {
      case f: Failure[_] => p complete f.asInstanceOf[Failure[S]]
      case Success(v) =>
        try {
          f(v).onComplete({
            case f: Failure[_] => p complete f.asInstanceOf[Failure[S]]
            case Success(v) => p success v
          })
        } catch {
          case NonFatal(t) => p failure t
        }
    }
    p
  }

  /** Creates a new WriteOnce by filtering the value of the current WriteOnce with a predicate.
    *
    *  If the current WriteOnce contains a value which satisfies the predicate, the new WriteOnce will also hold that value.
    *  Otherwise, the resulting WriteOnce will fail with a `NoSuchElementException`.
    *
    *  If the current WriteOnce fails, then the resulting WriteOnce also fails.
    *
    *  Example:
    *  {{{
    *  val f = WriteOnce { 5 }
    *  val g = f filter { _ % 2 == 1 }
    *  val h = f filter { _ % 2 == 0 }
    *  Await.result(g, Duration.Zero) // evaluates to 5
    *  Await.result(h, Duration.Zero) // throw a NoSuchElementException
    *  }}}
    */
  def filter(pred: T => Boolean): WriteOnce[T] = {
    val p = WriteOnce[T]

    onComplete {
      case f: Failure[_] => p complete f.asInstanceOf[Failure[T]]
      case Success(v) =>
        try {
          if (pred(v)) p success v
          else p failure new NoSuchElementException("WriteOnce.filter predicate is not satisfied")
        } catch {
          case NonFatal(t) => p failure t
        }
    }
    p
  }

  /** Used by for-comprehensions.
    */
  final def withFilter(p: T => Boolean): WriteOnce[T] = filter(p)

  /** Creates a new WriteOnce by mapping the value of the current WriteOnce, if the given partial function is defined at that value.
    *
    *  If the current WriteOnce contains a value for which the partial function is defined, the new WriteOnce will also hold that value.
    *  Otherwise, the resulting WriteOnce will fail with a `NoSuchElementException`.
    *
    *  If the current WriteOnce fails, then the resulting WriteOnce also fails.
    *
    *  Example:
    *  {{{
    *  val f = WriteOnce { -5 }
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
  def collect[S](pf: PartialFunction[T, S]): WriteOnce[S] = {
    val p = WriteOnce[S]

    onComplete {
      case f: Failure[_] => p complete f.asInstanceOf[Failure[S]]
      case Success(v) =>
        try {
          if (pf.isDefinedAt(v)) p success pf(v)
          else p failure new NoSuchElementException("WriteOnce.collect partial function is not defined at: " + v)
        } catch {
          case NonFatal(t) => p failure t
        }
    }

    p
  }

  /** Creates a new WriteOnce that will handle any matching throwable that this
    *  WriteOnce might contain. If there is no match, or if this WriteOnce contains
    *  a valid result then the new WriteOnce will contain the same.
    *
    *  Example:
    *
    *  {{{
    *  WriteOnce (6 / 0) recover { case e: ArithmeticException => 0 } // result: 0
    *  WriteOnce (6 / 0) recover { case e: NotFoundException   => 0 } // result: exception
    *  WriteOnce (6 / 2) recover { case e: ArithmeticException => 0 } // result: 3
    *  }}}
    */
  def recover[U >: T](pf: PartialFunction[Throwable, U]): WriteOnce[U] = {
    val p = WriteOnce[U]

    onComplete { case tr => p.complete(tr recover pf) }

    p
  }

  /** Creates a new WriteOnce that will handle any matching throwable that this
    *  WriteOnce might contain by assigning it a value of another WriteOnce.
    *
    *  If there is no match, or if this WriteOnce contains
    *  a valid result then the new WriteOnce will contain the same result.
    *
    *  Example:
    *
    *  {{{
    *  val f = WriteOnce { Int.MaxValue }
    *  WriteOnce (6 / 0) recoverWith { case e: ArithmeticException => f } // result: Int.MaxValue
    *  }}}
    */
  def recoverWith[U >: T](pf: PartialFunction[Throwable, WriteOnce[U]]): WriteOnce[U] = {
    val p = WriteOnce[U]

    onComplete {
      case Failure(t) if pf isDefinedAt t =>
        try {
          p completeWith pf(t)
        } catch {
          case NonFatal(t) => p failure t
        }
      case otherwise => p complete otherwise
    }

    p
  }

  /** Zips the values of `this` and `that` WriteOnce, and creates
    *  a new WriteOnce holding the tuple of their results.
    *
    *  If `this` WriteOnce fails, the resulting WriteOnce is failed
    *  with the throwable stored in `this`.
    *  Otherwise, if `that` WriteOnce fails, the resulting WriteOnce is failed
    *  with the throwable stored in `that`.
    */
  def zip[U](that: WriteOnce[U]): WriteOnce[(T, U)] = {
    val p = WriteOnce[(T, U)]

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

    p
  }

  /** Creates a new WriteOnce which holds the result of this WriteOnce if it was completed successfully, or, if not,
    *  the result of the `that` WriteOnce if `that` is completed successfully.
    *  If both writeOnces are failed, the resulting WriteOnce holds the throwable object of the first WriteOnce.
    *
    *  Using this method will not cause concurrent programs to become nondeterministic.
    *
    *  Example:
    *  {{{
    *  val f = WriteOnce { sys.error("failed") }
    *  val g = WriteOnce { 5 }
    *  val h = f fallbackTo g
    *  Await.result(h, Duration.Zero) // evaluates to 5
    *  }}}
    */
  def fallbackTo[U >: T](that: WriteOnce[U]): WriteOnce[U] = {
    val p = WriteOnce[U]
    onComplete {
      case s @ Success(_) => p complete s
      case _ => p completeWith that
    }
    p
  }

  /** Creates a new `WriteOnce[S]` which is completed with this `WriteOnce`'s result if
    *  that conforms to `S`'s erased type or a `ClassCastException` otherwise.
    */
  def mapTo[S](implicit tag: ClassTag[S]): WriteOnce[S] = {
    def boxedType(c: Class[_]): Class[_] = {
      if (c.isPrimitive) WriteOnce.toBoxed(c) else c
    }

    val p = WriteOnce[S]

    onComplete {
      case f: Failure[_] => p complete f.asInstanceOf[Failure[S]]
      case Success(t) =>
        p complete (try {
          Success(boxedType(tag.runtimeClass).cast(t).asInstanceOf[S])
        } catch {
          case e: ClassCastException => Failure(e)
        })
    }

    p
  }

  /** Applies the side-effecting function to the result of this WriteOnce, and returns
    *  a new WriteOnce with the result of this WriteOnce.
    *
    *  This method allows one to enforce that the callbacks are executed in a
    *  specified order.
    *
    *  Note that if one of the chained `andThen` callbacks throws
    *  an exception, that exception is not propagated to the subsequent `andThen`
    *  callbacks. Instead, the subsequent `andThen` callbacks are given the original
    *  value of this WriteOnce.
    *
    *  The following example prints out `5`:
    *
    *  {{{
    *  val f = WriteOnce { 5 }
    *  f andThen {
    *    case r => sys.error("runtime exception")
    *  } andThen {
    *    case Failure(t) => println(t)
    *    case Success(v) => println(v)
    *  }
    *  }}}
    */
  def andThen[U](pf: PartialFunction[Try[T], U]): WriteOnce[T] = {
    val p = WriteOnce[T]

    onComplete {
      case r => try if (pf isDefinedAt r) pf(r) finally p complete r
    }

    p
  }
}

class WriteOnceImpl[T] extends WriteOnce[T] {
  /** Tries to complete the WriteOnce with either a value or the exception.
    *
    * $nonDeterministic
    *
    * @return    If the WriteOnce has already been completed returns `false`, or `true` otherwise.
    */
  def tryComplete(result: Try[T]): Boolean = {

    import WriteOnce._

    val resolved = resolveTry(result)

    @tailrec
    def tryComplete(v: Try[T]): List[CallbackRunnable[T]] = {
      getState match {
        case raw: List[_] =>
          val cur = raw.asInstanceOf[List[CallbackRunnable[T]]]
          if (updateState(cur, v)) cur else tryComplete(v)
        case _ => null
      }
    }

    tryComplete(resolved) match {
      case null             => false
      case rs if rs.isEmpty => true
      case rs               => rs.foreach(r => r.executeWithValue(resolved)); true
    }
  }

  /** Returns whether the WriteOnce has already been completed with
    * a value or an exception.
    *
    * $nonDeterministic
    *
    * @return    `true` if the WriteOnce is already completed, `false` otherwise
    */
  override def isCompleted: Boolean = getState match { // Cheaper than boxing result into Option due to "def value"
    case _: Try[_] => true
    case _ => false
  }

  /** The value of this `WriteOnce`.
    *
    * If the WriteOnce is not completed the returned value will be `None`.
    * If the WriteOnce is completed the value will be `Some(Success(t))`
    * if it contains a valid result, or `Some(Failure(error))` if it contains
    * an exception.
    */
  def value: Option[Try[T]] = getState match {
    case c: Try[_] => Some(c.asInstanceOf[Try[T]])
    case _ => None
  }

  def onComplete[U](func: Try[T] => U): Unit = {
    val runnable = new CallbackRunnable[T](func, errorReporter)

    @tailrec //Tries to add the callback, if already completed, it dispatches the callback to be executed
    def dispatchOrAddCallback(): Unit =
      getState match {
        case r: Try[_]          => runnable.executeWithValue(r.asInstanceOf[Try[T]])
        case listeners: List[_] => if (updateState(listeners, runnable :: listeners)) () else dispatchOrAddCallback()
      }
    dispatchOrAddCallback()
  }

  var _ref: AnyRef = null

  def getState = _ref

  def updateState(oldState: AnyRef, newState: AnyRef) = {
    if (_ref == oldState) {
      _ref = newState
      true
    } else false
  }

  /** The default reporter simply prints the stack trace of the `Throwable` to System.err.
    */
  def defaultReporter: Throwable => Unit = (t: Throwable) => t.printStackTrace()

  var errorReporter = defaultReporter

  def setErrorReporter(reporter: Throwable => Unit) {
    errorReporter = reporter
  }
}