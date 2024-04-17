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

package org.squbs.cluster

import java.beans.ConstructorProperties
import java.lang.management.ManagementFactory
import javax.management.ObjectName
import scala.language.implicitConversions

import org.apache.pekko.actor.ActorContext

import scala.beans.BeanProperty

@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
object JMX {

  val membersInfoName = "org.squbs.zkcluster:type=MembersInfo"
  val partitionsInfoName = "org.squbs.zkcluster:type=Partitions"

  implicit def string2objectName(name:String):ObjectName = new ObjectName(name)

  def prefix(implicit context: ActorContext): String = s"${context.system.name}."

  def register(ob: AnyRef, objName: ObjectName) = ManagementFactory.getPlatformMBeanServer.registerMBean(ob, objName)

  def unregister(objName: ObjectName) = ManagementFactory.getPlatformMBeanServer.unregisterMBean(objName)

  def isRegistered(objName: ObjectName) = ManagementFactory.getPlatformMBeanServer.isRegistered(objName)

  def get(objName: ObjectName, attr: String) = ManagementFactory.getPlatformMBeanServer.getAttribute(objName, attr)
}

@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
case class PartitionInfo @ConstructorProperties(Array("name", "zkPath", "members"))
(@BeanProperty name: String,
 @BeanProperty zkPath: String,
 @BeanProperty members: String)

@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
trait MembersInfoMXBean {
  def getLeader: String
  def getMembers: java.util.List[String]
}

@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
trait PartitionsInfoMXBean {
  def getPartitions: java.util.List[PartitionInfo]
}
