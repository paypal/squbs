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

import scala.util.{Failure, Success, Try}

/** Promise is an object which can be completed with a value or failed
  *  with an exception.
  *
  *  @define promiseCompletion
  *  If the promise has already been fulfilled, failed or has timed out,
  *  calling this method will throw an IllegalStateException.
  *
  *  @define allowedThrowables
  *  If the throwable used to fail this promise is an error, a control exception
  *  or an interrupted exception, it will be wrapped as a cause within an
  *  `ExecutionException` which will fail the promise.
  *
  *  @define nonDeterministic
  *  Note: Using this method may result in non-deterministic concurrent programs.
  */
@deprecated("The Orchestration module is deprecated. Please use Pekko streams for safer orchestration instead.",
  since = "0.15.0")
trait OPromise[T] {

  /** Future containing the value of this promise.
    */
  def future: OFuture[T]

  /** Returns whether the promise has already been completed with
    *  a value or an exception.
    *
    *  $nonDeterministic
    *
    *  @return    `true` if the promise is already completed, `false` otherwise
    */
  def isCompleted: Boolean

  /** Completes the promise with either an exception or a value.
    *
    *  @param result     Either the value or the exception to complete the promise with.
    *
    *  $promiseCompletion
    */
  def complete(result: Try[T]): this.type =
    if (tryComplete(result)) this else throw new IllegalStateException("Promise already completed.")

  /** Tries to complete the promise with either a value or the exception.
    *
    *  $nonDeterministic
    *
    *  @return    If the promise has already been completed returns `false`, or `true` otherwise.
    */
  def tryComplete(result: Try[T]): Boolean

  /** Completes this promise with the specified future, once that future is completed.
    *
    *  @return   This promise
    */
  final def completeWith(other: OFuture[T]): this.type = {
    other onComplete { this complete _ }
    this
  }

  /** Attempts to complete this promise with the specified future, once that future is completed.
    *
    *  @return   This promise
    */
  final def tryCompleteWith(other: OFuture[T]): this.type = {
    other onComplete { this tryComplete _ }
    this
  }

  /** Completes the promise with a value.
    *
    *  @param v    The value to complete the promise with.
    *
    *  $promiseCompletion
    */
  def success(v: T): this.type = complete(Success(v))

  /** Tries to complete the promise with a value.
    *
    *  $nonDeterministic
    *
    *  @return    If the promise has already been completed returns `false`, or `true` otherwise.
    */
  def trySuccess(value: T): Boolean = tryComplete(Success(value))

  /** Completes the promise with an exception.
    *
    *  @param t        The throwable to complete the promise with.
    *
    *  $allowedThrowables
    *
    *  $promiseCompletion
    */
  def failure(t: Throwable): this.type = complete(Failure(t))

  /** Tries to complete the promise with an exception.
    *
    *  $nonDeterministic
    *
    *  @return    If the promise has already been completed returns `false`, or `true` otherwise.
    */
  def tryFailure(t: Throwable): Boolean = tryComplete(Failure(t))
}

object OPromise {

  /** Creates a promise object which can be completed with a value.
    *
    *  @tparam T       the type of the value in the promise
    *  @return         the newly created `Promise` object
    */
  def apply[T](): OPromise[T] = new impl.OPromise.DefaultOPromise[T]()

  /** Creates an already completed Promise with the specified exception.
    *
    *  @tparam T       the type of the value in the promise
    *  @return         the newly created `Promise` object
    */
  def failed[T](exception: Throwable): OPromise[T] = new impl.OPromise.KeptOPromise[T](Failure(exception))

  /** Creates an already completed Promise with the specified result.
    *
    *  @tparam T       the type of the value in the promise
    *  @return         the newly created `Promise` object
    */
  def successful[T](result: T): OPromise[T] = new impl.OPromise.KeptOPromise[T](Success(result))
}
