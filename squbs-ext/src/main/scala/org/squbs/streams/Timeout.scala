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

import org.apache.pekko.NotUsed
import org.apache.pekko.http.org.squbs.util.JavaConverters._
import org.apache.pekko.japi.Pair
import org.apache.pekko.stream._
import org.apache.pekko.stream.scaladsl.{BidiFlow, Flow}
import org.apache.pekko.stream.stage._
import com.typesafe.scalalogging.LazyLogging
import org.squbs.streams.TimeoutBidi._
import org.squbs.util.DurationConverters

import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.concurrent.{TimeUnit, TimeoutException}
import java.util.function.Consumer
import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/**
  * A bidi [[GraphStageLogic]] that is used by [[TimeoutOrdered]] and [[Timeout]] to wrap flows to add
  * timeout functionality.
  *
  * Once an element is pushed from the wrapped flow (from fromWrapped), it first checks if the element is already
  * timed out.  If a timeout message has already been sent for that element to downstream, then the element from
  * the wrapped flow is dropped.
  *
  * A timer gets scheduled when there is a downstream demand that's not immediately addressed.  This is to make sure
  * that a timeout response is sent to the downstream when upstream cannot address the demand on time.
  *
  * Timer precision is at best 10ms to avoid unnecessary timer scheduling cycles
  *
  * {{{
  *        +------+
  *  In ~> |      | ~> In
  *        | bidi |
  * Out <~ |      | <~ FromWrapped
  *        +------+
  * }}}
  *
  * @param shape the [[BidiShape]] that the timeout logic is applied on
  * @tparam In the type of the elements that gets forwarded to the wrapped flow
  * @tparam FromWrapped the type of the elements that the wrapped flow sends back
  * @tparam Out the type of the elements that are pushed to downstream
  */
abstract class TimeoutGraphStageLogic[In, FromWrapped, Out](shape: BidiShape[In, In, FromWrapped, Out])
  extends TimerGraphStageLogic(shape) {

  private val in = shape.in1
  private val fromWrapped = shape.in2
  private val toWrapped = shape.out1
  private val out = shape.out2

  private[this] def timerName = "TimeoutGraphStateLogic"
  private val timeoutAsNanos = timeoutDuration.toNanos
  private val timeoutAsMillis = timeoutDuration.toMillis
  private val precision = 10.milliseconds.toNanos
  private var downstreamDemand = 0
  private var upstreamFinished = false

  protected def timeoutDuration: FiniteDuration

  protected def enqueueInTimeoutQueue (elem: In): Unit

  protected def onPushFromWrapped(elem: FromWrapped, isOutAvailable: Boolean): Option[Out]

  protected def onScheduledTimeout(): Option[Out]

  protected def onPullOut(): Option[Out]

  protected def isBuffersEmpty: Boolean

  protected def timeLeftForNextElemToTimeout: Long = {
    val firstElemTime = firstElemStartTime
    if (firstElemTime == 0) timeoutAsMillis
    else {
      val timeLeftInmillis = timeoutAsMillis - NANOSECONDS.toMillis(System.nanoTime() - firstElemTime)
      if (MILLISECONDS.toNanos(timeLeftInmillis) < precision) NANOSECONDS.toMillis(precision)
      else timeLeftInmillis
    }
  }

  protected def expirationTime: Long = System.nanoTime - timeoutAsNanos - precision

  protected def firstElemStartTime: Long

  setHandler(in, new InHandler {
    override def onPush(): Unit = {
      val elem = grab(in)
      enqueueInTimeoutQueue(elem)
      push(toWrapped, elem)
    }

    override def onUpstreamFinish(): Unit = complete(toWrapped)
    override def onUpstreamFailure(ex: Throwable): Unit = fail(toWrapped, ex)
  })

  setHandler(toWrapped, new OutHandler {
    override def onPull(): Unit = {
      pull(in)
    }
    override def onDownstreamFinish(cause: Throwable): Unit = {
      completeStage()
      super.onDownstreamFinish(cause)
    }
  })

  setHandler(fromWrapped, new InHandler {
    override def onPush(): Unit = {
      onPushFromWrapped(grab(fromWrapped), isAvailable(out)) foreach { elem =>
        push(out, elem)
      }
      if(downstreamDemand > 0) {
        pull(fromWrapped)
        downstreamDemand -= 1
      }
    }
    override def onUpstreamFinish(): Unit = {
      if(isBuffersEmpty) completeStage()
      else upstreamFinished = true
    }

    override def onUpstreamFailure(ex: Throwable): Unit = fail(out, ex)
  })

  setHandler(out, new OutHandler {
    override def onPull(): Unit = {
      if(!upstreamFinished || !isBuffersEmpty) {
        onPullOut() match {
          case Some(elem) => push(out, elem)
          case None => if (!isTimerActive(timerName)) scheduleOnce(timerName, timeLeftForNextElemToTimeout.millis)
        }

        if (!isClosed(fromWrapped) && !hasBeenPulled(fromWrapped)) pull(fromWrapped)
        else downstreamDemand += 1
      } else complete(out)
    }
    override def onDownstreamFinish(cause: Throwable): Unit = {
      cancel(fromWrapped)
      super.onDownstreamFinish(cause)
    }
  })

  final override def onTimer(key: Any): Unit = {
    if(!upstreamFinished || !isBuffersEmpty) {
      if (isAvailable(out)) {
        onScheduledTimeout() match {
          case Some(elem) => push(out, elem)
          case None => scheduleOnce(timerName, timeLeftForNextElemToTimeout.millis)
        }
      }
    } else complete(out)
  }
}

object Timeout {

  /**
    * Creates a [[BidiFlow]] that can be joined with a [[Flow]] to add timeout functionality.
    * This API is specifically for the flows that do not guarantee message ordering.  For flows that guarantee message
    * ordering, please use [[TimeoutOrdered]].
    *
    * @see [[TimeoutSettings]] for details about each parameter and type parameter.
    */
  def apply[In, Out, Context](settings: TimeoutSettings[In, Out, Context]):
  BidiFlow[(In, Context), (In, Context), (Out, Context), (Try[Out], Context), NotUsed] =
    BidiFlow.fromGraph(new Timeout(settings))

  def apply[In, Out, Context](timeout: FiniteDuration):
  BidiFlow[(In, Context), (In, Context), (Out, Context), (Try[Out], Context), NotUsed] =
    apply(TimeoutSettings[In, Out, Context](timeout))

  /**
    * Java API
    */
  def create[In, Out, Context](settings: TimeoutSettings[In, Out, Context]):
  javadsl.BidiFlow[Pair[In, Context], Pair[In, Context], Pair[Out, Context], Pair[Try[Out], Context], NotUsed] =
    toJava(apply(settings))

  /**
    * Java API
    */
  def create[In, Out, Context](timeout: java.time.Duration):
  javadsl.BidiFlow[Pair[In, Context], Pair[In, Context], Pair[Out, Context], Pair[Try[Out], Context], NotUsed] =
    toJava(apply[In, Out, Context](DurationConverters.toScala(timeout)))

}

/**
  * Timeout functionality requires each element to be uniquely identified, so it requires a [[Context]], of any type
  * defined by the application, to be carried along with the flow's input and output as a [[Tuple2]] (Scala) or
  * [[Pair]] (Java).  The requirement is that either the [[Context]] itself or a mapping from [[Context]] should be
  * able to uniquely identify an element.  Here is the ways how a unique id can be retrieved:
  *
  *   - [[Context]] itself is a type that can be used as a unique id, e.g., [[Int]], [[Long]], [[java.util.UUID]]
  *   - [[Context]] extends [[UniqueId.Provider]] and implements [[UniqueId.Provider.uniqueId]] method
  *   - [[Context]] is of type [[UniqueId.Envelope]]
  *   - [[Context]] can be mapped to a unique id by calling {{{uniqueIdMapper}}}
  *
  * @param timeout the duration after which the processing of an element would be considered timed out
  * @param uniqueIdMapper the function that maps [[Context]] to a unique id
  * @param cleanUp an optional clean up function to be applied on timed out elements when pushed
  * @tparam In the type of the elements pulled from the upstream along with the [[Context]]
  * @tparam Out the type of the elements that are pushed to downstream along with the [[Context]]
  * @tparam Context the type of the context that is carried around along with the elements.
  * @return a [[BidiFlow]] with timeout functionality
  */
case class TimeoutSettings[In, Out, Context] private(timeout: FiniteDuration,
                                                     uniqueIdMapper: Option[Context => Any] = None,
                                                     cleanUp: Out => Unit = (_: Out) => ()) {

  def withUniqueIdMapper(uniqueIdMapper: Context => Any): TimeoutSettings[In, Out, Context] =
    copy(uniqueIdMapper = Some(uniqueIdMapper))

  def withCleanUp(cleanUp: Consumer[Out]): TimeoutSettings[In, Out, Context] =
    copy(cleanUp = out => cleanUp.accept(out))
}

object TimeoutSettings {
  def apply[In, Out, Context](timeout: FiniteDuration): TimeoutSettings[In, Out, Context] = new TimeoutSettings(timeout)

  /**
    * Java API
    */
  def create[In, Out, Context](timeout: java.time.Duration): TimeoutSettings[In, Out, Context] =
    apply(FiniteDuration(timeout.toMillis, TimeUnit.MILLISECONDS))
}

/**
  * A bidi [[GraphStage]] that is joined with flows to add timeout functionality.  This bidi stage is used with flows
  * that do not guarantee the message ordering.  So, it requires a context to be carried along with the elements to
  * uniquely identify each element.
  *
  *
  * '''Emits when''' an element is available from the joined [[Flow]] or an element has already timed out
  *
  * '''Backpressures when''' the downstream backpressures
  *
  * '''Completes when''' upstream completes
  *
  * '''Cancels when''' downstream cancels
  *
  *
  * {{{
  *                        +------+
  *       (In, Context) ~> |      | ~> (In, Context)
  *                        | bidi |
  * (Try[Out], Context) <~ |      | <~ (Out, Context)
  *                        +------+
  * }}}
  *
  * @param settings @see [[TimeoutSettings]]
  * @tparam In the type of the elements pulled from the upstream along with the [[Context]]
  * @tparam Out the type of the elements that are pushed by the joined [[Flow]] along with the [[Context]].
  *             This then gets wrapped with a [[Try]] and pushed downstream with a [[Context]]
  * @tparam Context the type of the context that is carried around along with the elements.
  */
final class Timeout[In, Out, Context](settings: TimeoutSettings[In, Out, Context])
  extends GraphStage[BidiShape[(In, Context), (In, Context), (Out, Context), (Try[Out], Context)]] with LazyLogging {

  private val in = Inlet[(In, Context)]("TimeoutBidiUnordered.in")
  private val fromWrapped = Inlet[(Out, Context)]("TimeoutBidiUnordered.fromWrapped")
  private val toWrapped = Outlet[(In, Context)]("TimeoutBidiUnordered.toWrapped")
  private val out = Outlet[(Try[Out], Context)]("TimeoutBidiUnordered.out")
  val shape = BidiShape(in, toWrapped, fromWrapped, out)

  val uniqueId: Context => Any = settings.uniqueIdMapper.getOrElse{
    context => context match {
      case uniqueIdProvider: UniqueId.Provider => uniqueIdProvider.uniqueId
      case uniqueId => uniqueId
    }
  }

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new TimeoutGraphStageLogic(shape) {

    val timeouts = mutable.LinkedHashMap.empty[Any, (Context, Long)]
    val readyToPush = mutable.Queue[((Try[Out], Context), Long)]()

    override protected def timeoutDuration: FiniteDuration = settings.timeout

    override protected def enqueueInTimeoutQueue(elemWithContext: (In, Context)): Unit = {
      val (_, context) = elemWithContext
      timeouts.put(uniqueId(context), (context, System.nanoTime()))
    }

    override protected def onPushFromWrapped(fromWrapped: (Out, Context), isOutAvailable: Boolean): Option[(Try[Out], Context)] = {
      val (elem, context) = fromWrapped
      timeouts.remove(uniqueId(context)).fold(tryCleanUp(elem, settings.cleanUp)) { case (_, startTime) =>
        readyToPush.enqueue(((Success(elem), context), startTime))
      }

      if(isOutAvailable) pickNextElemToPush()
      else None
    }

    override protected def firstElemStartTime = timeouts.headOption map { case (_, (_, startTime)) => startTime } getOrElse 0

    private def pickNextElemToPush(): Option[(Try[Out], Context)] = {
      timeouts.headOption.filter { case(_, (_, firstElemStartTime)) =>
        firstElemStartTime < expirationTime &&
          !readyToPush.headOption.exists { case(_, readyToPushStartTime) =>
            readyToPushStartTime <= firstElemStartTime
          }
      } map { case(id, (context, _)) =>
        timeouts.remove(id)
        (Failure(FlowTimeoutException()), context)
      } orElse dequeueOption().map { case(elem, _) => elem }
    }

    private def dequeueOption(): Option[((Try[Out], Context), Long)] =
      if (readyToPush.nonEmpty) Some(readyToPush.dequeue())
      else None

    override protected def onPullOut() = pickNextElemToPush()

    override protected def onScheduledTimeout() = pickNextElemToPush()

    override protected def isBuffersEmpty = timeouts.isEmpty && readyToPush.isEmpty
  }

  override def initialAttributes = Attributes.name("TimeoutBidiUnordered")
  override def toString = "TimeoutBidiUnordered"
}

object TimeoutOrdered {
  /**
    * Creates a [[BidiFlow]] that can be joined with a [[Flow]] to add timeout functionality.
    * This API is specifically for the flows that guarantee message ordering.  For flows that do not guarantee message
    * ordering, please use [[Timeout]].
    *
    * @param timeout the duration after which the processing of an element would be considered timed out
    * @param cleanUp an optional clean up function to be applied on timed out elements when pushed
    * @tparam In the type of the elements pulled from the upstream
    * @tparam Out the type of the elements that are pushed to downstream
    * @return a [[BidiFlow]] with timeout functionality
    */
  def apply[In, Out](timeout: FiniteDuration, cleanUp: Out => Unit = (_: Out) => ()):
  BidiFlow[In, In, Out, Try[Out], NotUsed] =
    BidiFlow.fromGraph(new TimeoutOrdered(timeout, cleanUp))

  /**
    * Java API
    */
  def create[In, Out](timeout: java.time.Duration,
                      cleanUp: Consumer[Out]):
  org.apache.pekko.stream.javadsl.BidiFlow[In, In, Out, Try[Out], NotUsed] = {
    apply(DurationConverters.toScala(timeout), (out: Out) => cleanUp.accept(out)).asJava
  }

  /**
    * Java API
    * @param timeout
    * @tparam In
    * @tparam Out
    * @return
    */
  def create[In, Out](timeout: java.time.Duration):
  org.apache.pekko.stream.javadsl.BidiFlow[In, In, Out, Try[Out], NotUsed] = {
    apply(DurationConverters.toScala(timeout), (_: Out) => ()).asJava
  }
}

/**
  * A bidi [[GraphStage]] that is joined with flows to add timeout functionality.  This bidi stage is used with flows
  * that guarantee the message ordering.
  *
  * '''Emits when''' an element is available from the wrapped flow or an element has already timed out
  *
  * '''Backpressures when''' the downstream backpressures
  *
  * '''Completes when''' upstream completes
  *
  * '''Cancels when''' downstream cancels
  *
  * {{{
  *             +------+
  *       In ~> |      | ~> In
  *             | bidi |
  * Try[Out] <~ |      | <~ Out
  *             +------+
  * }}}
  *
  * @param timeout the duration after which the processing of an element would be considered timed out.
  * @param cleanUp an optional clean up function to be applied on timed out elements when pushed
  * @tparam In the type of the elements pulled from the upstream and pushed down to joined flow
  * @tparam Out the type of the elements that are pushed to downstream
  */
final class TimeoutOrdered[In, Out](timeout: FiniteDuration, cleanUp: Out => Unit) extends GraphStage[BidiShape[In, In, Out, Try[Out]]] {

  val in = Inlet[In]("TimeoutBidiOrdered.in")
  val fromWrapped = Inlet[Out]("TimeoutBidiOrdered.fromWrapped")
  val toWrapped = Outlet[In]("TimeoutBidiOrdered.toWrapped")
  val out = Outlet[Try[Out]]("TimeoutBidiOrdered.out")
  val shape = BidiShape(in, toWrapped, fromWrapped, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new TimeoutGraphStageLogic(shape) {

    val timeouts = mutable.Queue[TimeoutTracker]()

    override def timeoutDuration: FiniteDuration = timeout

    override def enqueueInTimeoutQueue(elem: In): Unit = timeouts.enqueue(TimeoutTracker(System.nanoTime(), false))

    override def onPushFromWrapped(elem: Out, isOutAvailable: Boolean): Option[Try[Out]] = {
      if (isOutAvailable && timeouts.nonEmpty) {
        if (timeouts.dequeue().isTimedOut) {
          tryCleanUp(elem, cleanUp)
          None
        } else Some(Success(elem))
      } else None
    }

    override def firstElemStartTime: Long = timeouts.find(!_.isTimedOut).map(_.startTime).getOrElse(0)

    override def onPullOut() = None

    override def onScheduledTimeout() = {
      timeouts.find(!_.isTimedOut).filter(_.startTime < expirationTime).map { elem =>
        elem.isTimedOut = true
        Failure(FlowTimeoutException())
      }
    }

    override def isBuffersEmpty = timeouts.isEmpty || timeouts.forall(_.isTimedOut == true)

  }

  override def initialAttributes = Attributes.name("TimeoutBidiOrdered")
  override def toString = "TimeoutBidiOrdered"

  case class TimeoutTracker(startTime: Long, var isTimedOut: Boolean)
}

/**
  * Exception thrown when an element times out.
  *
  * @param msg Defaults to "Flow timed out!"
  */
case class FlowTimeoutException(msg: String = "Flow timed out!") extends TimeoutException(msg)

private object TimeoutBidi {
  private[streams] def tryCleanUp[Out](elem: Out, cleanUp: Out => Unit): Unit = {
    Try(cleanUp(elem)).recover {
      case NonFatal(_) => ()
      case ex => throw ex
    }
  }
}
