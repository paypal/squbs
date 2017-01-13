/*
 * Copyright 2015 PayPal
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

package org.squbs.circuitbreaker

import java.util.Optional
import java.util.function.{Function => JFunction}

import akka.NotUsed
import akka.http.org.squbs.util.JavaConverters
import akka.http.org.squbs.util.JavaConverters.toJava
import akka.japi.Pair
import akka.stream._
import akka.stream.scaladsl.{BidiFlow, Flow}
import akka.stream.stage._
import org.squbs.streams.TimeoutBidiUnordered

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

/**
  * A bidi [[GraphStage]] that is joined with a [[Flow]] to add Circuit Breaker functionality.  When the joined [[Flow]]
  * is having trouble responding in time or is responding with failures, then based on the provided settings, it
  * short circuits the [[Flow]]: Instead of pushing an element to the joined [[Flow]], it directly pushes down a
  * [[Failure]] or a fallback response to downstream, given that there is downstream demand.
  *
  * It accepts a [[CircuitBreakerState]] that keeps circuit breaker state.  The user can select a
  * [[CircuitBreakerState]] implementation that is right for the use case.  Please note, in many scenarios, a
  * [[CircuitBreakerState]] might need to be shared across materializations or even different streams.  In such
  * scenarios, make sure to select a [[CircuitBreakerState]] implementation that can work across materializations
  * concurrently.
  *
  * A [[fallback]] function can be passed to provide an alternative response when circuit is OPEN, .
  *
  * The joined [[Flow]] pushes down a [[Try]].  By default, any [[Failure]] is considered a problem and causes the
  * circuit breaker failure count to be incremented.  However, [[CircuitBreakerBidi]] also accepts a [[failureDecider]]
  * to decide on if an element passed by the joined [[Flow]] is actually considered a failure.  For instance, if
  * Circuit Breaker is joined with an Akka HTTP flow, a [[Success]] Http Response with status code 500 internal server
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
  * @param circuitBreakerState the [[CircuitBreakerState]] implementation that holds the state of the circuit breaker
  * @param fallback the function that gets called to provide an alternative response when the circuit is OPEN
  * @param failureDecider the function that gets called to determine if an element passed by the joined [[Flow]] is
  *                       actually a failure or not
  * @tparam In the type of the elements pulled from the upstream along with the [[Context]] and pushed down to joined
  *            flow
  * @tparam Out the type that's contained in a [[Try]] and pushed downstream along with the [[Context]]
  * @tparam Context the type of the context that is carried around along with the elements.  The context may be of any
  *                 type that can be used to uniquely identify each element
  */
class CircuitBreakerBidi[In, Out, Context](circuitBreakerState: CircuitBreakerState,
                                           fallback: Option[((In, Context)) => (Try[Out], Context)],
                                           failureDecider: Option[((Try[Out], Context)) => Boolean])
  extends GraphStage[BidiShape[(In, Context), (In, Context), (Try[Out], Context), (Try[Out], Context)]] {

  private val in = Inlet[(In, Context)]("CircuitBreakerBidi.in")
  private val fromWrapped = Inlet[(Try[Out], Context)]("CircuitBreakerBidi.fromWrapped")
  private val toWrapped = Outlet[(In, Context)]("CircuitBreakerBidi.toWrapped")
  private val out = Outlet[(Try[Out], Context)]("CircuitBreakerBidi.out")
  val shape = BidiShape(in, toWrapped, fromWrapped, out)
  private val isFailure = failureDecider.getOrElse((e: (Try[Out], Context)) => e._1.isFailure)

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
          val failFast = fallback.map(_(elem, context)).getOrElse((Failure(CircuitBreakerOpenException()), context))
          if(isAvailable(out) && readyToPush.isEmpty) push(out, failFast)
          else readyToPush.enqueue(failFast)
        } else push(toWrapped, (elem, context))
      }
      override def onUpstreamFinish(): Unit = complete(toWrapped)
      override def onUpstreamFailure(ex: Throwable): Unit = fail(toWrapped, ex)
    })

    setHandler(toWrapped, new OutHandler {
      override def onPull(): Unit = if(!hasBeenPulled(in)) pull(in)
      override def onDownstreamFinish(): Unit = completeStage()
    })

    setHandler(fromWrapped, new InHandler {
      override def onPush(): Unit = {
        val elemWithContext = grab(fromWrapped)

        if(isFailure(elemWithContext)) circuitBreakerState.markFailure()
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

      override def onDownstreamFinish(): Unit = cancel(fromWrapped)
    })
  }

  override def toString = "CircuitBreakerBidi"

}

object CircuitBreakerBidi {

  /**
    * Creates a bidi [[GraphStage]] that is joined with a [[Flow]] to add Circuit Breaker functionality
    * functionality.
    *
    * @param circuitBreakerState the [[CircuitBreakerState]] implementation that holds the state of the circuit breaker
    * @param fallback the function that gets called to provide an alternative response when the circuit is OPEN
    * @param failureDecider the function that gets called to determine if an element passed by the joined [[Flow]] is
    *                       actually a failure or not
    * @tparam In the type of the elements pulled from the upstream along with the [[Context]] and pushed down to joined
    *            flow
    * @tparam Out the type that's contained in a [[Try]] and pushed downstream along with the [[Context]]
    * @tparam Context the type of the context that is carried around along with the elements.  The context may be of any
    *                 type that can be used to uniquely identify each element
    * @return a [[CircuitBreakerBidi]] [[GraphStage]] that can be joined with a [[Flow]] with corresponding types to add
    *         timeout functionality.
    */
  def apply[In, Out, Context](circuitBreakerState: CircuitBreakerState,
            fallback: Option[((In, Context)) => (Try[Out], Context)] = None,
            failureDecider: Option[((Try[Out], Context)) => Boolean] = None):
  CircuitBreakerBidi[In, Out, Context] =
    new CircuitBreakerBidi(circuitBreakerState, fallback, failureDecider)
}

object CircuitBreakerBidiFlow {

  /**
    * Creates a [[CircuitBreakerBidiFlow]] with the default unique id retriever: {{{(context: Context) => context }}}.
    *
    * @see the API that takes a {{{uniqueId: Context => Id)}}} for more details.
    *
    * @param circuitBreakerState the [[CircuitBreakerState]] implementation that holds the state of the circuit breaker
    * @param fallback the function that gets called to provide an alternative response when the circuit is OPEN
    * @param failureDecider the function that gets called to determine if an element passed by the joined [[Flow]] is
    *                       actually a failure or not
    * @tparam In the type of the elements pulled from the upstream along with the [[Context]] and pushed down to joined
    *            flow
    * @tparam Out the type that's contained in a [[Try]] and pushed downstream along with the [[Context]]
    * @tparam Context the type of the context that is carried around along with the elements.  The context may be of any
    *                 type that can be used to uniquely identify each element
    * @return a [[CircuitBreakerBidiFlow]] that can be joined with a [[Flow]] with corresponding types to add timeout
    *         functionality.
    */
  def apply[In, Out, Context](circuitBreakerState: CircuitBreakerState,
                              fallback: Option[((In, Context)) => (Try[Out], Context)] = None,
                              failureDecider: Option[((Try[Out], Context)) => Boolean] = None):
  BidiFlow[(In, Context), (In, Context), (Out, Context), (Try[Out], Context), NotUsed] =
    apply(circuitBreakerState, fallback, failureDecider, (context: Context) => context)

  /**
    * Java API
    */
  def create[In, Out, Context](circuitBreakerState: CircuitBreakerState):
  akka.stream.javadsl.BidiFlow[Pair[In, Context], Pair[In, Context], Pair[Out, Context], Pair[Try[Out], Context], NotUsed] = {
    toJava[In, In, Out, Try[Out], Context](apply(circuitBreakerState, None, None))
  }

  /**
    * Java API
    */
  def create[In, Out, Context](circuitBreakerState: CircuitBreakerState,
                               fallback: Optional[JFunction[Pair[In, Context], Pair[Try[Out], Context]]],
                               failureDecider: Optional[JFunction[Pair[Try[Out], Context], Boolean]]):
  akka.stream.javadsl.BidiFlow[Pair[In, Context], Pair[In, Context], Pair[Out, Context], Pair[Try[Out], Context], NotUsed] = {
    JavaConverters.toJava[In, In, Out, Try[Out], Context] {
      apply(circuitBreakerState, fallbackAsScala(fallback), failureDeciderAsScala(failureDecider))
    }
  }

  /**
    * Creates a [[BidiFlow]] that can be joined with a [[Flow]] to add Circuit Breaker functionality.
    *
    * Circuit Breaker functionality requires each element to be uniquely identified, so it requires a [[Context]], of
    * any type defined by the application, to be carried along with the [[Flow]]'s input and output as a tuple.
    *
    * The requirement is that either the [[Context]] itself or an attribute accessed via the [[Context]] should be able
    * to uniquely identify an element.
    *
    * This API takes a unique id retriever.  Please also see the API with the default
    * unique id retriever: {{{(context: Context) => context }}}.
    *
    * @param circuitBreakerState the [[CircuitBreakerState]] implementation that holds the state of the circuit breaker
    * @param fallback the function that gets called to provide an alternative response when the circuit is OPEN
    * @param failureDecider the function that gets called to determine if an element passed by the joined [[Flow]] is
    *                       actually a failure or not
    * @tparam In the type of the elements pulled from the upstream along with the [[Context]] and pushed down to joined
    *            flow
    * @tparam Out the type that's contained in a [[Try]] and pushed downstream along with the [[Context]]
    * @tparam Context the type of the context that is carried around along with the elements.  The context may be of any
    *                 type that can be used to uniquely identify each element
    * @return a [[BidiFlow]] with Circuit Breaker functionality
    */
  def apply[In, Out, Context, Id](circuitBreakerState: CircuitBreakerState,
                                  fallback: Option[((In, Context)) => (Try[Out], Context)],
                                  failureDecider: Option[((Try[Out], Context)) => Boolean],
                                  uniqueId: Context => Id):
  BidiFlow[(In, Context), (In, Context), (Out, Context), (Try[Out], Context), NotUsed] =
    BidiFlow
      .fromGraph(CircuitBreakerBidi(circuitBreakerState, fallback, failureDecider))
      .atop(TimeoutBidiUnordered(circuitBreakerState.callTimeout, uniqueId))

  /**
    * Java API
    */
  def create[In, Out, Context, Id](circuitBreakerState: CircuitBreakerState,
                               fallback: Optional[JFunction[Pair[In, Context], Pair[Try[Out], Context]]],
                               failureDecider: Optional[JFunction[Pair[Try[Out], Context], Boolean]],
                               uniqueId: JFunction[Context, Id]):
  akka.stream.javadsl.BidiFlow[Pair[In, Context], Pair[In, Context], Pair[Out, Context], Pair[Try[Out], Context], NotUsed] = {
    import scala.compat.java8.FunctionConverters._
    JavaConverters.toJava[In, In, Out, Try[Out], Context] {
      apply(circuitBreakerState, fallbackAsScala(fallback), failureDeciderAsScala(failureDecider), uniqueId.asScala)
    }
  }

  private def fallbackAsScala[In, Out, Context](fallback: Optional[JFunction[Pair[In, Context], Pair[Try[Out], Context]]]) = {

    import scala.compat.java8.FunctionConverters._
    import scala.compat.java8.OptionConverters._

    def fallbackAsScala(fallback: JFunction[Pair[In, Context], Pair[Try[Out], Context]])
                                                      (tuple: ((In, Context))): (Try[Out], Context) = {
      val response = fallback.asScala.apply(Pair(tuple._1, tuple._2))
      (response.first, response.second)
    }

    fallback.asScala.map(fallbackAsScala _)
  }

  private def failureDeciderAsScala[Out, Context](failureDecider: Optional[JFunction[Pair[Try[Out], Context], Boolean]]) = {

    import scala.compat.java8.FunctionConverters._
    import scala.compat.java8.OptionConverters._

    def failureDeciderAsScala(failureDecider: JFunction[Pair[Try[Out], Context], Boolean])
                       (tuple: ((Try[Out], Context))): Boolean = {
      failureDecider.asScala.apply(Pair(tuple._1, tuple._2))
    }

    failureDecider.asScala.map(failureDeciderAsScala _)
  }
}
