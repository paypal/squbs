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

import java.lang.management.ManagementFactory
import java.lang.{Boolean => JBoolean}
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.{Function => JFunction}

import javax.management.{MXBean, ObjectName}
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.event.LoggingAdapter
import org.apache.pekko.japi.Pair
import org.apache.pekko.stream.Attributes.InputBuffer
import org.apache.pekko.stream._
import org.apache.pekko.stream.scaladsl.{BidiFlow, Flow}
import org.apache.pekko.stream.stage._
import com.codahale.metrics.MetricRegistry
import org.apache.pekko.http.org.squbs.util.JavaConverters
import org.squbs.metrics.MetricsExtension

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.Try

object Retry {

  /**
    * @param max     maximum number of retry attempts on any failures before giving up.
    * @tparam In      the type of elements pulled from upstream along with the [[Context]]
    * @tparam Out     the type of the elements that are pushed to downstream along with the [[Context]]
    * @tparam Context the type of the context that is carried along with the elements.
    * @return a [[BidiFlow]] with Retry functionality
    */
  def apply[In, Out, Context](max: Int):
  BidiFlow[(In, Context), (In, Context), (Try[Out], Context), (Try[Out], Context), NotUsed] =
    apply(RetrySettings[In, Out, Context](max))

  /**
    * @param retrySettings @see [[RetrySettings]]
    * @tparam In      the type of elements pulled from upstream along with the [[Context]]
    * @tparam Out     the type of the elements that are pushed to downstream along with the [[Context]]
    * @tparam Context the type of the context that is carried along with the elements.
    * @return a [[BidiFlow]] with Retry functionality
    */
  def apply[In, Out, Context](retrySettings: RetrySettings[In, Out, Context]):
  BidiFlow[(In, Context), (In, Context), (Try[Out], Context), (Try[Out], Context), NotUsed] =
    BidiFlow.fromGraph(new Retry(retrySettings))

  /**
    * Java API
    *
    * @param max     maximum number of retry attempts on any failures before giving up.
    * @tparam In      the type of elements pulled from upstream along with the [[Context]]
    * @tparam Out     the type of the elements that are pushed to downstream along with the [[Context]]
    * @tparam Context the type of the context that is carried along with the elements.
    * @return a [[BidiFlow]] with Retry functionality
    */
  def create[In, Out, Context](max: Integer):
  javadsl.BidiFlow[Pair[In, Context], Pair[In, Context], Pair[Try[Out], Context], Pair[Try[Out], Context], NotUsed] =
    create(RetrySettings[In, Out, Context](max))

  /**
    * Java API
    *
    * @param retrySettings @see [[RetrySettings]]
    * @tparam In      the type of elements pulled from upstream along with the [[Context]]
    * @tparam Out     the type of the elements that are pushed to downstream along with the [[Context]]
    * @tparam Context the type of the context that is carried along with the elements.
    * @return a [[BidiFlow]] with Retry functionality
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
  * @param settings the settings to configure Retry with
  * @tparam In the type of elements pulled from the upstream along with the [[Context]]
  * @tparam Out the type of the elements that are pushed by the joined [[Flow]] along with the [[Context]].
  *             This then gets wrapped with a [[Try]] and pushed downstream with a [[Context]]
  * @tparam Context the type of the context that is carried around along with the elements.
  */
final class Retry[In, Out, Context] private[streams](settings: RetrySettings[In, Out, Context])
  extends GraphStage[BidiShape[(In, Context), (In, Context), (Try[Out], Context), (Try[Out], Context)]] {

  import settings._

  private val in1 = Inlet[(In, Context)]("RetryBidi.in1")
  private val out1 = Outlet[(In, Context)]("RetryBidi.out1")
  private val in2 = Inlet[(Try[Out], Context)]("RetryBidi.in2")
  private val out2 = Outlet[(Try[Out], Context)]("RetryBidi.out2")
  private val delayAsNanos = delay.toNanos
  private val precisionAsNanos = 10.milliseconds.toNanos // the linux timer precision
  private val timerName = "RetryStageTimer"
  private val nameSuffix = new AtomicInteger(-1)
  override val shape = BidiShape(in1, out1, in2, out2)

  require(max > 0, "maximum retry count must be positive")
  require(delay == Duration.Zero || delayAsNanos > precisionAsNanos, "Delay must be greater than timer precision")
  require(exponentialBackoffFactor >= 0.0, "backoff factor must be >= 0.0")
  require(maxDelay == Duration.Zero || maxDelay >= delay, "maxDelay must be larger than delay")

  val uniqueId: Context => Any = uniqueIdMapper.getOrElse{
    context => context match {
      case uniqueIdProvider: UniqueId.Provider => uniqueIdProvider.uniqueId
      case uniqueId => uniqueId
    }
  }

  private[streams] val isFailure = failureDecider.getOrElse((e: Try[Out]) => e.isFailure)

  /*
     It keeps two internal structures:
       - Registry: Keeps track of every element passing through, along with the current retry count for each element.
       - Retry Queue: A queue of elements that needs to be retried.  Prioritized by soonest retry time.

     If there is demand on out1 and retry queue is not empty, but head of the queue is not ready to be pushed down yet
     (because of the delay):
       - a timer is scheduled.
       - if the current registry size is less than internal buffer size:
         - grab and push the element at in1 to downstream if available.
         - otherwise, demand element from upstream if not done so yet.

     Whenever the element at the head of the retry queue is pushed to downstream, the timer, if active,
     is canceled.  Because, the active timer is for the element which is just pushed down.  Once the timer is canceled,
     there is no need to schedule one for the new head of the retry queue until a demand is received on out1 (because
     the head cannot be pushed down when the timer fires off until there is demand).
   */
  // scalastyle:off method.length
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new TimerGraphStageLogic(shape)
    with StageLogging {

    val retryQMaxSize = maxWaitingRetries.getOrElse {
      inheritedAttributes.mandatoryAttribute[InputBuffer].max
    }
    private val matNumber = nameSuffix.incrementAndGet
    private val uniqueRetryName = if (matNumber == 0) name else s"$name-$matNumber"
    val retryMetrics = metricRegistry.map(registry =>
      RetryMetricsImpl(
        max,
        delay,
        exponentialBackoffFactor,
        maxDelay,
        retryQMaxSize,
        uniqueRetryName,
        registry,
        () => retryQ.size,
        () => retryRegistry.size)
    ).getOrElse(NoOpRetryMetrics)

    implicit val elementPriority: Ordering[(Long, Any)] = Ordering.by[(Long, Any), Long](e => e._1).reverse
    private val retryQ: mutable.PriorityQueue[(Long, Any)] = mutable.PriorityQueue.empty

    // A registry of all in-flight elements (including failed ones) with retry counts
    private val retryRegistry = mutable.HashMap.empty[Any, (In, Context, Int)]
    private val noDelay = delay == Duration.Zero
    private def upstreamFinished = isClosed(in1)

    private def shouldPullIn1 = !hasBeenPulled(in1) && retryQ.size < retryQMaxSize && !upstreamFinished
    private def hasBackpressuredIn1 = isAvailable(out1) && !hasBeenPulled(in1) && !upstreamFinished

    private def completeStageIfFinished() = if (retryRegistry.isEmpty && upstreamFinished) completeStage()

    private def isHeadReady: Boolean = noDelay || retryQ.head._1 <= System.nanoTime()

    private def grabAndPush() = {
      val (elem, ctx) = grab(in1)
      retryRegistry.put(uniqueId(ctx), (elem, ctx, 0))
      push(out1, (elem, ctx))
      // If a timer is active, do not cancel it here.  When the next demand comes with onPull(out1), the head of the
      // retry queue will be checked to see if it is ok to be pushed downstream.  If it is not, then a timer would
      // need to be scheduled anyway.  So, by not canceling here, we prevent a possible "cancel & schedule".
    }

    setHandler(in1, new InHandler {
      override def onPush(): Unit = if (isAvailable(out1)) grabAndPush()

      override def onUpstreamFinish(): Unit = if (retryRegistry.isEmpty) completeStage()

      override def onUpstreamFailure(ex: Throwable): Unit = if (retryRegistry.isEmpty) fail(out1, ex) else failStage(ex)
    })

    setHandler(out1, handler = new OutHandler {
      override def onPull(): Unit = {
        if (retryQ.nonEmpty && isHeadReady) {
          val (elem, ctx, _) = retryRegistry(retryQ.dequeue()._2)
          push(out1, (elem, ctx))
          // If a timer is active, that would be for the element we just pushed down.  So, not valid anymore.
          // Also, if the onTimer is called, cannot push down until a demand is created with onPull.
          cancelTimer(timerName)
        } else {
          if (isAvailable(in1)) grabAndPush()
          else {
            if (shouldPullIn1) pull(in1)
            // If the head is not ready yet, while there is a demand, we should schedule a timer.
            if (!noDelay && retryQ.nonEmpty && !isTimerActive(timerName)) scheduleOnce(timerName, remainingDelay)
          }
        }
      }

      override def onDownstreamFinish(cause: Throwable): Unit = {
        if (retryRegistry.isEmpty) completeStage() else cancel(in1)
        super.onDownstreamFinish(cause)
      }
    })

    setHandler(in2, handler = new InHandler {
      override def onPush(): Unit = {
        val (elem, context) = grab(in2)
        val key = uniqueId(context)

        // Only the first case triggers a retry.  Everything else is a push upstream.
        retryRegistry.get(key) match {
          case Some(retryEntry) if isFailure(elem) && retryEntry._3 < max =>
            retryMetrics.markRetry()
            val retryTime = System.nanoTime + delayTime(incrementAndGetRetryCount(key))
            val shouldRetryBeforeTheHeadOfQueue = retryQ.headOption.exists(_._1 - retryTime >= precisionAsNanos)
            if (shouldRetryBeforeTheHeadOfQueue) cancelTimer(timerName) // Because, the next retry time just changed

            retryQ.enqueue((retryTime, key))

            if (isAvailable(out1)) {
              if (isHeadReady) {
                val (elem, ctx, _) = retryRegistry(retryQ.dequeue()._2)
                push(out1, (elem, ctx))
                // If a timer is active, it is for the element which we just pushed, so not valid anymore.
                // Also, we do not need a timer until a demand from out1 comes with onPull.
                cancelTimer(timerName)
              } else if (!noDelay && !isTimerActive(timerName)) {
                // If the head of the queue has just changed but not pushed down, we need to schedule a new timer.
                scheduleOnce(timerName, remainingDelay)
              }
            }
            pull(in2)
          case _ =>
            if (elem.isSuccess) retryMetrics.markSuccess() else retryMetrics.markFailure()
            if (retryRegistry.remove(key).isEmpty) {
              log.error("The Context returned from downstream could not be matched to a Context that this Retry Stage " +
                "is tracking. At least 1 element may not be retried, and a memory leak is possible. Please see " +
                "section of Squbs Retry Stage documentation on Context to Unique Id Mapping.")
            }

            // If a demand from out1 was not propagated to in1 because of retryQ size earlier
            if (hasBackpressuredIn1) pull(in1)

            if (isAvailable(out2)) {
              push(out2, (elem, context))
              completeStageIfFinished()
            } else {
              // This branch should never get executed unless there is a bug.
              val elemDesc = elem.map(_ => "successful").getOrElse("exhausted")  // sucks to do this twice:(
              log.error(s"out2 is not available for push.  Dropping $elemDesc element")
            }
        }
      }

      override def onUpstreamFailure(ex: Throwable): Unit = if (retryQ.isEmpty) fail(out2, ex)
    })

    setHandler(out2, new OutHandler {
      override def onPull(): Unit =
        if (retryRegistry.isEmpty && upstreamFinished) completeStage()
        else pull(in2)
    })

    final override def onTimer(key: Any): Unit = {
      if (isAvailable(out1)) {
        val (elem, ctx, _) = retryRegistry(retryQ.dequeue()._2)
        push(out1, (elem, ctx))
      }
      // else element will be pushed when the demand arrives with the next onPull
    }

    private def incrementAndGetRetryCount(key: Any) = {
      val (elem, context, retryCount) = retryRegistry(key)
      val newRetryCount = retryCount + 1
      retryRegistry.update(key, (elem, context, newRetryCount))
      newRetryCount
    }

    private def remainingDelay = FiniteDuration(retryQ.head._1 - System.nanoTime(), NANOSECONDS)

    private def delayTime(retry: Long): Long = {
      // each retry delay will be delay duration * { backoff factor }
      // backoffFactor is (N ^ expbackOffFactor ) up to maxdelay (if one is specified)
      // E.g with a delay duration of 200ms and exponentialbackoff of 1.5
      //     retry,   delay * backoff factor = internal
      //       1        200 * (1 ^ 1.5) =   200ms
      //       2        200 * (2 ^ 1.5) =   566ms
      //       3        200 * (3 ^ 1.5) =  1039ms
      //       4        200 * (4 ^ 1.5) =  1600ms
      //     ...                        = <maxDelay if one is specified>
      val backoffFactor = math.pow(retry.toDouble, exponentialBackoffFactor)
      val sleepTimeAsNanos = (delayAsNanos * backoffFactor).toLong
      if (maxDelay != Duration.Zero) math.min(sleepTimeAsNanos, maxDelay.toNanos)
      else sleepTimeAsNanos
    }
  }
  // scalastyle:on method.length

  override def toString: String = "RetryBidi"

}

/**
  * A Retry Settings class for configuring a Retry
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
  * @param max     maximum number of retry attempts on any failures before giving up.
  * @param uniqueIdMapper function that maps [[Context]] to a unique id
  * @param failureDecider function to determine if an element passed by the joined [[Flow]] is
  *                       actually a failure or not
  * @param delay            to delay between retrying each failed element.
  * @param exponentialBackoffFactor exponential amount the delay duration will be increased upon each retry
  * @param maxDelay maximum delay duration for retry.
  * @param maxWaitingRetries maximum number of elements waiting to be retried.  Defaults to stream internal buffer size.
  * @tparam In      the type of elements pulled from upstream along with the [[Context]]
  * @tparam Out     the type of the elements that are pushed to downstream along with the [[Context]]
  * @tparam Context the type of the context that is carried along with the elements.
  * @return a [[RetrySettings]] with specified values
  */
case class RetrySettings[In, Out, Context] private[streams](
  max: Int,
  uniqueIdMapper: Option[Context => Any] = None,
  failureDecider: Option[Try[Out] => Boolean] = None,
  delay: FiniteDuration = Duration.Zero,
  exponentialBackoffFactor: Double = 0.0,
  maxDelay: FiniteDuration = Duration.Zero,
  maxWaitingRetries: Option[Int] = None,
  name: String = "Retry",
  metricRegistry: Option[MetricRegistry] = None) {

  def withUniqueIdMapper(uniqueIdMapper: Context => Any): RetrySettings[In, Out, Context] =
    copy(uniqueIdMapper = Some(uniqueIdMapper))

  def withFailureDecider(failureDecider: Try[Out] => Boolean): RetrySettings[In, Out, Context] =
    copy(failureDecider = Some(failureDecider))

  def withDelay(delay: FiniteDuration): RetrySettings[In, Out, Context] =
    copy(delay = delay)

  def withExponentialBackoff(exponentialBackoffFactor: Double): RetrySettings[In, Out, Context] =
    copy(exponentialBackoffFactor = exponentialBackoffFactor)

  def withMaxDelay(maxDelay: FiniteDuration): RetrySettings[In, Out, Context] =
    copy(maxDelay = maxDelay)

  def withMaxWaitingRetries(max: Int): RetrySettings[In, Out, Context] = copy(maxWaitingRetries = Some(max))

  def withMetrics(name: String)(implicit system: ActorSystem): RetrySettings[In, Out, Context] =
    copy(name = name, metricRegistry = Some(MetricsExtension.get(system).metrics))

  /**
    * Java API
    */
  def withFailureDecider(failureDecider: JFunction[Try[Out], JBoolean]): RetrySettings[In, Out, Context] =
    copy(failureDecider = Some((out: Try[Out]) => failureDecider(out).asInstanceOf[Boolean]))
}

object RetrySettings {
  /**
    * Creates a [[RetrySettings]] with default values that can be used to create a RetryBidi
    *
    * @param maxRetries the maximum number of retry attempts on any failures before giving up.
    * @tparam In Input type of [[Retry]]
    * @tparam Out Output type of [[Retry]]
    * @tparam Context the context type in [[Retry]]
    * @return a [[RetrySettings]] with default values
    */
  def apply[In, Out, Context](maxRetries: Int): RetrySettings[In, Out, Context] =
    new RetrySettings[In, Out, Context](maxRetries)

  /**
    * Java API
    *
    * Creates a [[RetrySettings]] with default values that can be used to create a RetryBidi
    *
    * @tparam In Input type of [[org.squbs.streams.Retry]]
    * @tparam Out Output type of [[org.squbs.streams.Retry]]
    * @tparam Context the carried content in [[org.squbs.streams.Retry]]
    * @return a [[RetrySettings]] with default values
    */
  def create[In, Out, Context](maxRetries: Integer): RetrySettings[In, Out, Context] =
    RetrySettings[In, Out, Context](maxRetries)
}

@MXBean
trait RetrySettingsMXBean {
  def getName: String
  def getMaxRetries: Int
  def getDelay: String
  def getMaxDelay: String
  def getExponentialBackoffFactor: Double
  def getMaxBufferSize: Int
}

private case class RetrySettingsMXBeanImpl(
  name: String,
  maxRetries: Int,
  delay: String,
  maxDelay: String,
  exponentialBackoffFactor: Double,
  maxBufferSize: Int) extends RetrySettingsMXBean {

  override def getName: String = name
  override def getMaxRetries: Int = maxRetries
  override def getDelay: String = delay
  override def getMaxDelay: String = maxDelay
  override def getExponentialBackoffFactor: Double = exponentialBackoffFactor
  override def getMaxBufferSize: Int = maxBufferSize
}


trait RetryMetrics {
  /**
    * Mark a failure element in Retry stage.
    */
  def markRetry(): Unit = {}

  /**
    * Mark a failing element that all retries were exhausted through Retry stage.
    */
  def markFailure(): Unit = {}

  /**
    * Mark a Success element passing trough Retry stage.
    */
  def markSuccess(): Unit = {}
}

object NoOpRetryMetrics extends RetryMetrics

case class RetryMetricsImpl private[streams](max: Int,
                                             delay: FiniteDuration,
                                             exponentialBackoffFactor: Double,
                                             maxDelay: FiniteDuration,
                                             maxWaitingRetries: Int,
                                             name: String,
                                             metricRegistry: MetricRegistry,
                                             retryQSize: () => Int,
                                             registrySize: () => Int)
  extends RetryMetrics {

  val RetryCount = s"$name.retry-count"
  val FailedCount = s"$name.failed-count" // count of exhausted retries
  val SuccessCount = s"$name.success-count"

  private val mBeanServer = ManagementFactory.getPlatformMBeanServer
  private val retrySettingsBeanName = new ObjectName(
    s"org.squbs.configuration:type=squbs.retry.settings,name=${ObjectName.quote(name)}")

  if (!mBeanServer.isRegistered(retrySettingsBeanName))
    mBeanServer.registerMBean(
      RetrySettingsMXBeanImpl(
        name,
        max,
        delay.toString,
        maxDelay.toString,
        exponentialBackoffFactor,
        maxWaitingRetries),
      retrySettingsBeanName)

  private val retryStateBeanName = new ObjectName(
    s"org.squbs.configuration:type=squbs.retry.state,name=${ObjectName.quote(name)}")

  if (!mBeanServer.isRegistered(retryStateBeanName))
    mBeanServer.registerMBean(RetryStateMXBeanImpl(name, retryQSize, registrySize), retryStateBeanName)

  override def markRetry(): Unit = metricRegistry.meter(RetryCount).mark()

  override def markFailure(): Unit = metricRegistry.meter(FailedCount).mark()

  override def markSuccess(): Unit = metricRegistry.meter(SuccessCount).mark()
}

@MXBean
trait RetryStateMXBean {
  def getName: String
  def getQueueSize: Int
  def getRegistrySize: Int
}

case class RetryStateMXBeanImpl(name: String,
                                retryQSize: () => Int,
                                registrySize: () => Int) extends RetryStateMXBean {
  override def getName: String = name
  override def getQueueSize: Int = retryQSize()
  override def getRegistrySize: Int = registrySize()
}
