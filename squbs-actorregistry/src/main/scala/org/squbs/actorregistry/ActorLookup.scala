/*
 *  Copyright 2015 PayPal
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

package org.squbs.actorregistry

import akka.actor._
import akka.pattern.AskSupport
import akka.util.Timeout

import scala.concurrent.{Promise, Future}
import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag
import scala.util.Success

object ActorLookup  {

  /**
   * Squbs API: Returns a ActorLookup instance that has default values except for responseClass which will be the supplied type
   */
  def apply[T <: AnyRef : ClassTag] = new ActorLookup(responseClass = Some(implicitly[ClassTag[T]].runtimeClass))

  /**
   * Squbs API: Returns a ActorLookup instance that has default values except for :
   *   responseClass which will be the supplied type
   *   actorName will be the supplied actorName
   */
  def apply[T <: AnyRef : ClassTag](actorName: String) = new ActorLookup(responseClass = Some(implicitly[ClassTag[T]].runtimeClass), actorName=Some(actorName))

  /**
   * Squbs API: Send msg with tell pattern to a corresponding actor based if the msg class type matching with an entry at Actor registry
   */
  def !(msg: Any)(implicit sender: ActorRef = Actor.noSender, system: ActorSystem) = tell(msg, sender)

  /**
   * Squbs API: Send msg with ask pattern to a corresponding actor based if the msg class type matching with an entry at Actor registry
   */
  def ?(message: Any)(implicit timeout: Timeout, system: ActorSystem): Future[Any] = ask(message)(timeout, system)

  /**
   * Squbs API: Send msg with tell pattern to a corresponding actor based if the msg class type matching with an entry at Actor registry
   */
  def tell(msg: Any, sender: ActorRef)(implicit system: ActorSystem) = (new ActorLookup).tell(msg, sender)

  /**
   * Squbs API: Send msg with ask pattern to a corresponding actor based if the msg class type matching with an entry at Actor registry
   */
  def ask(msg: Any)(implicit timeout: Timeout, system: ActorSystem ): Future[Any] =(new ActorLookup).ask(msg)(timeout, system)

}

case class ActorNotFound(actorLookup: ActorLookup) extends RuntimeException("Actor not found for: " + actorLookup)

/**
 * Construct an [[org.squbs.actorregistry.ActorLookup]] from the requestClass, responseClass, actor name
 */
 private[actorregistry] case class ActorLookup(requestClass: Option[Class[_]] = None, responseClass: Option[Class[_]]=None, actorName: Option[String] = None) extends AskSupport{

  /**
   * Squbs API: Send msg with tell pattern to a corresponding actor based if requestClass(msg's class type), responseClass, actorName matching with an entry at Actor registry
   */
  def !(msg: Any)(implicit sender: ActorRef = Actor.noSender, system: ActorSystem) = tell(msg, sender)

  /**
   * Squbs API: Send msg with ask pattern to a corresponding actor based if requestClass(msg's class type), responseClass, actorName matching with an entry at Actor registry
   */
  def ?(message: Any)(implicit timeout: Timeout, system: ActorSystem): Future[Any] = ask(message)(timeout, system)

  /**
   * Squbs API: Send msg with tell pattern to a corresponding actor based if requestClass(msg's class type), responseClass, actorName matching with an entry at Actor registry
   */
  def tell(msg: Any, sender: ActorRef)(implicit system: ActorSystem) =
    system.actorSelection(ActorRegistry.path).tell(ActorLookupMessage(copy(requestClass= Some(msg.getClass)), msg), sender)

  /**
   * Squbs API: Send msg with ask pattern to a corresponding actor based if requestClass(msg's class type), responseClass, actorName matching with an entry at Actor registry
   */
  def ask(msg: Any)(implicit timeout: Timeout, system: ActorSystem ): Future[Any] =
    system.actorSelection(ActorRegistry.path).ask(ActorLookupMessage(copy(requestClass= Some(msg.getClass)), msg))

  /**
   * Squbs API: return a ActorRef if there is requestClass(msg's class type), responseClass, actorName matching with an entry at Actor registry
   */
  def resolveOne(timeout: FiniteDuration)(implicit system: ActorSystem): Future[ActorRef] = resolveOne()(timeout, system)

  def resolveOne()(implicit timeout: Timeout, system: ActorSystem) : Future[ActorRef] = {
    val p = Promise[ActorRef]()
    this match {
      case ActorLookup(None, None, None) =>
        p.failure(org.squbs.actorregistry.ActorNotFound(this))
      case _ =>
        implicit val ec = system.dispatcher
        val fu = system.actorSelection(ActorRegistry.path).ask(ActorLookupMessage(this, Identify("ActorLookup"))).onComplete {
          case Success(ActorIdentity(_, Some(ref))) ⇒
            p.success(ref)
          case x ⇒
            p.failure(org.squbs.actorregistry.ActorNotFound(this))
        }
    }
    p.future
  }

}

