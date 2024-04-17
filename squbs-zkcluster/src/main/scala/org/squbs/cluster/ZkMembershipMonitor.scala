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

import java.util.concurrent.atomic.AtomicBoolean

import org.apache.pekko.actor.{Actor, Address, AddressFromURIString}
import com.typesafe.scalalogging.LazyLogging
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.api.CuratorWatcher
import org.apache.curator.framework.recipes.leader.{LeaderLatch, LeaderLatchListener}
import org.apache.zookeeper.Watcher.Event.EventType
import org.apache.zookeeper.{CreateMode, WatchedEvent}

import scala.jdk.CollectionConverters._

@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
private[cluster] case class ZkLeaderElected(address: Option[Address])

@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
private[cluster] case class ZkMembersChanged(members: Set[Address])

/**
 * The membership monitor has a few responsibilities,
 * most importantly to enroll the leadership competition and get membership,
 * leadership information immediately after change
 */
@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
private[cluster] class ZkMembershipMonitor extends Actor with LazyLogging {

  private[this] val zkCluster = ZkCluster(context.system)
  import zkCluster._

  private[this] implicit val log = logger
  private[this] var zkLeaderLatch: Option[LeaderLatch] = None
  private[this] val stopped = new AtomicBoolean(false)
  
  private def initialize()(implicit curatorFwk: CuratorFramework) = {
    //watch over leader changes
    curatorFwk.getData.usingWatcher(new CuratorWatcher {
      override def process(event: WatchedEvent): Unit = {
        log.debug("[membership] leader watch event:{} when stopped:{}", event, stopped.toString)
        if(!stopped.get && event.getType == EventType.NodeDataChanged) {
          zkClusterActor ! ZkLeaderElected(curatorFwk.getData.usingWatcher(this).forPath("/leader").toAddress)
        }
      }
    }).forPath("/leader")

    //watch over my self
    val me = guarantee(s"/members/${keyToPath(zkAddress.toString)}", Some(Array[Byte]()), CreateMode.EPHEMERAL)
    // Watch and recreate member node because it's possible for ephemeral node to be deleted while session is
    // still alive (https://issues.apache.org/jira/browse/ZOOKEEPER-1740)
    curatorFwk.getData.usingWatcher(new CuratorWatcher {
      def process(event: WatchedEvent): Unit = {
        log.debug("[membership] self watch event: {} when stopped:{}", event, stopped.toString)
        if(!stopped.get && event.getType == EventType.NodeDeleted) {
          log.info("[membership] member node was deleted unexpectedly, recreate")
          curatorFwk.getData.usingWatcher(this).forPath(guarantee(me, Some(Array[Byte]()), CreateMode.EPHEMERAL))
        }
      }
    }).forPath(me)

    //watch over members changes
    lazy val members = curatorFwk.getChildren.usingWatcher(new CuratorWatcher {
      override def process(event: WatchedEvent): Unit = {
        log.debug("[membership] membership watch event:{} when stopped:{}", event, stopped.toString)
        if(!stopped.get && event.getType == EventType.NodeChildrenChanged) {
          refresh(curatorFwk.getChildren.usingWatcher(this).forPath("/members").asScala.toSeq)
        }
      }
    }).forPath("/members")

    def refresh(members: Seq[String]) = {
      // tell the zkClusterActor to update the memory snapshot
      zkClusterActor ! ZkMembersChanged(members.map(m => AddressFromURIString(pathToKey(m))).toSet)
    }

    refresh(members.asScala.toSeq)
  }
  
  override def postStop(): Unit = {
    //stop the leader latch to quit the competition
    stopped set true
    zkLeaderLatch foreach (_.close())
    zkLeaderLatch = None
  }

  def receive: Actor.Receive = {
    case ZkClientUpdated(updated) =>
      // differentiate first connected to ZK or reconnect after connection lost
      implicit val curatorFwk = updated
      zkLeaderLatch foreach (_.close())
      zkLeaderLatch = Option(new LeaderLatch(curatorFwk, "/leadership"))
      initialize()
      zkLeaderLatch foreach {latch =>
        latch.addListener(new LeaderLatchListener {
          override def isLeader(): Unit = {
            log.info("[membership] leadership acquired @ {}", zkAddress)
            guarantee("/leader", Some(zkAddress))(curatorFwk, log)
          }
          override def notLeader(): Unit = {
            zkClusterActor ! ZkLeaderElected(curatorFwk.getData.forPath("/leader").toAddress)
          }
        }, context.dispatcher)
        latch.start()
        zkClusterActor ! ZkLeaderElected(curatorFwk.getData.forPath("/leader").toAddress)
      }
  }
}
