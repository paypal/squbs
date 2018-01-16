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

package org.squbs.streams

import java.util.Optional
import java.util.function.{Function => JFunction}
import java.lang.{Boolean => JBoolean}

import akka.NotUsed
import akka.http.org.squbs.util.JavaConverters
import akka.japi.Pair
import akka.stream.Attributes.InputBuffer
import akka.stream.scaladsl.{BidiFlow, Flow}
import akka.stream.stage._
import akka.stream._
import akka.stream.OverflowStrategy._

import scala.collection.mutable
import scala.util.{Failure, Try}

object RetryBidi {
  /**
    * Creates a [[BidiFlow]] that can be used to provide Retry functionality.
    * This API is specifically for flows that are using [[Try]]'s for elements that may occasionally fail.  By default,
    * any [[Failure]] is considered a failure that should be retried. However, a failureDecider function can be
    * specified to control what constitutes a failure.
    *
    * Retry functionality requires each element passing through this flow to be uniquely identifiable for retrying, so
    * it requires a [[Context]], of any type carried along with the flow's input and output element as a
    * [[Tuple2]] (Scala) or [[Pair]] (Java).  The requirement is that either the [[Context]] type itself or a mapping
    * from [[Context]] should be able to uniquely identify each element passing through flow.
    *
    * Here are the ways a unique id can be provided:
    *
    *   - [[Context]] itself is a type that can be used as a unique id, e.g., [[Int]], [[Long]], [[java.util.UUID]]
    *   - [[Context]] extends [[UniqueId.Provider]] and implements [[UniqueId.Provider.uniqueId]] method
    *   - [[Context]] is of type [[UniqueId.Envelope]]
    *   - [[Context]] can be mapped to a unique id by calling {{{uniqueIdMapper}}}
    *
    * This stage supports a default in-flight maximum number of elements based on stage Attribute InputBuffer max.
    * This maximum buffer size includes all elements currently failing (and being re-tried) as well as any elements
    * in-flight.  To increase this size you can update the stage attribute max value for InputBuffer.
    *
    * @param maxRetries     the maximum number of retry attempts on any failures before giving up.
    * @param uniqueIdMapper the function that maps [[Context]] to a unique id
    * @param failureDecider function to determine if an element passed by the joined [[Flow]] is
    *                       actually a failure or not
    * @param overflowStrategy the overflowStrategy to use on Retry buffer filling
    * @tparam In      the type of elements pulled from upstream along with the [[Context]]
    * @tparam Out     the type of the elements that are pushed to downstream along with the [[Context]]
    * @tparam Context the type of the context that is carried along with the elements.
    * @return a [[BidiFlow]] with Retry functionality
    */
  def apply[In, Out, Context](maxRetries: Long, uniqueIdMapper: Context => Option[Any] = (_: Any) => None,
                              failureDecider: Option[Try[Out] => Boolean] = None,
                              overflowStrategy: OverflowStrategy = OverflowStrategy.backpressure):
  BidiFlow[(In, Context), (In, Context), (Try[Out], Context), (Try[Out], Context), NotUsed] =
    BidiFlow.fromGraph(new RetryBidi(maxRetries, uniqueIdMapper, failureDecider, overflowStrategy))

  import scala.compat.java8.OptionConverters._
  /**
    * Java API
    * Creates a [[akka.stream.javadsl.BidiFlow]] that can be joined with a [[akka.stream.javadsl.Flow]] to add
    * Retry functionality with uniqueIdMapper, custom failure decider and OverflowStrategy.
    */
  def create[In, Out, Context](maxRetries: Long, uniqueIdMapper: JFunction[Context, Optional[Any]],
                               failureDecider: Optional[JFunction[Try[Out], JBoolean]],
                               overflowStrategy: OverflowStrategy):
  javadsl.BidiFlow[Pair[In, Context], Pair[In, Context], Pair[Try[Out], Context], Pair[Try[Out], Context], NotUsed] =
    JavaConverters.toJava(apply[In, Out, Context](maxRetries,
      uniqueIdMapper = UniqueId.javaUniqueIdMapperAsScala(uniqueIdMapper),
      failureDecider = failureDecider.asScala.map(f => (out: Try[Out]) => f(out)),
      overflowStrategy))

  /**
    * Java API
    * @see above for details about each parameter
    */
  def create[In, Out, Context](maxRetries: Long,
                               failureDecider: Optional[JFunction[Try[Out], JBoolean]],
                               overflowStrategy: OverflowStrategy):
  javadsl.BidiFlow[Pair[In, Context], Pair[In, Context], Pair[Try[Out], Context], Pair[Try[Out], Context], NotUsed] =
    JavaConverters.toJava(apply[In, Out, Context](maxRetries,
      failureDecider = failureDecider.asScala.map(f => (out: Try[Out]) => f(out)),
      overflowStrategy = overflowStrategy))

  /**
    * Java API
    * @see above for details about each parameter.
    */
  def create[In, Out, Context](maxRetries: Long, uniqueIdMapper: JFunction[Context, Optional[Any]],
                               overflowStrategy: OverflowStrategy):
  javadsl.BidiFlow[Pair[In, Context], Pair[In, Context], Pair[Try[Out], Context], Pair[Try[Out], Context], NotUsed] =
    JavaConverters.toJava(apply[In, Out, Context](maxRetries,
      uniqueIdMapper = UniqueId.javaUniqueIdMapperAsScala(uniqueIdMapper),
      overflowStrategy = overflowStrategy))

  /**
    * Java API
    * @see above for details about each parameter.
    */
  def create[In, Out, Context](maxRetries: Long):
  javadsl.BidiFlow[Pair[In, Context], Pair[In, Context], Pair[Try[Out], Context], Pair[Try[Out], Context], NotUsed] =
    JavaConverters.toJava(apply[In, Out, Context](maxRetries))
}

/**
  * A bidi [[GraphStage]] that can be joined with flows that produce [[Try]]'s to add Retry functionality
  * when there are any failures.  When the joined [[Flow]] has a failure then based on the provided
  * max retries count, it will retry the failures.
  *
  * '''Emits when''' a Success is available from joined flow or a failure has been retried the maximum number of retries
  *
  * '''Backpressures when''' the element is not a failure and downstream backpressures or the retry buffer is full
  *
  * '''Completes when''' upstream completes
  *
  * '''Cancels when''' downstream cancels
  *
  * {{{
  *          upstream      +------+      downstream
  *       (In, Context) ~> |      | ~> (In, Context)
  *            In1         | bidi |        Out1
  * (Try[Out], Context) <~ |      | <~ (Try[Out], Context)
  *           Out2         +------+        In2
  * }}}
  *
  * @param maxRetries maximum number of retry attempts on any failing [[Try]]'s
  * @param uniqueIdMapper function that maps a [[Context]] to a unique value per element
  * @param failureDecider function that gets called to determine if an element passed by the joined [[Flow]] is a
  *                       failure
  * @tparam In the type of elements pulled from the upstream along with the [[Context]]
  * @tparam Out the type of the elements that are pushed by the joined [[Flow]] along with the [[Context]].
  *             This then gets wrapped with a [[Try]] and pushed downstream with a [[Context]]
  * @tparam Context the type of the context that is carried around along with the elements.
  */
final class RetryBidi[In, Out, Context] private[streams](maxRetries: Long, uniqueIdMapper: Context => Option[Any],
                                                         failureDecider: Option[Try[Out] => Boolean] = None,
                                                         strategy: OverflowStrategy = OverflowStrategy.backpressure)
  extends GraphStage[BidiShape[(In, Context), (In, Context), (Try[Out], Context), (Try[Out], Context)]] {

  require(maxRetries > 0)

  private val in1 = Inlet[(In, Context)]("RetryBidi.in1")
  private val out1 = Outlet[(In, Context)]("RetryBidi.out1")
  private val in2 = Inlet[(Try[Out], Context)]("RetryBidi.in2")
  private val out2 = Outlet[(Try[Out], Context)]("RetryBidi.out2")
  override val shape = BidiShape(in1, out1, in2, out2)

  private[streams] def uniqueId(context: Context) =
    uniqueIdMapper(context).getOrElse {
      context match {
        case uniqueIdProvider: UniqueId.Provider ⇒ uniqueIdProvider.uniqueId
        case `context` ⇒ `context`
      }
    }

  private[streams] val isFailure = failureDecider.getOrElse((e: Try[Out]) => e.isFailure)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape)
    with StageLogging {

    val internalBufferSize: Int =
      inheritedAttributes.get[InputBuffer] match {
        case None ⇒ throw new IllegalStateException(s"Couldn't find InputBuffer Attribute for $this")
        case Some(InputBuffer(_, max)) ⇒ max
      }

    // A map of all the in flight (including failed) in elements with their retry count (bounded by size)
    private val retryRegistry = mutable.LinkedHashMap.empty[Any, (In, Context, Long)]
    private val readyToRetry = mutable.Queue.empty[(In, Context)] // Queue of elements ready to be emitted on out1
    private var upstreamFinished = false

    private def queueFailure(context: Context): Boolean =
      retryRegistry.get(uniqueId(context)) match {
        case None =>
          log.debug("Entry for context [{}] dropped", context)
          false
        case Some((_, ctx, retry)) if retry >= maxRetries =>
          retryRegistry -= uniqueId(ctx)
          log.debug("All retries exhausted for context [{}]", context)
          false
        case Some((in, ctx, retry)) =>
          retryRegistry += ((uniqueId(context), (in, ctx, retry + 1)))
          readyToRetry.enqueue((in, context))
          log.debug("Queueing retry {} for context [{}]", retry + 1, context)
          true
      }

    // Some useful hidden types for pattern match
    private val backPressure = OverflowStrategy.backpressure
    private val dropBuffer = OverflowStrategy.dropBuffer
    private val dropHead = OverflowStrategy.dropHead
    private val dropNew = OverflowStrategy.dropNew
    private val dropTail = OverflowStrategy.dropTail
    private val fail = OverflowStrategy.fail

    private def handleBufferFull(): Unit = strategy match {
      case `dropHead` =>
        retryRegistry -= retryRegistry.head._1 // build a Buffer for squbs
        log.debug("Buffer full dropping head")
        grabAndPush()
      case `dropNew` =>
        grab(in1)
        log.debug("Buffer full dropping newest")
      case `dropTail` =>
        retryRegistry -= retryRegistry.last._1
        log.debug("Buffer full dropping last")
        grabAndPush()
      case `dropBuffer` =>
        retryRegistry.clear()
        log.debug("Buffer full dropping buffer")
        grabAndPush()
      case `fail` =>
        failStage(BufferOverflowException(s"Retry buffer overflow for retry stage (max capacity was: $internalBufferSize)!"))
      case `backPressure` =>
        // NOP.  Don't grab any elements from upstream in backpressure mode when full
      case _ ⇒
        throw new IllegalStateException("Retry buffer overflow mode not supported")
    }

    private def pullCondition: Boolean = strategy != backpressure || retryRegistry.size < internalBufferSize
    private def isBufferFull: Boolean = retryRegistry.size >= internalBufferSize

    def grabAndPush(): Unit = {
      val (elem, context) = grab(in1)
      retryRegistry.put(uniqueId(context), (elem, context, 0))
      push(out1, (elem, context))
    }

    setHandler(in1, new InHandler {
      override def onPush(): Unit = {
        if (isAvailable(out1)) {
          if (isBufferFull) handleBufferFull()
          else grabAndPush()
        }
      }

      override def onUpstreamFinish(): Unit = {
        if (retryRegistry.isEmpty) completeStage()
        upstreamFinished = true
      }

      override def onUpstreamFailure(ex: Throwable): Unit = if (retryRegistry.isEmpty) fail(out1, ex) else failStage(ex)
    })

    setHandler(out1, new OutHandler {
      override def onPull(): Unit = {
        if (readyToRetry.nonEmpty) push(out1, readyToRetry.dequeue())
        else if (isAvailable(in1)) {
          if (isBufferFull) handleBufferFull()
          else grabAndPush()
        } else if (pullCondition && !upstreamFinished && !hasBeenPulled(in1)) pull(in1)
      }

      override def onDownstreamFinish(): Unit =
        if (retryRegistry.isEmpty) {
          completeStage()
          log.debug("completed Out1")
        } else cancel(in1)
    })

    setHandler(in2, new InHandler {
      override def onPush(): Unit = {
        val (elem, context) = grab(in2)
        if (isFailure(elem)) {
          if (queueFailure(context)) {
            if (isAvailable(out1)) push(out1, readyToRetry.dequeue())
            // continue propagating demand on in2 if grabbed element is queued for retry
            pull(in2)
          } else {
            if (isAvailable(out2)) push(out2, (elem, context))
            else log.error("out2 is not available for push.  Dropping exhausted element")
          }
        } else {
          retryRegistry.remove(uniqueId(context))
          if (isAvailable(out2)) push(out2, (elem, context))
          else log.error("out2 is not available for push.  Dropping successful element")
        }
      }

      override def onUpstreamFailure(ex: Throwable): Unit = if (readyToRetry.isEmpty) fail(out2, ex)
    })

    setHandler(out2, new OutHandler {
      override def onPull(): Unit =
        if (retryRegistry.isEmpty && upstreamFinished) completeStage()
        else if (!hasBeenPulled(in2)) pull(in2)

      override def onDownstreamFinish(): Unit =
        if (retryRegistry.isEmpty) {
          completeStage()
          log.debug("completed Out2")
        } else cancel(in2)

    })
  }

  override def toString: String = "RetryBidi"

}
