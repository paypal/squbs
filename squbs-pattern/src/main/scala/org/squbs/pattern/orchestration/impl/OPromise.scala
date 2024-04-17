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

package org.squbs.pattern.orchestration.impl

import org.squbs.pattern.orchestration.OFuture

import scala.annotation.tailrec
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

@deprecated("The Orchestration module is deprecated. Please use Pekko streams for safer orchestration instead.",
since = "0.15.0")
private[orchestration] trait OPromise[T] extends org.squbs.pattern.orchestration.OPromise[T]
    with OFuture[T] {
  def future: this.type = this

  /** The default reporter simply prints the stack trace of the `Throwable` to System.err.
    */
  def defaultReporter: Throwable => Unit = (t: Throwable) => t.printStackTrace()

  protected var errorReporter = defaultReporter

  def setErrorReporter(reporter: Throwable => Unit): Unit = {
    errorReporter = reporter
  }
}

/* Precondition: `executor` is prepared, i.e., `executor` has been returned from invocation of `prepare` on some other `ExecutionContext`.
 */
@deprecated("The Orchestration module is deprecated. Please use Pekko streams for safer orchestration instead.",
since = "0.15.0")
private class CallbackRunnable[T](val onComplete: Try[T] => Any, errorReporter: Throwable => Unit) {
  // must be filled in before running it
  var value: Try[T] = null
  var next: CallbackRunnable[T] = null

  def executeWithValue(v: Try[T]): Unit = {
    require(value eq null) // can't complete it twice
    require(v ne null)
    value = v
    try onComplete(value) catch { case NonFatal(t) => errorReporter(t) }
  }
}

private object CallbackRunnable {

  def executeWithValue[T](onComplete: Try[T] => Any, errorReporter: Throwable => Unit)(v: Try[T]): Unit = {
    require(v ne null)
    try onComplete(v) catch { case NonFatal(t) => errorReporter(t) }
  }
}

@deprecated("The Orchestration module is deprecated. Please use Pekko streams for safer orchestration instead.",
since = "0.15.0")
private class CallbackList[T] {
  val head = new CallbackRunnable[T](null, null) // Empty placeholder
  var tail = head

  def +=(node: CallbackRunnable[T]): Unit = {
    require(node ne null)
    tail.next = node
    tail = node
  }

  def isEmpty: Boolean = head.next == null

  def executeWithValue(v: Try[T]): Unit = {

    @tailrec
    def executeWithValue(node: CallbackRunnable[T]): Unit = {
      if (node != null) {
        node.executeWithValue(v)
        executeWithValue(node.next)
      }
    }

    executeWithValue(head.next)
  }
}

@deprecated("The Orchestration module is deprecated. Please use Pekko streams for safer orchestration instead.",
since = "0.15.0")
private[orchestration] object OPromise {

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
  class DefaultOPromise[T] extends AbstractOPromise with OPromise[T] { self =>
    updateState(null, new CallbackList[T]) // Start with empty CallbackList

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
        def tryComplete(v: Try[T]): CallbackList[T] = {
          getState match {
            case raw: CallbackList[_] =>
              val cur = raw.asInstanceOf[CallbackList[T]]
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
        case rs               => rs.executeWithValue(resolved); true
      }
    }

    def onComplete[U](func: Try[T] => U): Unit = {
      val runnable = new CallbackRunnable[T](func, errorReporter)

      def dispatchOrAddCallback(): Unit =
        getState match {
          case r: Try[_]          => runnable.executeWithValue(r.asInstanceOf[Try[T]])
          case listeners: CallbackList[_] => listeners.asInstanceOf[CallbackList[T]] += runnable
        }
      dispatchOrAddCallback()
    }
  }

  /** An already completed Future is given its result at creation.
    *
    *  Useful in Future-composition when a value to contribute is already available.
    */
  final class KeptOPromise[T](suppliedValue: Try[T]) extends OPromise[T] {

    val value = Some(resolveTry(suppliedValue))

    override def isCompleted: Boolean = true

    def tryComplete(value: Try[T]): Boolean = false

    def onComplete[U](func: Try[T] => U): Unit = {
      val completedAs = value.get
      CallbackRunnable.executeWithValue(func, errorReporter)(completedAs)
    }
  }
}

@deprecated("The Orchestration module is deprecated. Please use Pekko streams for safer orchestration instead.",
since = "0.15.0")
private[impl] abstract class AbstractOPromise {

  var _ref: AnyRef = null

  def getState = _ref

  def updateState(oldState: AnyRef, newState: AnyRef) = {
    if (_ref == oldState) {
      _ref = newState
      true
    } else false
  }
}

