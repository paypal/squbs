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
import org.squbs.actorregistry.ActorRegistryBean._
import org.squbs.unicomplex.Initialized
import org.squbs.unicomplex.JMX._

import scala.jdk.CollectionConverters._
import scala.language.existentials
import scala.util.Success

private[actorregistry] case class StartActorRegister(cubeNameList: Seq[CubeActorInfo], timeout: Int)
private[actorregistry] case class CubeActorInfo(actorPath: String ,messageTypeList: Seq[CubeActorMessageType])
private[actorregistry] case class CubeActorMessageType(requestClassName: Option[String] = None,
                                                       responseClassName: Option[String] = None)
private[actorregistry] case class ActorLookupMessage(actorLookup: ActorLookup[_], msg: Any)

private[actorregistry] case object ObtainRegistry

private[actorregistry] object ActorRegistry {
  val path = "/user/ActorRegistryCube/ActorRegistry"
  val configBean =  "org.squbs.unicomplex:type=ActorRegistry"
}

private[actorregistry] class ActorRegistry extends Actor with Stash {

  var registry = Map.empty[ActorRef, Seq[CubeActorMessageType]]
  var cubeCount =0

  private class ActorRegistryBean(actor: ActorRef) extends ActorRegistryMXBean {
    def getPath = actor.path.toString
    def getActorMessageTypeList = registry.getOrElse(actor, List.empty[CubeActorMessageType]).map(_.toString).asJava
  }

  override def postStop(): Unit = {
    unregister(prefix + ActorRegistry.configBean)
    totalBeans.asScala.foreach(unregister)
  }

  import ActorRegistry._
  import ActorRegistryBean._

  def startupReceive: Receive = {
    case ActorIdentity(cubeActorInfo : CubeActorInfo, Some(actor))=>
      registry += actor -> cubeActorInfo.messageTypeList
      register(new ActorRegistryBean(actor) , objName(actor))
      context.watch(actor)
      cubeCount -= 1
      if (cubeCount <= 0) {
        context.parent ! Initialized(Success(None))
        context.unbecome()
      }
    case _ => stash()
  }

  def receive = {
    case StartActorRegister(cubeActorInfoList, timeout) =>
      register(new ActorRegistryConfigBean(timeout, context), prefix + configBean )

      cubeCount = cubeActorInfoList.size

      if(cubeCount == 0) // No well-known actors to register
        context.parent ! Initialized(Success(None))
      else {
        cubeActorInfoList.foreach { cubeActorInfo =>
          context.actorSelection(cubeActorInfo.actorPath) ! Identify(cubeActorInfo)
        }

        context.become(startupReceive)
      }

    case ActorLookupMessage(lookupObj, Identify("ActorLookup"))  =>
      val result = processActorLookup(lookupObj).keys.headOption
      sender() !  ActorIdentity("ActorLookup", result)

    case ActorLookupMessage(lookupObj, msg) =>
      processActorLookup(lookupObj) match {
        case result if result.isEmpty =>
          sender() ! org.squbs.actorregistry.ActorNotFound(lookupObj)
        case result =>
          result.keys foreach { _ forward msg }
      }

    case Terminated(actor) =>
      registry -= actor
      unregister(objName(actor))
  }

  def processActorLookup(lookupObj: ActorLookup[_]) : Map[ActorRef, Seq[CubeActorMessageType]]= {
    val requestClass = lookupObj.requestClass map (_.getCanonicalName)
    val responseClass = if (lookupObj.explicitType) Option(lookupObj.responseClass.getCanonicalName) else None
    (requestClass, responseClass, lookupObj.actorName) match {

      case (requestClassName, Some("scala.runtime.Nothing$") | None, None) =>
        registry filter { case (_, messageTypes) => messageTypes.exists(_.requestClassName == requestClassName) }

      case (_,  Some("scala.runtime.Nothing$")| None , Some(actorName)) =>
        registry filter { case (actorRef, _) => actorRef.path.name == actorName }

      case (_, responseClassName, actorName) =>
        registry.filter { case (actorRef, messageTypes) =>
          actorRef.path.name == actorName.getOrElse(actorRef.path.name) &&
            messageTypes.exists(_.responseClassName == responseClassName)
        }

      case _ => Map.empty
    }
  }
}










