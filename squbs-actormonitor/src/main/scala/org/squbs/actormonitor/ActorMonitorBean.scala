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
package org.squbs.actormonitor

import java.lang.management.ManagementFactory
import javax.management.MXBean

import akka.actor.{ActorContext, ActorRef, Props}
import org.squbs.unicomplex.JMX._


private[actormonitor] object ActorMonitorBean {
  val Pattern = "org.squbs.unicomplex:type=ActorMonitor,name="
  val Total = Pattern + "*"

  def registerBean(actor: ActorRef) (implicit monitorConfig: ActorMonitorConfig , context: ActorContext) = {
    if (totalBeans.size < monitorConfig.maxActorCount)
      register(new ActorMonitorBean(actor), objName(actor))
  }

  def totalBeans = ManagementFactory.getPlatformMBeanServer.queryNames(Total, null)

  def unregisterBean(actor: ActorRef) (implicit context: ActorContext) = unregister(objName(actor))

  def objName(actor: ActorRef) (implicit context: ActorContext) = prefix + Pattern + actor.path.toString.split(s"${actor.path.root}user/").mkString("")

  def getDescendant(actor: ActorRef) = getPrivateValue(actor, List("children")).map(_.asInstanceOf[Iterable[ActorRef]]).map(_.toList).getOrElse(List.empty[ActorRef])

  def getPrivateValue(obj: Any, methods: List[String]): Option[Any] =
    try {
      methods.isEmpty match {
        case true => Some(obj)
        case false =>
          val m = try {
            obj.getClass.getDeclaredMethod(methods.head)
          } catch {
            case e: Exception =>
              obj.getClass.getSuperclass.getDeclaredMethod(methods.head)
          }
          m.setAccessible(true)
          val nextObj = m.invoke(obj)
          getPrivateValue(nextObj, methods.tail)
      }
    } catch {
      case e: Exception =>
        None
    }
}


@MXBean
private[actormonitor] trait ActorMonitorMXBean {
  def getActor: String
  def getClassName: String
  def getRouteConfig : String
  def getParent: String
  def getChildren: String
  def getDispatcher : String
  def getMailBoxSize : String
}

private[actormonitor] class ActorMonitorBean(actor: ActorRef)(implicit monitorConfig: ActorMonitorConfig) extends ActorMonitorMXBean {
  import ActorMonitorBean._

  def getActor = actor.toString
  def getClassName = props.map(_.actorClass.getCanonicalName).getOrElse("Error")
  def getRouteConfig = props.map(_.routerConfig.toString).getOrElse("Error")
  def getParent = getPrivateValue(actor, List("getParent")).map(_.toString).getOrElse("")
  def getChildren =  {
    val children = getDescendant(actor)
    import monitorConfig._
    children.size match {
      case count if (count > maxChildrenDisplay) => children.take(maxChildrenDisplay).mkString(",") + s"... total:$count"
      case _ => children.mkString(",")
    }
  }

  def getDispatcher = props.map(_.dispatcher).getOrElse("Error")
  def getMailBoxSize = getPrivateValue(actor, List("actorCell", "numberOfMessages")).map(_.toString).getOrElse("N/A")

  lazy val props : Option[Props] =
    actor.getClass.getName match {
      case "akka.actor.LocalActorRef" =>
        getPrivateValue(actor, List("actorCell","props")).map(_.asInstanceOf[Props])
      case "akka.routing.RoutedActorRef" | "akka.actor.RepointableActorRef"=>
        getPrivateValue(actor, List("props")).map(_.asInstanceOf[Props])
      case c =>
        None
    }
}

@MXBean
private[actormonitor] trait ActorMonitorConfigMXBean {
  def getMaxActorCount: Int
  def getMaxChildrenDisplay: Int
  def getTimeout : Int
}

private[actormonitor] class ActorMonitorConfigBean(config: ActorMonitorConfig) extends ActorMonitorConfigMXBean {
  def getMaxActorCount: Int = config.maxActorCount
  def getMaxChildrenDisplay: Int = config.maxChildrenDisplay
  def getTimeout: Int = config.timeout
}

