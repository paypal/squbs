/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the AUTHORS file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.squbs.actorregistry


import akka.actor._
import org.squbs.unicomplex.{JMX, Initialized}
import scala.collection.mutable
import scala.util.Success
import ActorRegistryBean._
import JMX._
import collection.JavaConversions._

private[actorregistry] case class StartActorRegister(cubeNameList: List[CubeActorInfo], timeout: Int)
private[actorregistry] case class CubeActorInfo(actorPath: String ,messageTypeList : List[CubeActorMessageType])
private[actorregistry] case class CubeActorMessageType(requestClassName: String=null, responseClassName: String=null)
private[actorregistry] case class ActorLookupMessage(actorLookup: ActorLookup, msg: Any)

private[actorregistry] object ActorRegistry {
  val path = "/user/ActorRegistryCube/ActorRegistry"
  val registry = mutable.HashMap.empty[ActorRef,List[CubeActorMessageType]]
  val configBean =  "org.squbs.unicomplex:type=ActorRegistry"

}

private[actorregistry] class ActorRegistry extends Actor with Stash {
  var cubeCount =0

  override def postStop() {
    unregister(prefix + ActorRegistry.configBean)
    totalBeans.foreach {unregister(_)}
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
      val result = processActorLookup(lookupObj).map(_._1).find(x=> true)
      sender !  ActorIdentity("ActorLookup", result)

    case ActorLookupMessage(lookupObj, msg) =>
      processActorLookup(lookupObj) match {
        case result if (result.isEmpty) =>
          sender ! org.squbs.actorregistry.ActorNotFound(lookupObj)
        case result =>
          result foreach (_._1.tell(msg, sender))
      }

    case Terminated(actor) =>
      registry.remove(actor)
      unregisterBean(actor)
  }

  def processActorLookup(lookupObj: ActorLookup) : Map[ActorRef, List[CubeActorMessageType]]= {
    var result= registry.toMap
    lookupObj match {
      case ActorLookup(None, None, None) =>
        Map.empty[ActorRef, List[CubeActorMessageType]]
      case ActorLookup(_, responseClass, Some(actorName)) =>
        val resultActorFilter = result.filter(_._1.path.name == actorName)
        responseClass.map(_.getCanonicalName) match {
          case Some("scala.runtime.Nothing$") =>
            resultActorFilter
          case any =>
            resultActorFilter.filter(_._2.exists(_.responseClassName == any.getOrElse("")))
        }
      case ActorLookup(requestClass, responseClass, None) =>
        val resultRequestFilter = requestClass.map(_.getCanonicalName) match {
          case Some(name) if name.startsWith("akka.actor.")=>
            result
          case Some(name) if (name.endsWith("$")) =>
            val newName = name.substring(0, name.length - 1)
            result.filter(_._2.exists(_.requestClassName == newName))
          case Some(name) =>
            result.filter(_._2.exists(_.requestClassName == name))
          case _ =>
            result
        }

        val resultResponseFilter = responseClass.map(_.getCanonicalName) match {
          case Some("scala.runtime.Nothing$") =>
            resultRequestFilter
          case Some(name)=>
            resultRequestFilter.filter(_._2.exists(_.responseClassName == name))
          case _ =>
            resultRequestFilter
        }

        resultResponseFilter
    }
  }
}










