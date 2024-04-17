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

package org.squbs.streams.circuitbreaker

import org.apache.pekko.NotUsed
import org.apache.pekko.http.org.squbs.util.JavaConverters
import org.apache.pekko.japi.Pair
import org.apache.pekko.stream._
import org.apache.pekko.stream.scaladsl.{BidiFlow, Flow}
import org.apache.pekko.stream.stage._
import org.squbs.streams.{Timeout, TimeoutSettings, UniqueId}

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

/**
  * A bidi [[GraphStage]] that is joined with a [[Flow]] to add Circuit Breaker functionality.  When the joined [[Flow]]
  * is having trouble responding in time or is responding with failures, then based on the provided settings, it
  * short circuits the [[Flow]]: Instead of pushing an element to the joined [[Flow]], it directly pushes down a
  * [[Failure]] or a fallback response to downstream, given that there is downstream demand.
  *
  * [[CircuitBreakerSettings.circuitBreakerState]] that keeps circuit breaker state.  The user can select a
  * [[CircuitBreakerState]] implementation that is right for the use case.  Please note, in many scenarios, a
  * [[CircuitBreakerState]] might need to be shared across materializations or even different streams.  In such
  * scenarios, make sure to select a [[CircuitBreakerState]] implementation that can work across materializations
  * concurrently.
  *
  * A [[CircuitBreakerSettings.fallback]] function provides an alternative response when circuit is OPEN.
  *
  * The joined [[Flow]] pushes down a [[Try]].  By default, any [[Failure]] is considered a problem and causes the
  * circuit breaker failure count to be incremented.  However, [[CircuitBreakerSettings.failureDecider]] can be used
  * to decide on if an element passed by the joined [[Flow]] is actually considered a failure.  For instance, if
  * Circuit Breaker is joined with an Pekko HTTP flow, a [[Success]] Http Response with status code 500 internal server
  * error should be considered a failure.
  *
  *
  * '''Emits when''' an element is available from the joined [[Flow]] or a short circuited element is available
  *
  * '''Backpressures when''' the downstream backpressures
  *
  * '''Completes when''' upstream completes
  *
  * '''Cancels when''' downstream cancels
  *
  * Please note, if the upstream does not control the throughput, then the throughput of the stream might temporarily
  * increase once the circuit is OPEN:  The downstream demand will be addressed with short circuit/fallback messages,
  * which might (or might not) take less time than it takes the joined [[Flow]] to process an element.  To eliminate
  * this problem, a throttle can be applied specifically for circuit breaker related messages.
  *
  * {{{
  *                        +------+
  *       (In, Context) ~> |      | ~> (In, Context)
  *                        | bidi |
  * (Try[Out], Context) <~ |      | <~ (Try[Out], Context)
  *                        +------+
  * }}}
  *
  * @param circuitBreakerSettings @see [[CircuitBreakerSettings]]
  * @tparam In the type of the elements pulled from the upstream along with the [[Context]] and pushed down to joined
  *            flow
  * @tparam Out the type that's contained in a [[Try]] and pushed downstream along with the [[Context]]
  * @tparam Context the type of the context that is carried around along with the elements.  The context may be of any
  *                 type that can be used to uniquely identify each element
  */
class CircuitBreaker[In, Out, Context] private[circuitbreaker](
  circuitBreakerSettings: CircuitBreakerSettings[In, Out, Context])
  extends GraphStage[BidiShape[(In, Context), (In, Context), (Try[Out], Context), (Try[Out], Context)]] {

  private val in = Inlet[(In, Context)]("CircuitBreakerBidi.in")
  private val fromWrapped = Inlet[(Try[Out], Context)]("CircuitBreakerBidi.fromWrapped")
  private val toWrapped = Outlet[(In, Context)]("CircuitBreakerBidi.toWrapped")
  private val out = Outlet[(Try[Out], Context)]("CircuitBreakerBidi.out")
  val shape = BidiShape(in, toWrapped, fromWrapped, out)
  private val isFailure = circuitBreakerSettings.failureDecider.getOrElse((e: Try[Out]) => e.isFailure)

  private val circuitBreakerState = circuitBreakerSettings.circuitBreakerState
  private val fallback = circuitBreakerSettings.fallback

  override def initialAttributes = Attributes.name("CircuitBreakerBidi")

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    private var upstreamFinished = false
    private val readyToPush = mutable.Queue[(Try[Out], Context)]()

    private def onPushFromWrapped(elem: (Try[Out], Context), isOutAvailable: Boolean): Option[(Try[Out], Context)] = {
      readyToPush.enqueue(elem)
      if(isOutAvailable) Some(readyToPush.dequeue())
      else None
    }

    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        val (elem, context) = grab(in)
        if(circuitBreakerState.checkAndMarkIfShortCircuit()) {
          val failFast = fallback.map(_(elem)).getOrElse(Failure(CircuitBreakerOpenException()))
          if(isAvailable(out) && readyToPush.isEmpty) push(out, (failFast, context))
          else readyToPush.enqueue((failFast, context))
        } else push(toWrapped, (elem, context))
      }
      override def onUpstreamFinish(): Unit = complete(toWrapped)
      override def onUpstreamFailure(ex: Throwable): Unit = fail(toWrapped, ex)
    })

    setHandler(toWrapped, new OutHandler {
      override def onPull(): Unit = if(!hasBeenPulled(in)) pull(in)
      override def onDownstreamFinish(cause: Throwable): Unit = {
        completeStage()
        super.onDownstreamFinish(cause)
      }
    })

    setHandler(fromWrapped, new InHandler {
      override def onPush(): Unit = {
        val elemWithContext = grab(fromWrapped)

        if(isFailure(elemWithContext._1)) circuitBreakerState.markFailure()
        else circuitBreakerState.markSuccess()

        onPushFromWrapped(elemWithContext, isAvailable(out)).foreach(tuple => push(out, tuple))
      }
      override def onUpstreamFinish(): Unit = {
        if(readyToPush.isEmpty) completeStage()
        else upstreamFinished = true
      }

      override def onUpstreamFailure(ex: Throwable): Unit = fail(out, ex)
    })

    setHandler(out, new OutHandler {
      override def onPull(): Unit =
        if(!upstreamFinished || readyToPush.nonEmpty) readyToPush.dequeueFirst((_: (Try[Out], Context)) => true) match {
          case Some(elemWithContext) => push(out, elemWithContext)
          case None =>
            if(!hasBeenPulled(fromWrapped)) pull(fromWrapped)
            else if(!hasBeenPulled(in) && isAvailable(toWrapped)) pull(in)
        }
        else complete(out)

      override def onDownstreamFinish(cause: Throwable): Unit = {
        cancel(fromWrapped)
        super.onDownstreamFinish(cause)
      }
    })
  }

  override def toString = "CircuitBreakerBidi"

}

object CircuitBreaker {

  /**
    * Creates a [[BidiFlow]] that can be joined with a [[Flow]] to add Circuit Breaker functionality.
    *
    * @see [[CircuitBreakerSettings]] for details about each parameter and type parameter.
    */
  def apply[In, Out, Context](circuitBreakerSettings: CircuitBreakerSettings[In, Out, Context]):
  BidiFlow[(In, Context), (In, Context), (Out, Context), (Try[Out], Context), NotUsed] =
    BidiFlow
      .fromGraph(new CircuitBreaker(circuitBreakerSettings))
      .atop(Timeout(
        TimeoutSettings[In, Out, Context](
          circuitBreakerSettings.circuitBreakerState.callTimeout,
          circuitBreakerSettings.uniqueIdMapper,
          circuitBreakerSettings.cleanUp)))

  /**
    * Java API
    *
    * Creates a [[akka.stream.javadsl.BidiFlow]] that can be joined with a [[akka.stream.javadsl.Flow]] to add
    * Circuit Breaker functionality.
    *
    * @see [[CircuitBreakerSettings]] for details about each parameter and type parameter.
    */
  def create[In, Out, Context](circuitBreakerSettings: japi.CircuitBreakerSettings[In, Out, Context]):
  org.apache.pekko.stream.javadsl.BidiFlow[Pair[In, Context], Pair[In, Context], Pair[Out, Context], Pair[Try[Out], Context], NotUsed] =
    JavaConverters.toJava[In, In, Out, Try[Out], Context](apply[In, Out, Context](circuitBreakerSettings.toScala))
}

/**
  * A container to hold circuit breaker settings.
  *
  * Circuit Breaker functionality requires each element to be uniquely identified, so it requires a [[Context]], of
  * any type defined by the application, to be carried along with the flow's input and output as a [[Tuple2]] (Scala)
  * or [[Pair]] (Java).  The requirement is that either the [[Context]] itself or a mapping from [[Context]] should be
  * able to uniquely identify an element.  Here is the ways how a unique id can be retrieved:
  *
  *   - [[Context]] itself is a type that can be used as a unique id, e.g., [[Int]], [[Long]], [[java.util.UUID]]
  *   - [[Context]] extends [[UniqueId.Provider]] and implements [[UniqueId.Provider.uniqueId]] method
  *   - [[Context]] is of type [[UniqueId.Envelope]]
  *   - [[Context]] can be mapped to a unique id by calling {{{uniqueIdMapper}}}
  *
  * @param circuitBreakerState the [[CircuitBreakerState]] implementation that holds the state of the circuit breaker
  * @param fallback the function that gets called to provide an alternative response when the circuit is OPEN
  * @param cleanUp an optional clean up function to be applied on timed out elements when pushed
  * @param failureDecider the function that gets called to determine if an element pased by the joined [[Flow]] is
  *                       actually a failure or not
  * @param uniqueIdMapper the function that maps [[Context]] to a unique id
  * @tparam In the type of the elements pulled from the upstream along with the [[Context]] and pushed down to joined
  *            flow
  * @tparam Out the type that's contained in a [[Try]] and pushed downstream along with the [[Context]]
  * @tparam Context the type of the context that is carried around along with the elements.  The context may be of any
  *                 type that can be used to uniquely identify each element
  */
case class CircuitBreakerSettings[In, Out, Context] private[circuitbreaker] (
  circuitBreakerState: CircuitBreakerState,
  fallback: Option[In => Try[Out]] = None,
  cleanUp: Out => Unit = (_: Out) => (),
  failureDecider: Option[Try[Out] => Boolean] = None,
  uniqueIdMapper: Option[Context => Any] = None) {

  def withFallback(fallback: In => Try[Out]): CircuitBreakerSettings[In, Out, Context] =
    copy(fallback = Some(fallback))

  def withCleanUp(cleanUp: Out => Unit): CircuitBreakerSettings[In, Out, Context] =
    copy(cleanUp = cleanUp)

  def withFailureDecider(failureDecider: Try[Out] => Boolean): CircuitBreakerSettings[In, Out, Context] =
    copy(failureDecider = Some(failureDecider))

  def withUniqueIdMapper(uniqueIdMapper: Context => Any): CircuitBreakerSettings[In, Out, Context] =
    copy(uniqueIdMapper = Some(uniqueIdMapper))
}

object CircuitBreakerSettings {

  /**
    * Creates a [[CircuitBreakerSettings]] with default values
    *
    * @param circuitBreakerState holds the state of circuit breaker
    * @tparam In Input type of [[CircuitBreaker]]
    * @tparam Out Output type of [[CircuitBreaker]]
    * @tparam Context the carried content in [[CircuitBreaker]]
    * @return a [[CircuitBreakerSettings]] with default values
    */
  def apply[In, Out, Context](circuitBreakerState: CircuitBreakerState): CircuitBreakerSettings[In, Out, Context] =
    CircuitBreakerSettings[In, Out, Context](circuitBreakerState, None)
}
