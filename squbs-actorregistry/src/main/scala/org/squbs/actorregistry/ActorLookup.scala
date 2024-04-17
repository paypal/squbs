/*
 *  Copyright 2017 PayPal
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

import org.apache.pekko.actor._
import org.apache.pekko.pattern.AskSupport
import org.apache.pekko.util.Timeout

import scala.concurrent.{ExecutionContext, Promise, Future}
import scala.concurrent.duration.FiniteDuration
import scala.language.existentials
import scala.reflect.ClassTag
import scala.util.Success

object ActorLookup {

  /**
   * Squbs API: Returns a ActorLookup instance that has default values except for responseClass
   * which will be the supplied type
   */
  def apply[T: ClassTag] = {
    val responseClass = implicitly[ClassTag[T]].runtimeClass
    new ActorLookup(responseClass = responseClass, explicitType = responseClass != classOf[Any])
  }

  /**
   * Squbs API: Returns a ActorLookup instance that has default values except for :
   *   responseClass which will be the supplied type
   *   actorName will be the supplied actorName
   */
  def apply[T: ClassTag](actorName: String) = {
    val responseClass = implicitly[ClassTag[T]].runtimeClass
    new ActorLookup(responseClass = responseClass, actorName=Some(actorName),
      explicitType = responseClass != classOf[Any])
  }

  def apply() = new ActorLookup(classOf[Any], None, None, explicitType = false)

  def apply(requestClass: Class[_]) = new ActorLookup(classOf[Any], Option(requestClass), None, explicitType = false)

  def apply(requestClass: Option[Class[_]], actorName: Option[String]) =
    new ActorLookup(classOf[Any], requestClass, actorName, explicitType = false)

  /**
   * Squbs API: Send msg with tell pattern to a corresponding actor based if the msg class type matching with an
   * entry at Actor registry
   */
  def !(msg: Any)(implicit sender: ActorRef = Actor.noSender, refFactory: ActorRefFactory) = tell(msg, sender)

  /**
   * Squbs API: Send msg with ask pattern to a corresponding actor based if the msg class type
   * matching with an entry at Actor registry
   */
  def ?(message: Any)(implicit timeout: Timeout, refFactory: ActorRefFactory): Future[Any] =
    ask(message)(timeout, refFactory)

  /**
   * Squbs API: Send msg with tell pattern to a corresponding actor based if the msg class type matching with an
   * entry at Actor registry.
   */
  def tell(msg: Any, sender: ActorRef)(implicit refFactory: ActorRefFactory) = ActorLookup().tell(msg, sender)

  /**
   * Squbs API: Send msg with ask pattern to a corresponding actor based if the msg class type matching with an
   * entry at Actor registry.
   */
  def ask(msg: Any)(implicit timeout: Timeout, refFactory: ActorRefFactory): Future[Any] =
    ActorLookup().ask(msg)(timeout, refFactory)

}

case class ActorNotFound(actorLookup: ActorLookup[_]) extends RuntimeException("Actor not found for: " + actorLookup)

/**
 * Construct an [[org.squbs.actorregistry.ActorLookup]] from the requestClass, responseClass, actor name
 */
 private[actorregistry] case class ActorLookup[T](responseClass: Class[T],
                                                  requestClass: Option[Class[_]] = None,
                                                  actorName: Option[String] = None,
                                                  explicitType: Boolean = false) extends AskSupport{

  /**
   * Squbs API: Send msg with tell pattern to a corresponding actor based if requestClass(msg's class type),
   * responseClass, actorName matching with an entry at Actor registry
   */
  def !(msg: Any)(implicit sender: ActorRef = Actor.noSender, refFactory: ActorRefFactory) = tell(msg, sender)

  /**
   * Squbs API: Send msg with ask pattern to a corresponding actor based if requestClass(msg's class type),
   * responseClass, actorName matching with an entry at Actor registry
   */
  def ?(message: Any)(implicit timeout: Timeout, refFactory: ActorRefFactory): Future[T] =
    ask(message)(timeout, refFactory)

  /**
   * Squbs API: Send msg with tell pattern to a corresponding actor based if requestClass(msg's class type),
   * responseClass, actorName matching with an entry at Actor registry
   */
  def tell(msg: Any, sender: ActorRef)(implicit refFactory: ActorRefFactory) =
    refFactory.actorSelection(ActorRegistry.path)
      .tell(ActorLookupMessage(copy(requestClass= Some(msg.getClass)), msg), sender)

  /**
   * Squbs API: Send msg with ask pattern to a corresponding actor based if requestClass(msg's class type),
   * responseClass, actorName matching with an entry at Actor registry
   */
  def ask(msg: Any)(implicit timeout: Timeout, refFactory: ActorRefFactory ): Future[T] = {
    val f = refFactory.actorSelection(ActorRegistry.path)
      .ask(ActorLookupMessage(copy(requestClass = Some(msg.getClass)), msg))
    import refFactory.dispatcher
    mapFuture(f, responseClass)
  }

  private def mapFuture[U](f: Future[Any], responseType: Class[U])(implicit ec: ExecutionContext): Future[U] = {
    val boxedClass = {
      if (responseType.isPrimitive) toBoxed(responseType) else responseType
    }
    require(boxedClass ne null)
    f.map(v => boxedClass.cast(v).asInstanceOf[U])
  }

  private val toBoxed = Map[Class[_], Class[_]](
    classOf[Boolean] -> classOf[java.lang.Boolean],
    classOf[Byte]    -> classOf[java.lang.Byte],
    classOf[Char]    -> classOf[java.lang.Character],
    classOf[Short]   -> classOf[java.lang.Short],
    classOf[Int]     -> classOf[java.lang.Integer],
    classOf[Long]    -> classOf[java.lang.Long],
    classOf[Float]   -> classOf[java.lang.Float],
    classOf[Double]  -> classOf[java.lang.Double],
    classOf[Unit]    -> classOf[scala.runtime.BoxedUnit]
  )



  /**
   * Squbs API: return a ActorRef if there is requestClass(msg's class type), responseClass, actorName matching
   * with an entry at Actor registry
   */
  def resolveOne(timeout: FiniteDuration)(implicit refFactory: ActorRefFactory): Future[ActorRef] =
    resolveOne(timeout, refFactory)

  def resolveOne(implicit timeout: Timeout, refFactory: ActorRefFactory) : Future[ActorRef] = {
    val p = Promise[ActorRef]()
    this match {
      case ActorLookup(_, None, None, false) =>
        p.failure(org.squbs.actorregistry.ActorNotFound(this))
      case _ =>
        import refFactory.dispatcher
        refFactory.actorSelection(ActorRegistry.path) ? ActorLookupMessage(this, Identify("ActorLookup")) onComplete {
          case Success(ActorIdentity(_, Some(ref))) =>
            p.success(ref)
          case _ =>
            p.failure(org.squbs.actorregistry.ActorNotFound(this))
        }
    }
    p.future
  }
}

