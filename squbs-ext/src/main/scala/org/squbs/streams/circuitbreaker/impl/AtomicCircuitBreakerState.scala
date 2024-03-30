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

/**
  * Copyright (C) 2009-2016 Lightbend Inc. <http://www.lightbend.com>
  */
package org.squbs.streams.circuitbreaker.impl

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicLong}
import java.util.concurrent.TimeUnit

import org.apache.pekko.actor.{ActorSystem, Scheduler}
import org.apache.pekko.util.Unsafe
import com.codahale.metrics.MetricRegistry
import com.typesafe.config.Config
import org.squbs.streams.circuitbreaker._
import org.squbs.metrics.MetricsExtension

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import org.squbs.util.DurationConverters._

object AtomicCircuitBreakerState {

  /**
    * Create a new CircuitBreaker.
    *
    * Callbacks run in caller's thread when using withSyncCircuitBreaker, and in same ExecutionContext as the passed
    * in Future when using withCircuitBreaker. To use another ExecutionContext for the callbacks you can specify the
    * executor in the constructor.
    *
    * @param maxFailures Maximum number of failures before opening the circuit
    * @param callTimeout [[scala.concurrent.duration.FiniteDuration]] of time after which to consider a call a failure
    * @param resetTimeout [[scala.concurrent.duration.FiniteDuration]] of time after which to attempt to close the circuit
    * @param maxResetTimeout the upper bound of resetTimeout
    * @param exponentialBackoffFactor The exponential amount that the wait time will be increased
    */
  def apply(name: String,
            maxFailures: Int,
            callTimeout: FiniteDuration,
            resetTimeout: FiniteDuration,
            maxResetTimeout: FiniteDuration = 36500.days,
            exponentialBackoffFactor: Double = 1.0)
           (implicit executor: ExecutionContext, scheduler: Scheduler): CircuitBreakerState =
    new AtomicCircuitBreakerState(name, scheduler, maxFailures, callTimeout, resetTimeout, maxResetTimeout, exponentialBackoffFactor)

  /**
    * Create a new [[AtomicCircuitBreakerState]] from configuration.
    *
    * @param name The unique name of this circuit breaker instance.
    * @param config Configuration to look for the settings
    * @param system ActorSystem
    */
  def apply(name: String, config: Config)(implicit system: ActorSystem): CircuitBreakerState = {

    val configWithDefaults = config.withFallback(system.settings.config.getConfig("squbs.circuit-breaker"))

    apply(name,
      configWithDefaults.getInt("max-failures"),
      Duration(configWithDefaults.getString("call-timeout")).asInstanceOf[FiniteDuration],
      Duration(configWithDefaults.getString("reset-timeout")).asInstanceOf[FiniteDuration],
      Duration(configWithDefaults.getString("max-reset-timeout")).asInstanceOf[FiniteDuration],
      configWithDefaults.getDouble("exponential-backoff-factor")
    )(system.dispatcher, system.scheduler)
      .withMetricRegistry(MetricsExtension(system).metrics)
  }

  /**
    * Java API
    *
    * Create a new [[AtomicCircuitBreakerState]].
    *
    * @param maxFailures Maximum number of failures before opening the circuit
    * @param callTimeout [[scala.concurrent.duration.FiniteDuration]] of time after which to consider a call a failure
    * @param resetTimeout [[scala.concurrent.duration.FiniteDuration]] of time after which to attempt to close the circuit
    */
  def create(name: String,
             maxFailures: Int,
             callTimeout: java.time.Duration,
             resetTimeout: java.time.Duration,
             executor: ExecutionContext,
             scheduler: Scheduler): CircuitBreakerState =
    apply(name, maxFailures, toScala(callTimeout), toScala(resetTimeout))(executor, scheduler)

  /**
    * Java API
    *
    * Create a new [[AtomicCircuitBreakerState]] with exponential backoff strategy.
    *
    * The `resetTimeout` will be increased exponentially for each failed attempt to close the circuit.
    *
    * @param maxFailures Maximum number of failures before opening the circuit
    * @param callTimeout [[scala.concurrent.duration.FiniteDuration]] of time after which to consider a call a failure
    * @param resetTimeout [[scala.concurrent.duration.FiniteDuration]] of time after which to attempt to close the circuit
    * @param maxResetTimeout the upper bound of resetTimeout
    * @param exponentialBackoffFactor The exponential amount that the wait time will be increased
    */
  def create(name: String,
             maxFailures: Int,
             callTimeout: java.time.Duration,
             resetTimeout: java.time.Duration,
             maxResetTimeout: java.time.Duration,
             exponentialBackoffFactor: Double,
             executor: ExecutionContext,
             scheduler: Scheduler): CircuitBreakerState =
    apply(name, maxFailures, toScala(callTimeout), toScala(resetTimeout), toScala(maxResetTimeout),
      exponentialBackoffFactor)(executor, scheduler)

  /**
    * Java API
    *
    * Create a new Circuit Breaker from configuration.
    *
    * @param name The unique name of this circuit breaker instance.
    * @param config Configuration to look for the settings
    * @param system ActorSystem
    */
  def create(name: String, config: Config, system: ActorSystem): CircuitBreakerState =
    apply(name, config)(system)
}

/**
  * A [[CircuitBreakerState]] implementation based on `Atomic` variables.
  *
  * @param name used to differentiate circuit breakers in metrics
  * @param scheduler used to schedule reset attempt
  * @param maxFailures maximum number of failures before opening the circuit
  * @param callTimeout [[scala.concurrent.duration.FiniteDuration]] of time after which to consider a call a failure
  * @param resetTimeout [[scala.concurrent.duration.FiniteDuration]] of time after which to attempt to close the circuit
  * @param maxResetTimeout the upper bound of resetTimeout
  * @param exponentialBackoffFactor The exponential amount that the wait time will be increased
  * @param executor [[scala.concurrent.ExecutionContext]] used for execution of the scheduler
  */
class AtomicCircuitBreakerState(val name: String,
                                scheduler: Scheduler,
                                val maxFailures: Int,
                                val callTimeout: FiniteDuration,
                                val resetTimeout: FiniteDuration,
                                val maxResetTimeout: FiniteDuration,
                                val exponentialBackoffFactor: Double,
                                val metricRegistry:       MetricRegistry = new MetricRegistry())
                               (implicit executor: ExecutionContext)
  extends AbstractAtomicCircuitBreakerLogic with CircuitBreakerState {

  require(exponentialBackoffFactor >= 1.0, "factor must be >= 1.0")

  /**
    * @inheritdoc
    */
  def withMetricRegistry(metricRegistry: MetricRegistry): AtomicCircuitBreakerState = {
    new AtomicCircuitBreakerState(
      name,
      scheduler,
      maxFailures,
      callTimeout,
      resetTimeout,
      maxResetTimeout,
      exponentialBackoffFactor,
      metricRegistry)(executor)
  }

  /**
    * Holds reference to current state of CircuitBreaker - *access only via helper methods*
    */
  @volatile
  private[this] var _currentStateDoNotCallMeDirectly: AtomicState = AtomicClosed

  /**
    * Holds reference to current resetTimeout of CircuitBreaker - *access only via helper methods*
    */
  @volatile
  private[this] var _currentResetTimeoutDoNotCallMeDirectly: FiniteDuration = resetTimeout

  /**
    * Helper method for access to underlying state via Unsafe
    *
    * @param oldState Previous state on transition
    * @param newState Next state on transition
    * @return Whether the previous state matched correctly
    */
  @inline
  private[this] def swapAtomicState(oldState: AtomicState, newState: AtomicState): Boolean =
  Unsafe.instance.compareAndSwapObject(this, AbstractAtomicCircuitBreakerLogic.stateOffset, oldState, newState)

  /**
    * Helper method for accessing underlying state via Unsafe
    *
    * @return Reference to current state
    */
  @inline
  private[this] def currentAtomicState: AtomicState =
  Unsafe.instance.getObjectVolatile(this, AbstractAtomicCircuitBreakerLogic.stateOffset).asInstanceOf[AtomicState]

  /**
    * Helper method for updating the underlying resetTimeout via Unsafe
    */
  @inline
  private[this] def swapResetTimeout(oldResetTimeout: FiniteDuration, newResetTimeout: FiniteDuration): Boolean =
  Unsafe.instance.compareAndSwapObject(this, AbstractAtomicCircuitBreakerLogic.resetTimeoutOffset, oldResetTimeout, newResetTimeout)

  /**
    * Helper method for accessing to the underlying resetTimeout via Unsafe
    */
  @inline
  private[this] def currentResetTimeout: FiniteDuration =
  Unsafe.instance.getObjectVolatile(this, AbstractAtomicCircuitBreakerLogic.resetTimeoutOffset).asInstanceOf[FiniteDuration]

  /**
    * Mark a successful call through CircuitBreaker.
    */
  def succeeds(): Unit = currentAtomicState.succeeds()

  /**
    * Mark a failed call through CircuitBreaker.
    */
  def fails(): Unit = currentAtomicState.fails()

  /**
    * Check if circuit should be short circuited.
    */
  def isShortCircuited: Boolean = currentAtomicState.isShortCircuited

  /**
    * Get the current state of the Circuit Breaker.
    */
  def currentState = mapFromAtomicStateToState(currentAtomicState)

  private val mapToAtomicState = Map(
    Closed -> AtomicClosed,
    Open -> AtomicOpen,
    HalfOpen -> AtomicHalfOpen
  )

  private val mapFromAtomicStateToState: Map[AtomicState, State] = mapToAtomicState.map(_.swap)

  /**
    * Implements consistent transition between states. Throws IllegalStateException if an invalid transition is attempted.
    *
    * @param fromState State being transitioning from
    * @param toState State being transitioning from
    */
  override def transitionImpl(fromState: State, toState: State): Boolean = {
    val fromAtomicState = mapToAtomicState(fromState)
    val toAtomicState = mapToAtomicState(toState)
    val isTransitioned = swapAtomicState(fromAtomicState, toAtomicState)
    if (isTransitioned) toAtomicState.enter()
    // else some other thread already swapped state
    isTransitioned
  }

  /**
    * Internal state abstraction
    */
  private sealed trait AtomicState {

    /**
      * Check if circuit should be short circuited.
      *
      * @return
      */
    def isShortCircuited: Boolean

    /**
      * Invoked when call succeeds
      *
      */
    def succeeds(): Unit

    /**
      * Invoked when call fails
      *
      */
    def fails(): Unit

    /**
      * Invoked on the transitioned-to state during transition.
      */
    def enter(): Unit
  }

  /**
    * Concrete implementation of Closed state
    */
  private object AtomicClosed extends AtomicInteger with AtomicState {

    /**
      * Implementation of isShortCircuited, which simply returns false
      *
      * @return false
      */
    override def isShortCircuited: Boolean = false

    /**
      * On successful call, the failure count is reset to 0
      *
      * @return
      */
    override def succeeds(): Unit = set(0)

    /**
      * On failed call, the failure count is incremented.  The count is checked against the configured maxFailures, and
      * the breaker is tripped if we have reached maxFailures.
      *
      * @return
      */
    override def fails(): Unit = if (incrementAndGet() == maxFailures) tripBreaker(Closed)

    /**
      * On entry of this state, failure count and resetTimeout is reset.
      *
      * @return
      */
    override def enter(): Unit = {
      set(0)
      swapResetTimeout(currentResetTimeout, resetTimeout)
    }

    /**
      * Override for more descriptive toString
      *
      * @return
      */
    override def toString: String = "AtomicClosed with failure count = " + get()
  }

  /**
    * Concrete implementation of half-open state
    */
  private object AtomicHalfOpen extends AtomicBoolean(true) with AtomicState {

    /**
      * Allows a single call through, during which all other callers fail-fast.  If the call fails, the breaker reopens.
      * If the call succeeds the breaker closes.
      *
      * @return true if already returned false once.
      */
    override def isShortCircuited: Boolean = !compareAndSet(true, false)

    /**
      * Reset breaker on successful call.
      *
      * @return
      */
    override def succeeds(): Unit = resetBreaker()

    /**
      * Reopen breaker on failed call.
      *
      * @return
      */
    override def fails(): Unit = tripBreaker(HalfOpen)

    /**
      * On entry, guard should be reset for that first call to get in
      *
      * @return
      */
    override def enter(): Unit = set(true)

    /**
      * Override for more descriptive toString
      *
      * @return
      */
    override def toString: String = "Half-AtomicOpen currently testing call for success = " + get()
  }

  /**
    * Concrete implementation of Open state
    */
  private object AtomicOpen extends AtomicLong with AtomicState {

    /**
      * Fail-fast on any invocation.
      *
      * @return true
      */
    override def isShortCircuited: Boolean = true

    /**
      * No-op for open, calls are never executed so cannot succeed or fail
      *
      * @return
      */
    override def succeeds(): Unit = ()

    /**
      * No-op for open, calls are never executed so cannot succeed or fail
      *
      * @return
      */
    override def fails(): Unit = ()

    /**
      * On entering this state, schedule an attempted reset via [[pekko.actor.Scheduler]] and store the entry time.
      *
      * @return
      */
    override def enter(): Unit = {
      set(System.nanoTime())

      scheduler.scheduleOnce(currentResetTimeout) {
        attemptReset()
      }
      val nextResetTimeout = currentResetTimeout * exponentialBackoffFactor match {
        case f: FiniteDuration => f
        case _                 => currentResetTimeout
      }

      if (nextResetTimeout < maxResetTimeout)
        swapResetTimeout(currentResetTimeout, nextResetTimeout)
    }

    /**
      * Override for more descriptive toString
      *
      * @return
      */
    override def toString: String = "AtomicOpen"
  }
}
