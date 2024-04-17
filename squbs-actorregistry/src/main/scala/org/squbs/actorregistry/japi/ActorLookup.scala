/*
 *  Copyright 2017-2017 PayPal
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.squbs.actorregistry.japi

import java.util.Optional
import java.util.concurrent.CompletionStage

import org.apache.pekko.actor.{ActorRef, ActorRefFactory}
import org.apache.pekko.util.Timeout
import org.squbs.actorregistry.{ActorLookup => SActorLookup}

import scala.compat.java8.FutureConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.duration._

/**
  * Java API: Factory for obtaining a lookup object.
  */
object ActorLookup {

  /**
    * Creates a reusable ActorLookup
    *
    * @param refFactory The ActorSystem or ActorContext
    * @return An ActorLookup object
    */
  def create(refFactory: ActorRefFactory): ActorLookup[AnyRef] =
    new ActorLookup(None, None, classOf[AnyRef])(refFactory)
}

/**
  * Java API: The reusable ActorLookup instance, only to be created from ActorLookup object.
 *
  * @param refFactory The ActorSystem or ActorContext
  */
class ActorLookup[T <: AnyRef] private[japi] (private[japi] val name: Option[String],
                                              private[japi] val requestType: Option[Class[_]],
                                              private[japi] val responseType: Class[T])
                                             (private[japi] implicit val refFactory: ActorRefFactory) {

  private def getLookup(msg: Any) =
    new SActorLookup(responseType, Some(msg.getClass), name, responseType != classOf[AnyRef])

  /**
    * Sends a message to an ActorRef
    *
    * @param msg The message to send
    * @param sender The sender's actor reference
    */
  def tell(msg: Any, sender: ActorRef): Unit = getLookup(msg).tell(msg, sender)

  /**
    * Sends a message to an ActorRef, obtaining a future for the response
 *
    * @param msg The message to send
    * @param timeout The response timeout, in milliseconds
    * @return The CompletionStage of the response
    */
  def ask(msg: Any, timeout: Long): CompletionStage[T] = ask(msg, timeout.milliseconds)

  /**
    * Sends a message to an ActorRef
    * @param msg The message to send
    * @param timeout The response timeout
    * @return The CompletionStage of the response
    */
  def ask(msg: Any, timeout: Timeout): CompletionStage[T] = getLookup(msg).ask(msg)(timeout, refFactory).toJava

  def resolveOne(timeout: FiniteDuration): CompletionStage[ActorRef] =
    new SActorLookup(responseType, requestType, name, responseType != classOf[AnyRef]).resolveOne(timeout).toJava

  /**
    * Creates an ActorLookup based on a response type
    *
    * @param responseType The class representing the response type
    * @tparam U The response type for the lookup
    * @return The ActorLookup looking up matching the response type
    */
  def lookup[U <: AnyRef](responseType: Class[U]): ActorLookup[U] =
    new ActorLookup(None, None, responseType)(refFactory)

  /**
    * Creates an ActorLookup looking up an actor by name.
    *
    * @param name The actor name, as registered
    * @return The ActorLookup matching the actor name
    */
  def lookup(name: String): ActorLookup[AnyRef] =
    new ActorLookup(Some(name), None, classOf[AnyRef])(refFactory)

  /**
    * Creates an ActorLookup looking up an actor by both name and response type.
    *
    * @param name The actor name, as registered
    * @param responseType The class representing the response type
    * @tparam U The response type for the lookup
    * @return The ActorLookup matching the name and the response type
    */
  def lookup[U <: AnyRef](name: String, responseType: Class[U]): ActorLookup[U] =
    new ActorLookup(Some(name), None, responseType)(refFactory)


  /**
    * Creates an ActorLookup looking up an actor by name.
    *
    * @param name The actor name, if any, as registered
    * @return The ActorLookup matching the actor name
    */
  def lookup(name: Optional[String]): ActorLookup[AnyRef] =
    new ActorLookup(name.asScala, None, classOf[AnyRef])(refFactory)

  /**
    * Creates an ActorLookup looking up an actor by both name and response type.
    *
    * @param name The actor name, if any, as registered
    * @param responseType The class representing the response type
    * @tparam U The response type for the lookup
    * @return The ActorLookup matching the name and the response type
    */
  def lookup[U <: AnyRef](name: Optional[String], responseType: Class[U]): ActorLookup[U] =
    new ActorLookup(name.asScala, None, responseType)(refFactory)

  def lookup[U <: AnyRef](name: Optional[String], requestType: Optional[Class[_]],
                          responseType: Class[U]): ActorLookup[U] =
    new ActorLookup(name.asScala, requestType.asScala, responseType)(refFactory)
}
