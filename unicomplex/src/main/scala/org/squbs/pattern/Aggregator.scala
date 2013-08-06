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

  /**
   * Adds the partial function to the receive set, to be removed on first match.
   * @param fn The receive function.
   * @return The same receive function.
   */
  def expectOnce(fn: Actor.Receive) = {
    expectList += fn
    fn
  }

  /**
   * Adds the partial function to the receive set and keeping it in the receive set till removed.
   * @param fn The receive function.
   * @return The same receive function.
   */
  def expect(fn: Actor.Receive) = {
    implicit val permanent = true
    expectList += fn
    fn
  }

  /**
   * Removes the partial function from the receive set.
   * @param fn The receive function.
   * @return True if the partial function is removed, false if not found.
   */
  def unexpect(fn: Actor.Receive) = {
    expectList -= fn
  }

  /**
   * Receive function for handling the aggregations.
   */
  def receive: Actor.Receive = {
    case msg if handleResponse(msg) => // already dealt with in handleResponse
  }

  def handleResponse(msg: Any) = {
    expectList process { fn =>
      var processed = true
      fn.applyOrElse(msg, (_: Any) => processed = false)
      processed
    }
  }
}

