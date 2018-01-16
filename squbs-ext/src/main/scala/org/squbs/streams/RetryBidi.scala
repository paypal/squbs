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
import java.lang.{Boolean => JBoolean, Long => JLong}
import java.util.concurrent.{DelayQueue, Delayed, TimeUnit}

import akka.NotUsed
import akka.http.org.squbs.util.JavaConverters
import akka.japi.Pair
import akka.stream.Attributes.InputBuffer
import akka.stream.scaladsl.{BidiFlow, Flow}
import akka.stream.stage._
import akka.stream._
import akka.stream.OverflowStrategy._

import scala.concurrent.duration._
import scala.collection.mutable
import scala.concurrent.duration.{Duration, FiniteDuration}
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
    * An optional delay duration interval (with an optional exponential backoff factor) can be used to delay the
    * retries of each failing retry.  Delay must be great 10ms (timer precision)
    *
    * @param maxRetries     the maximum number of retry attempts on any failures before giving up.
    * @param uniqueIdMapper the function that maps [[Context]] to a unique id
    * @param failureDecider function to determine if an element passed by the joined [[Flow]] is
    *                       actually a failure or not
    * @param overflowStrategy the overflowStrategy to use on Retry buffer filling
    * @param delay            the delay duration between retrying each failed retry
    * @param exponentialBackoffFactor exponential factor the delay duration will be increased upon each retry
    * @param maxDelay the maximum delay duration during retry backoff
    * @tparam In      the type of elements pulled from upstream along with the [[Context]]
    * @tparam Out     the type of the elements that are pushed to downstream along with the [[Context]]
    * @tparam Context the type of the context that is carried along with the elements.
    * @return a [[BidiFlow]] with Retry functionality
    */
  def apply[In, Out, Context](maxRetries: Long, uniqueIdMapper: Context => Option[Any] = (_: Any) => None,
                              failureDecider: Option[Try[Out] => Boolean] = None,
                              overflowStrategy: OverflowStrategy = OverflowStrategy.backpressure,
                              delay: FiniteDuration = Duration.Zero,
                              exponentialBackoffFactor: Double = 1.0,
                              maxDelay: FiniteDuration = Duration.Zero):
  BidiFlow[(In, Context), (In, Context), (Try[Out], Context), (Try[Out], Context), NotUsed] =
    BidiFlow.fromGraph(new RetryBidi[In, Out, Context](maxRetries, uniqueIdMapper, failureDecider, overflowStrategy,
      delay, exponentialBackoffFactor, maxDelay))

  /**
    * @param retrySettings @see [[RetrySettings]]
    * @tparam In      the type of elements pulled from upstream along with the [[Context]]
    * @tparam Out     the type of the elements that are pushed to downstream along with the [[Context]]
    * @tparam Context the type of the context that is carried along with the elements.
    * @return a [[BidiFlow]] with Retry functionality
    */
  def apply[In, Out, Context](retrySettings: RetrySettings[In, Out, Context]):
  BidiFlow[(In, Context), (In, Context), (Try[Out], Context), (Try[Out], Context), NotUsed] =
    BidiFlow.fromGraph(new RetryBidi(
      maxRetries = retrySettings.maxRetries,
      uniqueIdMapper = retrySettings.uniqueIdMapper,
      failureDecider = retrySettings.failureDecider,
      overflowStrategy = retrySettings.overflowStrategy,
      delay = retrySettings.delay,
      exponentialBackoffFactor = retrySettings.exponentialBackoffFactor,
      maxDelay = retrySettings.maxDelay))

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
    JavaConverters.toJava(apply[In, Out, Context](new RetrySettings[In, Out, Context](
      maxRetries = maxRetries,
      uniqueIdMapper = UniqueId.javaUniqueIdMapperAsScala(uniqueIdMapper),
      failureDecider = failureDecider.asScala.map(f => (out: Try[Out]) => f(out)),
      overflowStrategy = overflowStrategy)))

  /**
    * Java API
    * @see above for details about each parameter
    */
  def create[In, Out, Context](maxRetries: Long,
                               failureDecider: Optional[JFunction[Try[Out], JBoolean]],
                               overflowStrategy: OverflowStrategy):
  javadsl.BidiFlow[Pair[In, Context], Pair[In, Context], Pair[Try[Out], Context], Pair[Try[Out], Context], NotUsed] =
    JavaConverters.toJava(apply[In, Out, Context](new RetrySettings[In, Out, Context](
      maxRetries = maxRetries,
      failureDecider = failureDecider.asScala.map(f => (out: Try[Out]) => f(out)),
      overflowStrategy = overflowStrategy)))

  /**
    * Java API
    * @see above for details about each parameter.
    */
  def create[In, Out, Context](maxRetries: Long, uniqueIdMapper: JFunction[Context, Optional[Any]],
                               overflowStrategy: OverflowStrategy):
  javadsl.BidiFlow[Pair[In, Context], Pair[In, Context], Pair[Try[Out], Context], Pair[Try[Out], Context], NotUsed] =
    JavaConverters.toJava(apply[In, Out, Context](
      maxRetries = maxRetries,
      uniqueIdMapper = UniqueId.javaUniqueIdMapperAsScala(uniqueIdMapper),
      overflowStrategy = overflowStrategy))

  /**
    * Java API
    * @see above for details about each parameter.
    */
  def create[In, Out, Context](maxRetries: Long):
  javadsl.BidiFlow[Pair[In, Context], Pair[In, Context], Pair[Try[Out], Context], Pair[Try[Out], Context], NotUsed] =
    JavaConverters.toJava(apply[In, Out, Context](maxRetries = maxRetries))

  /**
    * Java API
    * @see above for details about each parameter.
    */
  def create[In, Out, Context](retrySettings: RetrySettings[In, Out, Context]):
  javadsl.BidiFlow[Pair[In, Context], Pair[In, Context], Pair[Try[Out], Context], Pair[Try[Out], Context], NotUsed] =
    JavaConverters.toJava(apply[In, Out, Context](retrySettings))
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
  * @param overflowStrategy the overflowStrategy to use on Retry buffer filling
  * @param delay the delay duration to wait between each retry.  Defaults to 0 nanos (no delay)
  * @param exponentialBackoffFactor The exponential backoff factor that the delay duration will be increased on each
  *                                 retry
  * @param maxDelay The maximum retry delay duration during retry backoff
  * @tparam In the type of elements pulled from the upstream along with the [[Context]]
  * @tparam Out the type of the elements that are pushed by the joined [[Flow]] along with the [[Context]].
  *             This then gets wrapped with a [[Try]] and pushed downstream with a [[Context]]
  * @tparam Context the type of the context that is carried around along with the elements.
  */
final class RetryBidi[In, Out, Context] private[streams](maxRetries: Long, uniqueIdMapper: Context => Option[Any],
                                                         failureDecider: Option[Try[Out] => Boolean] = None,
                                                         overflowStrategy: OverflowStrategy = OverflowStrategy.backpressure,
                                                         delay: FiniteDuration = Duration.Zero,
                                                         exponentialBackoffFactor: Double = 1.0,
                                                         maxDelay: FiniteDuration = Duration.Zero)
  extends GraphStage[BidiShape[(In, Context), (In, Context), (Try[Out], Context), (Try[Out], Context)]] {

  private val in1 = Inlet[(In, Context)]("RetryBidi.in1")
  private val out1 = Outlet[(In, Context)]("RetryBidi.out1")
  private val in2 = Inlet[(Try[Out], Context)]("RetryBidi.in2")
  private val out2 = Outlet[(Try[Out], Context)]("RetryBidi.out2")
  private val delayAsNanos = delay.toNanos
  private val precisionAsNanos = 10.milliseconds.toNanos // the linux timer precision
  private val timerName = "RetryStageTimer"
  override val shape = BidiShape(in1, out1, in2, out2)

  require(maxRetries > 0, "maximum retry count must be positive")
  require(delay == Duration.Zero || delayAsNanos > precisionAsNanos, "Delay must be greater than timer precision")
  require(exponentialBackoffFactor >= 0.0, "backoff factor must be >= 0.0")
  require(maxDelay == Duration.Zero || maxDelay >= delay, "maxDelay must be larger than delay")

  private[streams] def uniqueId(context: Context) =
    uniqueIdMapper(context).getOrElse {
      context match {
        case uniqueIdProvider: UniqueId.Provider ⇒ uniqueIdProvider.uniqueId
        case `context` ⇒ `context`
      }
    }

  private[streams] val isFailure = failureDecider.getOrElse((e: Try[Out]) => e.isFailure)

  // scalastyle:off method.length
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new TimerGraphStageLogic(shape)
    with StageLogging {

    val internalBufferSize: Int =
      inheritedAttributes.get[InputBuffer] match {
        case None => throw new IllegalStateException(s"Couldn't find InputBuffer Attribute for $this")
        case Some(InputBuffer(_, max)) => max
      }

    case class RetryTracker(ctx: Context, count: Long, lastFailureTime: Long) extends Delayed {
      override def getDelay(unit: TimeUnit): Long = {
        if (noDelay) 0
        else {
          val elapsedNanos = System.nanoTime() - lastFailureTime
          unit.convert(computeSleepTime(count) - elapsedNanos, NANOSECONDS)
        }
      }

      override def compareTo(o: Delayed): Int =
        if (noDelay) 0 else if (getDelay(TimeUnit.NANOSECONDS) < o.getDelay(TimeUnit.NANOSECONDS)) -1 else +1
    }
    private val retryDelayQ = new DelayQueue[RetryTracker]()

    // A map of all the in flight (including failed) in elements (bounded by size)
    private val retryRegistry = mutable.LinkedHashMap.empty[Any, (In, Context, RetryTracker)]
    private val noDelay = delay == Duration.Zero
    private var upstreamFinished = false

    private def queueFailure(context: Context): Boolean =
      retryRegistry.get(uniqueId(context)) match {
        case None =>
          log.debug("Element for context [{}] dropped", context)
          false
        case Some((_, ctx, retryTracker)) if retryTracker.count >= maxRetries =>
          log.debug("Retries exhausted for context [{}]", context)
          retryRegistry.get(uniqueId(ctx)) foreach(entry => {
            retryDelayQ.remove(entry._3)
            retryRegistry -= uniqueId(entry._2)
          })
          false
        case Some((_, ctx, retryTracker)) =>
          log.debug("Queueing retry {} for context [{}]", retryTracker.count + 1, context)
          updateTracker(ctx).foreach(tracker => retryDelayQ.add(tracker))
          if (!isTimerActive(timerName) && !noDelay) scheduleOnce(timerName, sleepTimeLeft)
          true
      }

    // Some useful hidden types for pattern match
    private val backPressure = OverflowStrategy.backpressure
    private val dropBuffer = OverflowStrategy.dropBuffer
    private val dropHead = OverflowStrategy.dropHead
    private val dropNew = OverflowStrategy.dropNew
    private val dropTail = OverflowStrategy.dropTail
    private val fail = OverflowStrategy.fail

    private def handleBufferFull(): Unit = overflowStrategy match {
      case `dropHead` =>
        val head = retryRegistry.head
        retryDelayQ.remove(head._2._3)
        retryRegistry -= head._1 // build a Buffer for squbs
        log.debug("Buffer full dropping head")
        grabAndPush()
      case `dropNew` =>
        grab(in1)
        log.debug("Buffer full dropping newest")
      case `dropTail` =>
        val tail = retryRegistry.last
        retryDelayQ.remove(tail._2._3)
        retryRegistry -= tail._1
        log.debug("Buffer full dropping last")
        grabAndPush()
      case `dropBuffer` =>
        retryDelayQ.clear()
        retryRegistry.clear()
        log.debug("Buffer full dropping buffer")
        grabAndPush()
      case `fail` =>
        failStage(BufferOverflowException(s"Buffer overflow for Retry stage (max capacity was: $internalBufferSize)!"))
      case `backPressure` => // NOP.  Don't grab any elements from upstream in backpressure mode when full
      case _ ⇒
        throw new IllegalStateException("Retry buffer overflow mode not supported")
    }

    private def pullCondition = overflowStrategy != backpressure || retryRegistry.size < internalBufferSize
    private def isBufferFull = retryRegistry.size >= internalBufferSize

    def grabAndPush(): Unit = {
      val (elem, ctx) = grab(in1)
      val tracker = RetryTracker(ctx, 0, System.nanoTime())
      retryRegistry.put(uniqueId(ctx), (elem, ctx, tracker))
      log.debug("1st attempt for context [{}] ", tracker.ctx) // refactor
      push(out1, (elem, ctx))
    }

    def readyOption(): Option[(In, Context)] =
      Option(retryDelayQ.poll()) flatMap (retryTracker => {
        retryRegistry.get(uniqueId(retryTracker.ctx))
      }) match {
        case Some(e) => Some(e._1, e._2)
        case None => None
      }

    def pushIfReady(): Unit =
      readyOption() match {
        case Some(elemWithContext) =>
          log.debug("Retrying context {} ", elemWithContext._2)
          push(out1, (elemWithContext._1, elemWithContext._2))
        case None =>
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
        readyOption() match {
          case Some(elemWithCtx) =>
            push(out1, elemWithCtx)
          case None =>
            if (isAvailable(in1)) {
              if (isBufferFull) handleBufferFull()
              else grabAndPush()
            } else if (pullCondition && !upstreamFinished && !hasBeenPulled(in1)) pull(in1)
        }
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
            if (isAvailable(out1)) pushIfReady()
            // continue propagating demand on in2 if grabbed element is queued for retry
            pull(in2)
          } else {
            if (isAvailable(out2)) push(out2, (elem, context))
            else log.error("out2 is not available for push.  Dropping exhausted element")
          }
        } else {
          retryRegistry.get(uniqueId(context)) foreach(entry => {
            retryDelayQ.remove(entry._3)
            retryRegistry.remove(uniqueId(entry._2))
          })
          if (isAvailable(out2)) push(out2, (elem, context))
          else log.error("out2 is not available for push.  Dropping successful element")
        }
      }

      override def onUpstreamFailure(ex: Throwable): Unit = if (retryDelayQ.isEmpty) fail(out2, ex)
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

    final override def onTimer(key: Any): Unit = {
      if (isAvailable(out1)) pushIfReady()
      if (!retryDelayQ.isEmpty) scheduleOnce(timerName, sleepTimeLeft)
    }

    private def updateTracker(context: Context): Option[RetryTracker] =
      retryRegistry.get(uniqueId(context)) match {
        case Some((elem, ctx, retryTracker)) =>
          val newTracker = RetryTracker(ctx, retryTracker.count + 1, System.nanoTime())
          retryRegistry += ((uniqueId(ctx), (elem, ctx, newTracker)))
          Some(newTracker)
        case None => None
      }

    private def sleepTimeLeft: FiniteDuration = {
      Option(retryDelayQ.peek()) match {
        case Some(retryTracker) =>
          val elapsedTime = System.nanoTime() - retryTracker.lastFailureTime
          FiniteDuration(math.max(computeSleepTime(retryTracker.count) - elapsedTime, delayAsNanos), NANOSECONDS)
        case None => FiniteDuration(delayAsNanos, NANOSECONDS)
      }
    }

    private def computeSleepTime(retry: Long): Long = {
      // each retry delay will be delay duration * { backoff factor }
      // backoffFactor is (N ^ expbackOffFactor ) up to maxdelay (if one is specified)
      // E.g with a delay duration of 200ms and exponentialbackoff of 1.5
      //     retry,   delay * backoff factor = internal
      //       1        200 * (1 ^ 1.5) =   200ms
      //       2        200 * (2 ^ 1.5) =   566ms
      //       3        200 * (3 ^ 1.5) =  1039ms
      //       4        200 * (4 ^ 1.5) =  1600ms
      //     ...                        = <maxDelay if one is specified>
      val backoffFactor = math.pow(retry, exponentialBackoffFactor)
      val sleepTimeAsNanos = (delayAsNanos * backoffFactor).toLong
      if (maxDelay != Duration.Zero) math.min(sleepTimeAsNanos, maxDelay.toNanos)
      else sleepTimeAsNanos
    }
  }
  // scalastyle:on method.length

  override def toString: String = "RetryBidi"

}

/**
  * A Retry Settings class for configuring a RetryBidi
  *
  * Retry functionality requires each element passing through is uniquely identifiable for retrying, so
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
  * @param maxRetries     maximum number of retry attempts on any failures before giving up.
  * @param uniqueIdMapper function that maps [[Context]] to a unique id
  * @param failureDecider function to determine if an element passed by the joined [[Flow]] is
  *                       actually a failure or not
  * @param overflowStrategy overflowStrategy to use on Retry buffer filling
  * @param delay            to delay between retrying each failed element.
  * @param exponentialBackoffFactor exponential amount the delay duration will be increased upon each retry
  * @param maxDelay maximum delay duration for retry.
  * @tparam In      the type of elements pulled from upstream along with the [[Context]]
  * @tparam Out     the type of the elements that are pushed to downstream along with the [[Context]]
  * @tparam Context the type of the context that is carried along with the elements.
  * @return a [[RetrySettings]] with specified values
  */
case class RetrySettings[In, Out, Context] private[streams](
   maxRetries: Long,
   uniqueIdMapper: Context => Option[Any] = (_: Any) => None,
   failureDecider: Option[Try[Out] => Boolean] = None,
   overflowStrategy: OverflowStrategy = OverflowStrategy.backpressure,
   delay: FiniteDuration = Duration.Zero,
   exponentialBackoffFactor: Double = 0.0,
   maxDelay: FiniteDuration = Duration.Zero) {

  def withUniqueIdMapper(uniqueIdMapper: Context => Option[Any]): RetrySettings[In, Out, Context] =
    copy(uniqueIdMapper = uniqueIdMapper)

  def withFailureDecider(failureDecider: Try[Out] => Boolean): RetrySettings[In, Out, Context] =
    copy(failureDecider = Some(failureDecider))

  def withOverflowStrategy(overflowStrategy: OverflowStrategy): RetrySettings[In, Out, Context] =
    copy(overflowStrategy = overflowStrategy)

  def withDelay(delay: FiniteDuration): RetrySettings[In, Out, Context] =
    copy(delay = delay)

  def withExponentialBackoff(exponentialBackoffFactor: Double): RetrySettings[In, Out, Context] =
    copy(exponentialBackoffFactor = exponentialBackoffFactor)

  def withMaxDelay(maxDelay: FiniteDuration): RetrySettings[In, Out, Context] =
    copy(maxDelay = maxDelay)

  import scala.compat.java8.OptionConverters._

  // Java API
  def withUniqueIdMapper(uniqueIdMapper: JFunction[Context, Optional[Any]]): RetrySettings[In, Out, Context] =
    copy(uniqueIdMapper = UniqueId.javaUniqueIdMapperAsScala(uniqueIdMapper))

  def withFailureDecider(failureDecider: Optional[JFunction[Try[Out], JBoolean]]): RetrySettings[In, Out, Context] =
    copy(failureDecider = failureDecider.asScala.map(f => (out: Try[Out]) => f(out).asInstanceOf[Boolean]))
}

object RetrySettings {
  /**
    * Creates a [[RetrySettings]] with default values that can be used to create a RetryBidi
    *
    * @param maxRetries the maximum number of retry attempts on any failures before giving up.
    * @tparam In Input type of [[RetryBidi]]
    * @tparam Out Output type of [[RetryBidi]]
    * @tparam Context the context type in [[RetryBidi]]
    * @return a [[RetrySettings]] with default values
    */
  def apply[In, Out, Context](maxRetries: Long): RetrySettings[In, Out, Context] =
    new RetrySettings[In, Out, Context](maxRetries)

  /**
    * Java API
    *
    * Creates a [[RetrySettings]] with default values that can be used to create a RetryBidi
    *
    * @tparam In Input type of [[org.squbs.streams.RetryBidi]]
    * @tparam Out Output type of [[org.squbs.streams.RetryBidi]]
    * @tparam Context the carried content in [[org.squbs.streams.RetryBidi]]
    * @return a [[RetrySettings]] with default values
    */
  def create[In, Out, Context](maxRetries: JLong): RetrySettings[In, Out, Context] =
    RetrySettings[In, Out, Context](maxRetries)

}
