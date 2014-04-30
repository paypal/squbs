package org.squbs.unicomplex

import java.lang.management.ManagementFactory
import javax.management.{MXBean, ObjectName}
import java.util.Date
import java.beans.ConstructorProperties
import scala.beans.BeanProperty

object JMX {

  val systemStateName = "org.squbs.unicomplex:type=SystemState"
  val cubesName       = "org.squbs.unicomplex:type=Cubes"
  val cubeStateName   = "org.squbs.unicomplex:type=CubeState,name="
  val contextsName    = "org.squbs.unicomplex:type=Contexts"

  implicit def string2objectName(name:String):ObjectName = new ObjectName(name)

  def register(ob: AnyRef, objName: ObjectName) = ManagementFactory.getPlatformMBeanServer.registerMBean(ob, objName)

  def unregister(objName: ObjectName) = ManagementFactory.getPlatformMBeanServer.unregisterMBean(objName)

  def get(objName: ObjectName, attr: String) = ManagementFactory.getPlatformMBeanServer.getAttribute(objName, attr)
}

case class CubeInfo @ConstructorProperties(Array("name", "fullName", "version", "supervisorPath"))(
                                                                                 @BeanProperty name: String,
                                                                                 @BeanProperty fullName: String,
                                                                                 @BeanProperty version: String,
                                                                                 @BeanProperty supervisorPath: String)

@MXBean
trait SystemStateMXBean {
  def getSystemState: String
  def getStartTime : Date
  def getInitMillis: Int
  def getActivationMillis: Int
}

@MXBean
trait CubesMXBean {
  def getCubes: java.util.List[CubeInfo]
}

@MXBean
trait CubeStateMXBean {
  def getName: String
  def getCubeState: String
}

@MXBean
trait ContextsMXBean {
  def getContexts: java.util.List[String]
}


