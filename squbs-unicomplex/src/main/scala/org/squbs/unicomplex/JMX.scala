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

package org.squbs.unicomplex

import org.apache.pekko.actor._
import com.typesafe.config.Config

import java.beans.ConstructorProperties
import java.lang.management.ManagementFactory
import java.util
import java.util.Date
import javax.management.{MXBean, ObjectInstance, ObjectName}
import scala.beans.BeanProperty
import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters._
import scala.language.{implicitConversions, postfixOps}

object JMX {

  val prefixConfig = "prefix-jmx-name"

  val systemStateName = "org.squbs.unicomplex:type=SystemState"
  val cubesName       = "org.squbs.unicomplex:type=Cubes"
  val extensionsName  = "org.squbs.unicomplex:type=Extensions"
  val cubeStateName   = "org.squbs.unicomplex:type=CubeState,name="
  val listenersName    = "org.squbs.unicomplex:type=Listeners"
  val listenerStateName = "org.squbs.unicomplex:type=ListenerState"
  val serverStats = "org.squbs.unicomplex:type=ServerStats,listener="
  val systemSettingName   = "org.squbs.unicomplex:type=SystemSetting"
  val forkJoinStatsName = "org.squbs.unicomplex:type=ForkJoinPool,name="
  val materializerName = "org.squbs.unicomplex:type=Materializer,name="


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
      import org.squbs.util.ConfigUtil._
      val p =
        if (Unicomplex(system).config.get[Boolean](prefixConfig, false) || isRegistered(systemStateName))
          system.name + '.'
        else ""
      prefixes += system -> p
      p
    }).get
  }

  def prefix(implicit context: ActorContext): String = prefix(context.system)

  def register(ob: AnyRef, objName: ObjectName): ObjectInstance =
    ManagementFactory.getPlatformMBeanServer.registerMBean(ob, objName)

  def unregister(objName: ObjectName): Unit = ManagementFactory.getPlatformMBeanServer.unregisterMBean(objName)

  def isRegistered(objName: ObjectName): Boolean = ManagementFactory.getPlatformMBeanServer.isRegistered(objName)

  def get(objName: ObjectName, attr: String): AnyRef =
    ManagementFactory.getPlatformMBeanServer.getAttribute(objName, attr)
}

// $COVERAGE-OFF$
case class CubeInfo @ConstructorProperties(Array("name", "fullName", "version", "supervisor"))(
                                          @BeanProperty name: String,
                                          @BeanProperty fullName: String,
                                          @BeanProperty version: String,
                                          @BeanProperty supervisor: String)

case class ListenerState @ConstructorProperties(Array("listener", "state", "error"))(
                                          @BeanProperty listener: String,
                                          @BeanProperty state: String,
                                          @BeanProperty error: String)

case class ListenerInfo @ConstructorProperties(Array("listener", "context", "actor"))(
                                          @BeanProperty listener: String,
                                          @BeanProperty context: String,
                                          @BeanProperty actorPath: String)
case class SystemSetting @ConstructorProperties(Array("key", "value"))(
                                          @BeanProperty key: String,
                                          @BeanProperty value: String)


case class ExtensionInfo @ConstructorProperties(Array("cube", "sequence", "phase", "error"))(
                                          @BeanProperty cube: String,
                                          @BeanProperty sequence: Int,
                                          @BeanProperty phase: String,
                                          @BeanProperty error: String)

case class ActorErrorState @ConstructorProperties(Array("actorPath", "errorCount", "latestException"))(
                                          @BeanProperty actorPath: String,
                                          @BeanProperty errorCount: Int,
                                          @BeanProperty latestException: String)

// $COVERAGE-ON$

@MXBean
trait SystemStateMXBean {
  def getSystemState: String
  def getStartTime : Date
  def getInitMillis: Int
  def getActivationMillis: Int
}

@MXBean
trait CubesMXBean {
  def getCubes: util.List[CubeInfo]
}

@MXBean
trait ExtensionsMXBean {
  def getExtensions: util.List[ExtensionInfo]
}

@MXBean
trait ActorMXBean {
  def getActor: String
  def getClassName: String
  def getRouteConfig : String
  def getParent: String
  def getChildren: String
  def getDispatcher : String
  def getMailBoxSize : String
}

@MXBean
trait CubeStateMXBean {
  def getName: String
  def getCubeState: String
  def getWellKnownActors : String
  def getActorErrorStates: util.List[ActorErrorState]
}

@MXBean
trait ListenerStateMXBean {
  def getListenerStates: java.util.List[ListenerState]
}

@MXBean
trait ListenerMXBean {
  def getListeners: java.util.List[ListenerInfo]
}

@MXBean
trait ServerStatsMXBean {
  def getListenerName: String
  def getUptime: String
  def getTotalRequests: Long
  def getOpenRequests: Long
  def getMaxOpenRequests: Long
  def getTotalConnections: Long
  def getOpenConnections: Long
  def getMaxOpenConnections: Long
  def getRequestsTimedOut: Long
}

@MXBean
trait SystemSettingMXBean {
  def getSystemSetting: util.List[SystemSetting]
}

@MXBean
trait ForkJoinPoolMXBean {
  def getPoolSize: Int
  def getActiveThreadCount: Int
  def getParallelism: Int
  def getStealCount: Long
  def getMode: String
  def getQueuedSubmissionCount: Int
  def getQueuedTaskCount: Long
  def getRunningThreadCount: Int
  def isQuiescent: Boolean
}

class SystemSettingBean(config: Config) extends SystemSettingMXBean {
  lazy val settings:util.List[SystemSetting] = {
    def iterateMap(prefix: String, map: util.Map[String, AnyRef]): util.Map[String, String] = {
      val result = new util.TreeMap[String, String]()
      map.asScala.foreach {
        case (key, v: util.List[_]) =>
          val value = v.asInstanceOf[util.List[AnyRef]]
          result.putAll(iterateList(s"$prefix$key", value))
        case (key, v: util.Map[_, _]) =>
          val value = v.asInstanceOf[util.Map[String, AnyRef]]
          result.putAll(iterateMap(s"$prefix$key.", value))
        case (key, value) => result.put(s"$prefix$key", String.valueOf(value))
      }
      result
    }

    def iterateList(prefix: String, list: util.List[AnyRef]): util.Map[String, String] = {
      val result = new util.TreeMap[String, String]()

      list.asScala.zipWithIndex.foreach{
        case (v: util.List[_], i) =>
          val value = v.asInstanceOf[util.List[AnyRef]]
          result.putAll(iterateList(s"$prefix[$i]", value))
        case (v: util.Map[_, _], i) =>
          val value = v.asInstanceOf[util.Map[String, AnyRef]]
          result.putAll(iterateMap(s"$prefix[$i].", value))
        case (value, i) => result.put(s"$prefix[$i]", String.valueOf(value))
      }
      result
    }

    iterateMap("", config.root.unwrapped()).asScala.toList.map { case (k:String, v:String) =>
      SystemSetting(k, v)
    }.asJava
  }
  override def getSystemSetting: util.List[SystemSetting] = settings
}