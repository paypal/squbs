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
package org.squbs.unicomplex

import java.lang.management.ManagementFactory
import javax.management.{MXBean, ObjectName}
import java.util.Date
import java.beans.ConstructorProperties
import spray.can.Http
import spray.can.server.Stats

import scala.beans.BeanProperty
import akka.actor._
import scala.collection.concurrent.TrieMap
import scala.concurrent.Await
import scala.util.Try

object JMX {

  val prefixConfig = "prefix-jmx-name"

  val systemStateName = "org.squbs.unicomplex:type=SystemState"
  val cubesName       = "org.squbs.unicomplex:type=Cubes"
  val cubeStateName   = "org.squbs.unicomplex:type=CubeState,name="
  val listenersName    = "org.squbs.unicomplex:type=Listeners"
  val serverStats = "org.squbs.unicomplex:type=serverStats,listener="
  val actorInfo       = "org.squbs.unicomplex:type=Actor,name="

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

// $COVERAGE-OFF$
case class CubeInfo @ConstructorProperties(Array("name", "fullName", "version", "supervisor"))(
                                          @BeanProperty name: String,
                                          @BeanProperty fullName: String,
                                          @BeanProperty version: String,
                                          @BeanProperty supervisor: String)

case class ListenerInfo @ConstructorProperties(Array("listener", "context", "actor"))(
                                          @BeanProperty listener: String,
                                          @BeanProperty context: String,
                                          @BeanProperty actorPath: String)
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
  def getCubes: java.util.List[CubeInfo]
}

@MXBean
trait ActorMXBean {
  def getActor: String
  def getClassName: String
  def getRouteConfig : String
  def getParent: String
  def getChildren: String
  def getDispatcher : String
}

@MXBean
trait CubeStateMXBean {
  def getName: String
  def getCubeState: String
  def getWellKnownActors : String
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

class SeverStats(name: String, httpListener: ActorRef) extends ServerStatsMXBean {
  import akka.pattern._
  import scala.concurrent.duration._
  import spray.util._

  override def getListenerName: String = name

  override def getTotalConnections: Long = status.map(_.totalConnections) getOrElse -1

  override def getRequestsTimedOut: Long = status.map(_.requestTimeouts) getOrElse -1

  override def getOpenRequests: Long = status.map(_.openRequests) getOrElse -1

  override def getUptime: String = status.map(_.uptime.formatHMS) getOrElse "00:00:00.000"

  override def getMaxOpenRequests: Long = status.map(_.maxOpenRequests) getOrElse -1

  override def getOpenConnections: Long = status.map(_.openConnections) getOrElse -1

  override def getMaxOpenConnections: Long = status.map(_.maxOpenConnections) getOrElse -1

  override def getTotalRequests: Long = status.map(_.totalRequests) getOrElse -1

  private def status: Option[Stats] = {
    val statsFuture = httpListener.ask(Http.GetStats)(1 second).mapTo[Stats]
    Try(Await.result(statsFuture, 1 second)).toOption
  }
}

class ActorBean(implicit context: ActorContext) extends PredefinedActorBean(context.props, context.self, context.parent){
  import context._
  override def getChildren = {
   children.size match {
     case count if (count > 10) => children.drop(count-20).mkString(",") + s"... total:$count"
     case _ => children.mkString(",")
   }
  }
}

class PredefinedActorBean(props: Props, self: ActorRef, parent: ActorRef) extends ActorMXBean {
  def getActor = self.toString
  def getClassName = props.actorClass.getCanonicalName
  def getRouteConfig = props.routerConfig.toString
  def getParent = parent.toString
  def getChildren = "Not Monitored"
  def getDispatcher = props.dispatcher
}

trait ActorJMX  {
  this : Actor =>

  import JMX._
  val objName = {
    val name = self.path.toString.split(s"${self.path.root}user/").mkString("")
    prefix + actorInfo + name
  }

  override def preStart() {
    if (isRegistered(objName))
      unregister(objName)

    register(new ActorBean, objName)
  }

  override def postStop() {
    unregister(objName )
  }
}
