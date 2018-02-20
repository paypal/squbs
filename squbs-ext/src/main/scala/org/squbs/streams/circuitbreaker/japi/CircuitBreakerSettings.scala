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
package org.squbs.streams.circuitbreaker.japi

import java.lang.{Boolean => JBoolean}
import java.util.Optional
import java.util.function.{Consumer, Function}

import org.squbs.streams.circuitbreaker.CircuitBreakerState

import scala.util.Try

/**
  * Java API
  *
  * @see [[org.squbs.streams.circuitbreaker.CircuitBreakerSettings]] for details
  */
case class CircuitBreakerSettings[In, Out, Context] private[japi] (
  circuitBreakerState: CircuitBreakerState,
  fallback: Optional[Function[In, Try[Out]]] = Optional.empty[Function[In, Try[Out]]],
  cleanUp: Consumer[Out] = new Consumer[Out] {
    override def accept(t: Out): Unit = ()
  },
  failureDecider: Optional[Function[Try[Out], JBoolean]] = Optional.empty[Function[Try[Out], JBoolean]],
  uniqueIdMapper: Function[Context, Any] = new Function[Context, Any] {
    override def apply(context: Context): Any = context
  }) {

  def withFallback(fallback: Function[In, Try[Out]]): CircuitBreakerSettings[In, Out, Context] =
    copy(fallback = Optional.of(fallback))

  def withCleanUp(cleanUp: Consumer[Out]): CircuitBreakerSettings[In, Out, Context] =
    copy(cleanUp = cleanUp)

  def withFailureDecider(failureDecider: Function[Try[Out], JBoolean]):
  CircuitBreakerSettings[In, Out, Context] = copy(failureDecider = Optional.of(failureDecider))

  def withUniqueIdMapper(uniqueIdMapper: Function[Context, Any]):
  CircuitBreakerSettings[In, Out, Context] = copy(uniqueIdMapper = uniqueIdMapper)

  import scala.compat.java8.OptionConverters._

  def toScala =
    org.squbs.streams.circuitbreaker.CircuitBreakerSettings(
      circuitBreakerState,
      fallback.asScala.map(f => (in: In) => f(in)),
      (out: Out) => cleanUp.accept(out),
      failureDecider.asScala.map(f => (out: Try[Out]) => f(out).asInstanceOf[Boolean]),
      Some(uniqueIdMapper.apply _))
}

object CircuitBreakerSettings {

  /**
    * Java API
    *
    * Creates a [[CircuitBreakerSettings]] with default values
    *
    * @param circuitBreakerState holds the state of circuit breaker
    * @tparam In Input type of [[org.squbs.streams.circuitbreaker.CircuitBreakerBidiFlow]]
    * @tparam Out Output type of [[org.squbs.streams.circuitbreaker.CircuitBreakerBidiFlow]]
    * @tparam Context the carried content in [[org.squbs.streams.circuitbreaker.CircuitBreakerBidiFlow]]
    * @return a [[CircuitBreakerSettings]] with default values
    */
  def create[In, Out, Context](circuitBreakerState: CircuitBreakerState): CircuitBreakerSettings[In, Out, Context] =
    CircuitBreakerSettings(circuitBreakerState)
}
