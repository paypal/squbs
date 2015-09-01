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
import org.squbs.unicomplex.{JMX, Initialized}
import scala.collection.mutable
import scala.util.Success
import ActorRegistryBean._
import JMX._
import collection.JavaConversions._

private[actorregistry] case class StartActorRegister(cubeNameList: Seq[CubeActorInfo], timeout: Int)
private[actorregistry] case class CubeActorInfo(actorPath: String ,messageTypeList: Seq[CubeActorMessageType])
private[actorregistry] case class CubeActorMessageType(requestClassName: Option[String] = None,
                                                       responseClassName: Option[String] = None)
private[actorregistry] case class ActorLookupMessage(actorLookup: ActorLookup, msg: Any)

private[actorregistry] object ActorRegistry {
  val path = "/user/ActorRegistryCube/ActorRegistry"
  val registry = mutable.HashMap.empty[ActorRef, Seq[CubeActorMessageType]]
  val configBean =  "org.squbs.unicomplex:type=ActorRegistry"

}

private[actorregistry] class ActorRegistry extends Actor with Stash {
  var cubeCount =0

  override def postStop() {
    unregister(prefix + ActorRegistry.configBean)
    totalBeans foreach unregister
  }

  import ActorRegistry._
  import ActorRegistryBean._

  def startupReceive: Receive = {
    case ActorIdentity(cubeActorInfo : CubeActorInfo, Some(actor))=>
      registry += (actor -> cubeActorInfo.messageTypeList)
      registerBean(actor)
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
      cubeActorInfoList.foreach { cubeActorInfo=>
        context.actorSelection(cubeActorInfo.actorPath) ! Identify(cubeActorInfo)
      }

      context.become(startupReceive)

    case ActorLookupMessage(lookupObj, Identify("ActorLookup"))  =>
      val result = processActorLookup(lookupObj).keys.headOption
      sender() !  ActorIdentity("ActorLookup", result)

    case ActorLookupMessage(lookupObj, msg) =>
      processActorLookup(lookupObj) match {
        case result if result.isEmpty =>
          sender() ! org.squbs.actorregistry.ActorNotFound(lookupObj)
        case result =>
          result.keys foreach { _ tell (msg, sender()) }
      }

    case Terminated(actor) =>
      registry.remove(actor)
      unregisterBean(actor)
  }

  def processActorLookup(lookupObj: ActorLookup) : Map[ActorRef, Seq[CubeActorMessageType]]= {
    val requestClass = lookupObj.requestClass map (_.getCanonicalName)
    val responseClass = lookupObj.responseClass map (_.getCanonicalName)
    (requestClass, responseClass, lookupObj.actorName) match {
      case (requestClassName, Some("scala.runtime.Nothing$") | None, None) =>
        registry.toMap.filter(_._2.exists(_.requestClassName == requestClassName))
      case (_,  Some("scala.runtime.Nothing$")| None , Some(actorName)) =>
        registry.toMap.filter(_._1.path.name == actorName)
      case (_, responseClassName, actorName) =>
        registry.toMap.filter(x=>x._1.path.name == actorName.getOrElse(x._1.path.name))
        .filter(_._2.exists(_.responseClassName == responseClassName))
      case obj =>
        Map.empty[ActorRef, Seq[CubeActorMessageType]]
    }
  }
}










