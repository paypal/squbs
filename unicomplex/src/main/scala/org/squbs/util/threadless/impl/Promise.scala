/*
 * Copyright (c) 2013 eBay, Inc.
 * All rights reserved.
 *
 * Contributors:
 * asucharitakul
 */
package org.squbs.util.threadless.impl

import scala.annotation.tailrec
import scala.util.control.NonFatal
import scala.util.{ Try, Success, Failure }


private[threadless] trait Promise[T] extends org.squbs.util.threadless.Promise[T]
    with org.squbs.util.threadless.Future[T] {
  def future: this.type = this

  /** The default reporter simply prints the stack trace of the `Throwable` to System.err.
    */
  def defaultReporter: Throwable => Unit = (t: Throwable) => t.printStackTrace()

  protected var errorReporter = defaultReporter

  def setErrorReporter(reporter: Throwable => Unit) {
    errorReporter = reporter
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

private[threadless] object Promise {

  private def resolveTry[T](source: Try[T]): Try[T] = source match {
    case Failure(t) => resolver(t)
    case _          => source
  }

  private def resolver[T](throwable: Throwable): Try[T] = throwable match {
    case t: scala.runtime.NonLocalReturnControl[_] => Success(t.value.asInstanceOf[T])
    case t                                         => Failure(t)
  }

  /** Default promise implementation.
    */
  class DefaultPromise[T] extends AbstractPromise with Promise[T] { self =>
    updateState(null, Nil) // Start at "No callbacks"

    def value: Option[Try[T]] = getState match {
      case c: Try[_] => Some(c.asInstanceOf[Try[T]])
      case _ => None
    }

    override def isCompleted: Boolean = getState match { // Cheaper than boxing result into Option due to "def value"
      case _: Try[_] => true
      case _ => false
    }

    def tryComplete(value: Try[T]): Boolean = {
      val resolved = resolveTry(value)
      (try {
        @tailrec
        def tryComplete(v: Try[T]): List[CallbackRunnable[T]] = {
          getState match {
            case raw: List[_] =>
              val cur = raw.asInstanceOf[List[CallbackRunnable[T]]]
              if (updateState(cur, v)) cur else tryComplete(v)
            case _ => null
          }
        }
        tryComplete(resolved)
      } finally {
        synchronized { notifyAll() } //Notify any evil blockers
      }) match {
        case null             => false
        case rs if rs.isEmpty => true
        case rs               => rs.foreach(r => r.executeWithValue(resolved)); true
      }
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
  }

  /** An already completed Future is given its result at creation.
    *
    *  Useful in Future-composition when a value to contribute is already available.
    */
  final class KeptPromise[T](suppliedValue: Try[T]) extends Promise[T] {

    val value = Some(resolveTry(suppliedValue))

    override def isCompleted: Boolean = true

    def tryComplete(value: Try[T]): Boolean = false

    def onComplete[U](func: Try[T] => U): Unit = {
      val completedAs = value.get
      new CallbackRunnable(func, errorReporter).executeWithValue(completedAs)
    }
  }
}

private[impl] abstract class AbstractPromise {

  var _ref: AnyRef = null

  def getState = _ref

  def updateState(oldState: AnyRef, newState: AnyRef) = {
    if (_ref == oldState) {
      _ref = newState
      true
    } else false
  }
}

