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

import java.lang.management.ManagementFactory
import javax.management.{MXBean, ObjectName}

import org.apache.pekko.actor.ActorRef
import org.apache.pekko.event.{EventBus, SubchannelClassification}
import org.apache.pekko.util.Subclassification
import com.codahale.metrics.{Gauge, MetricRegistry}

import scala.concurrent.duration._

/**
  * Holds the state of the circuit breaker.  Transitions through three states:
  *
  * `Closed`: elements/requests/calls run through the main logic until the `maxFailures` count is reached.  If
  * `maxFailures` is reached, it transitions to `Open` state.
  *
  * `Open`:  Short circuits.  A [[scala.util.Failure]] or a fallback response is returned.  After `resetTimeout`, it
  * transitions to `HalfOpen` state.
  *
  * `HalfOpen`: the first element/request/call will be allowed through the main logic, if it succeeds the circuit
  * breaker will reset to `Closed` state.  If it fails, the circuit breaker will re-open to `Open` state.  All
  * elements/requests/calls beyond the first that execute while the first is running will fail-fast with
  * [[scala.util.Failure]] or a fallback response.
  *
  * An [[ActorRef]] can be subscribed to receive certain events, e.g., [[TransitionEvents]] to receive all state
  * transition events or specific state transition event like [[Open]].
  *
  * It collects metrics for success, failure and short-circuit counts.  Also, exposes the state as a gauge.
  */
trait CircuitBreakerState {

  private val eventBus = new CircuitBreakerEventBusImpl

  val metricRegistry: MetricRegistry
  val name: String
  val maxFailures: Int
  val callTimeout: FiniteDuration
  val resetTimeout: FiniteDuration
  val maxResetTimeout: FiniteDuration
  val exponentialBackoffFactor: Double

  private val SuccessCount = s"$name.circuit-breaker.success-count"
  private val FailureCount = s"$name.circuit-breaker.failure-count"
  private val ShortCircuitCount = s"$name.circuit-breaker.short-circuit-count"

  object StateGauge extends Gauge[State] {
    val MetricName = s"$name.circuit-breaker.state"
    override def getValue: State = currentState
  }

  if(!metricRegistry.getGauges.containsKey(StateGauge.MetricName))
    metricRegistry.register(s"$name.circuit-breaker.state", StateGauge)

  val mBeanServer = ManagementFactory.getPlatformMBeanServer
  val beanName = new ObjectName(
    s"org.squbs.configuration:type=squbs.circuitbreaker,name=${ObjectName.quote(name)}")
  if(!mBeanServer.isRegistered(beanName))
      mBeanServer.registerMBean(
        CircuitBreakerStateMXBeanImpl(
          name,
          this.getClass.getName,
          maxFailures,
          callTimeout,
          resetTimeout,
          maxResetTimeout,
          exponentialBackoffFactor),
        beanName)

  /**
    * Subscribe an [[ActorRef]] to receive events that it's interested in.
    *
    * @param subscriber [[ActorRef]] that would receive the events
    * @param to event types that this [[ActorRef]] is interested in
    * @return true if subscription is successful
    */
  def subscribe(subscriber: ActorRef, to: EventType): Boolean = {
    eventBus.subscribe(subscriber, to)
  }

  /**
    * The provided [[MetricRegistry]] will be used to register metrics
    *
    * @param metricRegistry the registry to use for codahale metrics
    */
  def withMetricRegistry(metricRegistry: MetricRegistry): CircuitBreakerState

  /**
    * Mark a successful element/response/call through CircuitBreaker.
    */
  final def markSuccess(): Unit = {
    metricRegistry.meter(SuccessCount).mark()
    succeeds()
  }

  /**
    * Mark a failed element/response/call through CircuitBreaker.
    */
  final def markFailure(): Unit = {
    metricRegistry.meter(FailureCount).mark()
    fails()
  }

  /**
    * Check if circuit should be short circuited.
    */
  final def checkAndMarkIfShortCircuit(): Boolean = {
    val shortCircuited = isShortCircuited
    if(shortCircuited) metricRegistry.meter(ShortCircuitCount).mark()
    shortCircuited
  }

  /**
    * Implementation specific success logic
    */
  protected def succeeds(): Unit

  /**
    * Implementation specific fail logic
    */
  protected def fails(): Unit

  /**
    * Implementation specific short circuit check
    *
    * @return true if short circuited
    */
  protected def isShortCircuited: Boolean

  /**
    * @return the current state
    */
  protected def currentState: State

  /**
    * Implements consistent transition between states. Throws IllegalStateException if an invalid transition is attempted.
    *
    * @param fromState State being transitioning from
    * @param toState   State being transitioning to
    */
  protected final def transition(fromState: State, toState: State): Unit =
    if(transitionImpl(fromState, toState)) eventBus.publish(CircuitBreakerEvent(toState, toState))

  /**
    * Implementation specific transition logic
    */
  protected def transitionImpl(fromState: State, toState: State): Boolean

  /**
    * Trips breaker to an open state.  This is valid from `Closed` or `HalfOpen` states.
    *
    * @param fromState State we're coming from (`Closed` or `HalfOpen`)
    */
  protected final def tripBreaker(fromState: State): Unit = transition(fromState, Open)

  /**
    * Resets breaker to `Closed` state.  This is valid from `HalfOpen` state only.
    *
    */
  protected final def resetBreaker(): Unit = transition(HalfOpen, Closed)

  /**
    * Attempts to reset breaker by transitioning to `HalfOpen` state.  This is valid from `Open` state only.
    *
    */
  protected final def attemptReset(): Unit = transition(Open, HalfOpen)

}

/**
  * Exception thrown when Circuit Breaker is open.
  *
  * @param msg Defaults to "Circuit Breaker is open; calls are failing fast"
  */
case class CircuitBreakerOpenException(msg: String = "Circuit Breaker is open; calls are failing fast!")
  extends Exception(msg)

sealed trait EventType
sealed trait TransitionEvent extends EventType

object TransitionEvents extends TransitionEvent {
  def instance = this
}

sealed trait State extends TransitionEvent

object Closed extends State {
  def instance = this
}

object HalfOpen extends State {
  def instance = this
}

object Open extends State {
  def instance = this
}

case class CircuitBreakerEvent(eventType: EventType, payload: Any)

class CircuitBreakerEventClassification extends Subclassification[EventType] {
  override def isEqual(x: EventType, y: EventType): Boolean =
    x == y

  override def isSubclass(x: EventType, y: EventType): Boolean =
    x match {
      case `y` => true
      case _ if x.isInstanceOf[TransitionEvent] && y == TransitionEvents => true
      case _ => false
    }
}

/**
  * Publishes the payload of the [[CircuitBreakerEvent]] when the event type of the
  * [[CircuitBreakerEvent]] matches with the one used during subscription.
  */
class CircuitBreakerEventBusImpl extends EventBus with SubchannelClassification {
  type Event = CircuitBreakerEvent
  type Classifier = EventType
  type Subscriber = ActorRef

  override protected val subclassification: Subclassification[Classifier] =
  new CircuitBreakerEventClassification

  override protected def classify(event: Event): Classifier = event.eventType

  override protected def publish(event: Event, subscriber: Subscriber): Unit = {
    subscriber ! event.payload
  }
}


@MXBean
trait CircuitBreakerStateMXBean {
  def getName: String
  def getImplementationClass: String
  def getMaxFailures: Int
  def getCallTimeout: String
  def getResetTimeout: String
  def getMaxResetTimeout: String
  def getExponentialBackoffFactor: Double
}

case class CircuitBreakerStateMXBeanImpl(
  name: String,
  implementationClass: String,
  maxFailures: Int,
  callTimeout: FiniteDuration,
  resetTimeout: FiniteDuration,
  maxResetTimeout: FiniteDuration,
  exponentialBackoffFactor: Double) extends CircuitBreakerStateMXBean {

  override def getName: String = name

  override def getImplementationClass: String = implementationClass

  override def getMaxFailures: Int = maxFailures

  override def getCallTimeout: String = callTimeout.toString

  override def getResetTimeout: String = resetTimeout.toString

  override def getMaxResetTimeout: String = maxResetTimeout.toString

  override def getExponentialBackoffFactor: Double = exponentialBackoffFactor
}