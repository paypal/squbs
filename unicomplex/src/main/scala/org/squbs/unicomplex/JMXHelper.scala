package org.squbs.unicomplex

import java.lang.management.ManagementFactory
import javax.management.{MXBean, ObjectName}
import scala.beans.BeanProperty
import java.util.Date

object JMXHelper {
  implicit def string2objectName(name:String):ObjectName = new ObjectName(name)
  def jmxRegister(ob:Object, objName:ObjectName) =
    ManagementFactory.getPlatformMBeanServer.registerMBean(ob, objName)
}

@MXBean
trait SystemStateMXBean {
  def getSystemState: String
  def getStartTime : Date
  def getInitDuration: Int
  def getActivationDuration: Int
}


