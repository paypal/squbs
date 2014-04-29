package org.squbs.unicomplex

import java.lang.management.ManagementFactory
import javax.management.{ObjectInstance, MXBean, ObjectName}
import java.util.Date

object JMX {

  val unicomplexName = "org.squbs.unicomplex:type=Unicomplex"

  implicit def string2objectName(name:String):ObjectName = new ObjectName(name)

  def register(ob: AnyRef): ObjectInstance = ob match {

    case b: SystemStateMXBean =>
      register(b, unicomplexName)

    case s =>
      throw new IllegalArgumentException(s"Unknown type ${s.getClass.getName}. " +
        "Please use register with object name instead")
  }

  def register(ob: AnyRef, objName: ObjectName) = ManagementFactory.getPlatformMBeanServer.registerMBean(ob, objName)

  def get(objName: ObjectName, attr: String) = ManagementFactory.getPlatformMBeanServer.getAttribute(objName, attr)
}

@MXBean
trait SystemStateMXBean {
  def getSystemState: String
  def getStartTime : Date
  def getInitMillis: Int
  def getActivationMillis: Int
}


