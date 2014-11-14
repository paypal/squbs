package org.squbs.cluster

import java.beans.ConstructorProperties
import java.lang.management.ManagementFactory
import javax.management.ObjectName

import akka.actor.ActorContext

import scala.beans.BeanProperty

/**
 * Created by zhuwang on 11/4/14.
 */
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

case class PartitionInfo @ConstructorProperties(Array("name", "zkPath", "members"))
                                               (@BeanProperty name: String,
                                                @BeanProperty zkPath: String,
                                                @BeanProperty members: String)

trait MembersInfoMXBean {
  def getLeader: String
  def getMembers: java.util.List[String]
}

trait PartitionsInfoMXBean {
  def getPartitions: java.util.List[PartitionInfo]
}