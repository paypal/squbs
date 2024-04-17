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

import java.lang.management.ManagementFactory
import javax.management.MXBean

import org.apache.pekko.actor.{ActorContext, ActorRef}
import org.squbs.unicomplex.JMX._



private[actorregistry] object ActorRegistryBean {
  val Pattern  = "org.squbs.unicomplex:type=ActorRegistry,name="
  val Total = Pattern + "*"

  def objName(actor: ActorRef) (implicit context: ActorContext)= prefix + Pattern + actor.path.toString.split(s"${actor.path.root}user/").mkString("")
  def totalBeans(implicit context: ActorContext) = ManagementFactory.getPlatformMBeanServer.queryNames(prefix + Total, null)
}

@MXBean
private[actorregistry] trait ActorRegistryMXBean {
  def getPath : String
  def getActorMessageTypeList: java.util.List[String]
}

@MXBean
private[actorregistry] trait ActorRegistryConfigMXBean {
  def getCount : Int
  def getTimeout: Int
}

private[actorregistry] class ActorRegistryConfigBean(timeout: Int, implicit val context: ActorContext) extends ActorRegistryConfigMXBean {
  def getCount : Int = ActorRegistryBean.totalBeans.size
  def getTimeout: Int = timeout
}
