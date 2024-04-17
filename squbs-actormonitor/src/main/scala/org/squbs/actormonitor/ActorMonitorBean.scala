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

package org.squbs.actormonitor

import java.lang.management.ManagementFactory
import javax.management.MXBean
import scala.language.existentials

import org.apache.pekko.actor.{ActorContext, ActorRef, Props}
import org.squbs.unicomplex.JMX._

import scala.annotation.tailrec
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


private[actormonitor] object ActorMonitorBean {
  val Pattern = "org.squbs.unicomplex:type=ActorMonitor,name="
  val Total = Pattern + "*"

  def registerBean(actor: ActorRef) (implicit monitorConfig: ActorMonitorConfig , context: ActorContext) = {
    if (totalBeans.size < monitorConfig.maxActorCount)
      register(new ActorMonitorBean(actor), objName(actor))
  }

  def totalBeans(implicit context: ActorContext) =
    ManagementFactory.getPlatformMBeanServer.queryNames(prefix + Total, null)

  def unregisterBean(actor: ActorRef) (implicit context: ActorContext) = unregister(objName(actor))

  def objName(actor: ActorRef) (implicit context: ActorContext) = {
    prefix + Pattern + actor.path.toString.split(s"${actor.path.root}").mkString("")

  }

  def getDescendant(actor: ActorRef) =
    getPrivateValue(actor, Seq("children")).map(_.asInstanceOf[Iterable[ActorRef]].toSeq).getOrElse(Seq.empty[ActorRef])

  @tailrec
  def getPrivateValue(obj: Any, methods: Seq[String]): Option[Any] =
    methods.headOption match {
      case None => Some(obj)
      case Some(methodName) =>
        Try {
          val clazz = obj.getClass
          clazz.getDeclaredMethod(methodName)
        } recoverWith {
          case NonFatal(_) => Try {
            val clazz = obj.getClass.getSuperclass
            clazz.getDeclaredMethod(methodName)
          }
        } map { method =>
          method.setAccessible(true)
          method.invoke(obj)
        } match {
          case Failure(_) => None
          case Success(nextObj) => getPrivateValue(nextObj, methods.tail)
        }
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

private[actormonitor] class ActorMonitorBean(actor: ActorRef)(implicit monitorConfig: ActorMonitorConfig)
    extends ActorMonitorMXBean {
  import ActorMonitorBean._

  def getActor = actor.toString()
  def getClassName = props.map(_.actorClass().getCanonicalName).getOrElse("Error")
  def getRouteConfig = props.map(_.routerConfig.toString).getOrElse("Error")
  def getParent = getPrivateValue(actor, List("getParent")).map(_.toString).getOrElse("")
  def getChildren =  {
    val children = getDescendant(actor)
    import monitorConfig._
    children.size match {
      case count if count > maxChildrenDisplay => children.take(maxChildrenDisplay).mkString(",") + s"... total:$count"
      case _ => children.mkString(",")
    }
  }

  def getDispatcher = props.map(_.dispatcher).getOrElse("Error")
  def getMailBoxSize =
    actor.getClass.getName match {
      case "org.apache.pekko.actor.RepointableActorRef" =>
        getPrivateValue(actor, Seq("underlying", "numberOfMessages")).map(_.toString).getOrElse("N/A")
      case clazz =>
        getPrivateValue(actor, Seq("actorCell", "numberOfMessages")).map(_.toString).getOrElse("N/A")
  }

  lazy val props : Option[Props] =
    actor.getClass.getName match {
      case "org.apache.pekko.actor.LocalActorRef" =>
        getPrivateValue(actor, Seq("actorCell","props")).map(_.asInstanceOf[Props])
      case "org.apache.pekko.routing.RoutedActorRef" | "org.apache.pekko.actor.RepointableActorRef"=>
        getPrivateValue(actor, Seq("props")).map(_.asInstanceOf[Props])
      case c =>
        None
    }
}

@MXBean
private[actormonitor] trait ActorMonitorConfigMXBean {
  def getCount : Int
  def getMaxCount: Int
  def getMaxChildrenDisplay: Int
}

private[actormonitor] class ActorMonitorConfigBean(config: ActorMonitorConfig, monitorActor: ActorRef,
                                                   implicit val context: ActorContext)
    extends ActorMonitorConfigMXBean {
  def getCount : Int = ActorMonitorBean.totalBeans.size()
  def getMaxCount: Int = config.maxActorCount
  def getMaxChildrenDisplay: Int = {
    monitorActor ! "refresh"
    config.maxChildrenDisplay
  }
}

