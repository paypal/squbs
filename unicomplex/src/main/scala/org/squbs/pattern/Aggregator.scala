/**
 * Copyright (c) 2013 eBay, Inc.
 * All rights reserved.
 *
 * Contributors:
 * asucharitakul
 */
package org.squbs.pattern

import akka.actor.Actor

/**
 * The aggregator is to be mixed into an actor for the aggregator behavior.
 */
trait Aggregator {
  this: Actor =>

  private val expectList = WorkList.empty[Actor.Receive]
  private var aggregating: Boolean = false

  /**
   * Sets a receive function to handle receives. The actor would use the "aggregate" receive function.
   * @param fn The receive function.
   */
  def expectResponse(fn: Actor.Receive) {
    if (!aggregating) {
      context.become(aggregate)
      aggregating = true
    }
    expectList += fn
  }

  /**
   * Receive function for handling the aggregations.
   */
  def aggregate: Receive = {
    case msg if handleResponse(msg) => // already dealt with in handleResponse
  }

  def handleResponse(msg: Any) = {
    expectList processAndRemove { fn =>
      var processed = true
      fn.applyOrElse(msg, (_: Any) => processed = false)
      processed
    }
  }
}

