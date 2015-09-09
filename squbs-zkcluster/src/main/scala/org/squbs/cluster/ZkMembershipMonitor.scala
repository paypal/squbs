/*
 *  Copyright 2015 PayPal
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

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.{Actor, Address, AddressFromURIString}
import com.typesafe.scalalogging.LazyLogging
import org.apache.curator.framework.api.CuratorWatcher
import org.apache.curator.framework.recipes.leader.LeaderLatch
import org.apache.zookeeper.Watcher.Event.EventType
import org.apache.zookeeper.{CreateMode, WatchedEvent}

import scala.collection.JavaConversions._
import scala.concurrent.duration._

private[cluster] case object ZkAcquireLeadership
private[cluster] case class ZkLeaderElected(address: Option[Address])
private[cluster] case class ZkMembersChanged(members: Set[Address])

/**
 * the membership monitor has a few responsibilities, 
 * most importantly to enroll the leadership competition and get membership,
 * leadership information immediately after change
 */
private[cluster] class ZkMembershipMonitor extends Actor with LazyLogging {

  private[this] val zkCluster = ZkCluster(context.system)
  import zkCluster._
  
  private[this] implicit val log = logger
  private[this] var zkLeaderLatch: Option[LeaderLatch] = None
  private[this] val stopped = new AtomicBoolean(false)
  
  def initialize() = {
    //watch over leader changes
    val leader = Option(zkClientWithNs.getData.usingWatcher(new CuratorWatcher {
      override def process(event: WatchedEvent): Unit = {
        log.debug("[membership] leader watch event:{} when stopped:{}", event, stopped.toString)
        if(!stopped.get) {
          event.getType match {
            case EventType.NodeCreated | EventType.NodeDataChanged =>
              zkClusterActor ! ZkLeaderElected(zkClientWithNs.getData.usingWatcher(this).forPath("/leader").toAddress)
            case EventType.NodeDeleted =>
              self ! ZkAcquireLeadership
            case _ =>
          }
        }
      }
    }).forPath("/leader"))

    //watch over my self
    val me = guarantee(s"/members/${keyToPath(zkAddress.toString)}", Some(Array[Byte]()), CreateMode.EPHEMERAL)
    // Watch and recreate member node because it's possible for ephemeral node to be deleted while session is
    // still alive (https://issues.apache.org/jira/browse/ZOOKEEPER-1740)
    zkClientWithNs.getData.usingWatcher(new CuratorWatcher {
      def process(event: WatchedEvent): Unit = {
        log.debug("[membership] self watch event: {} when stopped:{}", event, stopped.toString)
        if(!stopped.get) {
          event.getType match {
            case EventType.NodeDeleted =>
              log.info("[membership] member node was deleted unexpectedly, recreate")
              zkClientWithNs.getData.usingWatcher(this).forPath(
                guarantee(me, Some(Array[Byte]()), CreateMode.EPHEMERAL)
              )
            case _ =>
          }
        }
      }
    }).forPath(me)

    //watch over members changes
    lazy val members = zkClientWithNs.getChildren.usingWatcher(new CuratorWatcher {
      override def process(event: WatchedEvent): Unit = {
        log.debug("[membership] membership watch event:{} when stopped:{}", event, stopped.toString)
        if(!stopped.get) {
          event.getType match {
            case EventType.NodeChildrenChanged =>
              refresh(zkClientWithNs.getChildren.usingWatcher(this).forPath("/members"))
            case _ =>
          }
        }
      }
    }).forPath("/members")

    def refresh(members: Seq[String]) = {
      // tell the zkClusterActor to update the memory snapshot
      zkClusterActor ! ZkMembersChanged(members.map(m => AddressFromURIString(pathToKey(m))).toSet)
      // member changed, try to acquire the leadership
      self ! ZkAcquireLeadership
    }

    refresh(members)
    leader foreach { l => zkClusterActor ! ZkLeaderElected(l.toAddress) }
  }
  
  override def postStop() = {
    //stop the leader latch to quit the competition
    stopped set true
    zkLeaderLatch foreach (_.close())
    zkLeaderLatch = None
  }
  
  def receive: Actor.Receive = {
    case ZkClientUpdated(updated) =>
      // differentiate first connected to ZK or reconnect after connection lost
      zkLeaderLatch foreach (_.close())
      zkLeaderLatch = Some(new LeaderLatch(zkClientWithNs, "/leadership"))
      zkLeaderLatch foreach (_.start())
      initialize()
    case ZkAcquireLeadership =>
      //repeatedly enroll in the leadership competition once the last attempt fails
      val oneSecond = 1.second
      zkLeaderLatch foreach {
        _.await(oneSecond.length, oneSecond.unit) match {
          case true =>
            log.info("[membership] leadership acquired @ {}", zkAddress)
            guarantee("/leader", Some(zkAddress))
          case false =>
        }
      }
  }
}