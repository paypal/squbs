package org.squbs.unicomplex

import java.lang.management.ManagementFactory
import javax.management.{MXBean, ObjectName}
import java.util.Date
import java.beans.ConstructorProperties
import scala.beans.BeanProperty
import akka.actor.{ActorSystem, ActorContext}
import scala.collection.concurrent.TrieMap

object JMX {

  val prefixConfig = "prefix-jmx-name"

  val systemStateName = "org.squbs.unicomplex:type=SystemState"
  val cubesName       = "org.squbs.unicomplex:type=Cubes"
  val cubeStateName   = "org.squbs.unicomplex:type=CubeState,name="
  val contextsName    = "org.squbs.unicomplex:type=Contexts,name="

  implicit def string2objectName(name:String):ObjectName = new ObjectName(name)

  private val prefixes = TrieMap.empty[ActorSystem, String]

  /**
   * Gets the prefix used for prefixing JMX names. If a single ActorSystem is used, this function returns empty string
   * unless explicitly configured with squbs.prefix-jmx-name = true. If multiple actor systems are detected, the first
   * (which could be indeterministic) will use no prefix. Subsequent JMX registration of the same component will
   * be prefixed with the ActorSystem name.<br/>
   *
   * Note: prefix derivation may not be reliable on concurrent access. If intending to use multiple ActorSystems,
   * it is more reliable to set configuration squbs.prefix-jmx-name = true
   *
   * @param system The caller's ActorSystem
   * @return The ActorSystem's name or empty string dependent on configuration and conflict.
   */
  def prefix(system: ActorSystem): String = {
    (prefixes.get(system) orElse Option {
      import ConfigUtil._
      val p =
        if (Unicomplex(system).config.getOptionalBoolean(prefixConfig).getOrElse(false) || isRegistered(systemStateName))
          system.name + '.'
        else ""
      prefixes += system -> p
      p
    }).get
  }

  def prefix(implicit context: ActorContext): String = prefix(context.system)

  def register(ob: AnyRef, objName: ObjectName) = ManagementFactory.getPlatformMBeanServer.registerMBean(ob, objName)

  def unregister(objName: ObjectName) = ManagementFactory.getPlatformMBeanServer.unregisterMBean(objName)

  def isRegistered(objName: ObjectName) = ManagementFactory.getPlatformMBeanServer.isRegistered(objName)

  def get(objName: ObjectName, attr: String) = ManagementFactory.getPlatformMBeanServer.getAttribute(objName, attr)
}

case class CubeInfo @ConstructorProperties(Array("name", "fullName", "version", "supervisorPath"))(
                                          @BeanProperty name: String,
                                          @BeanProperty fullName: String,
                                          @BeanProperty version: String,
                                          @BeanProperty supervisorPath: String)

case class ContextInfo @ConstructorProperties(Array("context", "routeClass", "cubeFullName", "cubeVersion"))(
                                          @BeanProperty context: String,
                                          @BeanProperty routeClass: String,
                                          @BeanProperty cubeFullName: String,
                                          @BeanProperty cubeVersion: String)

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
  def getContexts: java.util.List[ContextInfo]
}


