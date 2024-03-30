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

import java.util

import org.apache.pekko.actor._
import org.apache.pekko.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import org.apache.curator.framework.CuratorFramework
import org.squbs.cluster.JMX._

import scala.language.postfixOps
import scala.util.Try
import scala.jdk.CollectionConverters._

@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
private[cluster] sealed trait ZkClusterState

@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
private[cluster] case object ZkClusterUninitialized extends ZkClusterState

@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
private[cluster] case object ZkClusterActiveAsLeader extends ZkClusterState

@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
private[cluster] case object ZkClusterActiveAsFollower extends ZkClusterState

@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
private[cluster] case class ZkPartitionData(partitionKey: ByteString,
                                            members: Set[Address] = Set.empty,
                                            expectedSize: Int,
                                            props: Array[Byte] = Array.empty)

@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
private[cluster] case class ZkClusterData(leader: Option[Address],
                                          members: Set[Address],
                                          partitions: Map[ByteString, ZkPartitionData],
                                          curatorFwk: Option[CuratorFramework])

/**
 * The main Actor of ZkCluster
 */
@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
class ZkClusterActor extends FSM[ZkClusterState, ZkClusterData] with Stash with LazyLogging {

  private[this] val zkCluster = ZkCluster(context.system)
  import zkCluster._

  private[this] implicit val segLogic = segmentationLogic
  import segLogic._

  private[this] implicit val iLog = logger
  private[cluster] var whenZkClientUpdated = Seq.empty[ActorRef]
  private[cluster] var whenPartitionUpdated = Set.empty[ActorRef]

  //begin the process of electing a leader
  private val zkMembershipMonitor =
    context.actorOf(Props[ZkMembershipMonitor]().withDispatcher("ZkMembershipMonitor-dispatcher"), "zkMembership")

  //begin the process of partitioning management
  private val zkPartitionsManager = context.actorOf(Props[ZkPartitionsManager](), "zkPartitions")

  private[this] val mandatory:StateFunction = {
    case Event(updatedEvent @ ZkClientUpdated(updated), zkClusterData) =>
      zkMembershipMonitor ! updatedEvent
      zkPartitionsManager ! updatedEvent
      whenZkClientUpdated.foreach(_ ! updatedEvent)
      implicit val curatorFwk = updated
      val partitions = ZkPartitionsManager.loadPartitions()
      stay() using zkClusterData.copy(partitions = partitions, curatorFwk = Some(updated))
    case Event(ZkMonitorClient, _) =>
      whenZkClientUpdated = whenZkClientUpdated :+ sender()
      stay()
    case Event(ZkQueryMembership, zkClusterData) =>
      sender() ! ZkMembership(zkClusterData.members)
      stay()
    case Event(ZkMonitorPartition, _) =>
      log.info("[follower/leader] monitor partitioning from:{}", sender().path)
      whenPartitionUpdated += sender()
      stay()
    case Event(ZkStopMonitorPartition, _) =>
      log.info("[follower/leader] stop monitor partitioning from:{}", sender().path)
      whenPartitionUpdated -= sender()
      stay()
    case Event(ZkListPartitions(member), zkClusterData) =>
      sender() ! ZkPartitions(zkClusterData.partitions.collect{
        case (partitionKey, ZkPartitionData(_, members, _, _)) if members.contains(member) => partitionKey
      }.toSeq)
      stay()
  }

  // reflect the current memory snapshot in the MXBean
  class MembersInfoBean extends MembersInfoMXBean {
    override def getLeader: String = stateData.leader.map(_.toString).toString
    override def getMembers: util.List[String] = stateData.members.map(_.toString).toList.asJava
  }

  class PartitionsInfoBean extends PartitionsInfoMXBean {
    override def getPartitions: util.List[PartitionInfo] = stateData.partitions.map {
      case (key, data) => PartitionInfo(key, partitionZkPath(key), data.members.mkString(","))
    }.toList.asJava
  }

  //the reason we put startWith into #preStart is to allow postRestart to trigger new FSM actor when recover from error
  override def preStart(): Unit = {
    Try{
      register(new MembersInfoBean, prefix + membersInfoName)
      register(new PartitionsInfoBean, prefix + partitionsInfoName)
    }
    startWith(ZkClusterUninitialized, ZkClusterData(None, Set.empty[Address], Map.empty, None))
  }
  
  override def postStop(): Unit = {
    Try{
      unregister(prefix + membersInfoName)
      unregister(prefix + partitionsInfoName)
    }
    zkCluster.close()
  }

  when(ZkClusterUninitialized)(mandatory orElse {
    case Event(ZkLeaderElected(Some(address)), zkClusterData) =>
      log.info("[uninitialized] leader elected:{} and my zk address:{}", address, zkAddress)
      val partitions = zkClusterData.curatorFwk map {implicit fwk =>
        ZkPartitionsManager.loadPartitions()
      } getOrElse zkClusterData.partitions
      if(address.hostPort == zkAddress.hostPort) {
        val rebalanced = rebalance(partitions, partitions, zkClusterData.members)
        goto(ZkClusterActiveAsLeader) using zkClusterData.copy(leader = Some(address), partitions = rebalanced)
      } else {
        goto(ZkClusterActiveAsFollower) using zkClusterData.copy(leader = Some(address), partitions = partitions)
      }
    case Event(ZkMembersChanged(members), zkClusterData) =>
      log.info("[uninitialized] membership updated:{}", members)
      stay() using zkClusterData.copy(members = members)
    case Event(_, _) =>
      stash()
      stay()
  })

  when(ZkClusterActiveAsFollower)(mandatory orElse {
    case Event(ZkLeaderElected(Some(address)), zkClusterData) =>
      if(address.hostPort == zkAddress.hostPort) {
        // in case of leader dies before follower get the whole picture of partitions
        // the follower get elected need to read from Zookeeper
        val partitions = zkClusterData.curatorFwk map {implicit fwk =>
          ZkPartitionsManager.loadPartitions()
        } getOrElse zkClusterData.partitions
        val rebalanced = rebalance(zkClusterData.partitions, partitions, zkClusterData.members)
        goto(ZkClusterActiveAsLeader) using zkClusterData.copy(leader = Some(address), partitions = rebalanced)
      } else {
        goto(ZkClusterActiveAsFollower) using zkClusterData.copy(leader = Some(address))
      }
    case Event(ZkQueryLeadership, zkClusterData) =>
      log.info("[follower] leadership query answered:{} to:{}", zkClusterData.leader, sender().path)
      zkClusterData.leader.foreach(address => sender() ! ZkLeadership(address))
      stay()
    case Event(ZkMembersChanged(members), zkClusterData) =>
      log.info("[follower] membership updated:{}", members)
      stay() using zkClusterData.copy(members = members)
    case Event(origin @ ZkQueryPartition(partitionKey, notification, sizeOpt, props, members), zkClusterData) =>
      log.info("[follower] partition query: {} with expected size {}, current snapshot: {}",
        keyToPath(partitionKey),
        sizeOpt,
        zkClusterData.partitions.map{case (k, v) => keyToPath(k) -> v}
      )
      // There are couple cases regarding the current memory snapshot and the sizeOpt
      // 1. partition not available => ask leader
      // 3. partition available && sizeOpt empty => reply the partition
      // 4. partition available && partition.members.size != min(clusterSize, expectedSize) => ask leader
      // 5. partition available && partition.members.size == min(clusterSize, expectedSize, sizeOpt.getOrElse(expectedSize))
      // 1) sizeOpt.get == expectedSize => reply the partition
      // 2) sizeOpt.get != expectedSize => ask leader
      zkClusterData.partitions.get(partitionKey) match {
        case Some(data @ ZkPartitionData(_, servants, originalExpectedSize, _))
          if sizeOpt.getOrElse(originalExpectedSize) == originalExpectedSize
            && servants.size == Math.min(zkClusterData.members.size, sizeOpt.getOrElse(originalExpectedSize)) =>
          log.info("[follower] answer the partition query using snapshot {}", data)
          sender() ! ZkPartition(partitionKey, servants, partitionZkPath(partitionKey), notification)
          stay()
        case None if sizeOpt.isEmpty =>
          log.info("[follower] partitions {} does not exist for now", keyToPath(partitionKey))
          sender() ! ZkPartitionNotFound(partitionKey)
          stay()
        case _ =>
          log.info("[follower] local snapshot {} wasn't available yet or probably a resize to {}. Forward to {}",
            zkClusterData.partitions.map{case (k, v) => keyToPath(k) -> v},
            sizeOpt,
            zkClusterData.leader
          )
          zkClusterData.leader.foreach(address => {
            context.actorSelection(self.path.toStringWithAddress(address)) forward origin
          })
          stay()
      }
    case Event(ZkPartitionsChanged(_, changes), zkClusterData) =>
      // Only apply the changes that reaches the expected size or the total member size
      val (partitionsToRemove, updates) = changes partition {
        case (key, change) => change.members.isEmpty && change.expectedSize == 0
      }
      log.info("[follower] got the partitions to update {} and partitions to remove {} from partition manager",
        updates.map{case (key, members) => keyToPath(key) -> members},
        partitionsToRemove.map{case (key, members) => keyToPath(key)}
      )
      val newPartitions = zkClusterData.partitions ++ updates -- partitionsToRemove.keys
      if (updates.nonEmpty || partitionsToRemove.nonEmpty) {
        notifyPartitionDiffs(zkClusterData.partitions, newPartitions)("follower")
        // For the partitions without any members, we remove them from the memory map
        stay() using zkClusterData.copy(partitions = newPartitions)
      } else {
        stay()
      }
    case Event(remove:ZkRemovePartition, zkClusterData) =>
      zkClusterData.leader.foreach(address => {
        context.actorSelection(self.path.toStringWithAddress(address)) forward remove
      })
      stay()
  })

  when(ZkClusterActiveAsLeader)(mandatory orElse {
    case Event(ZkLeaderElected(Some(address)), zkClusterData) =>
      if (address.hostPort == zkAddress.hostPort) {
        stay()
      } else {
        goto(ZkClusterActiveAsFollower) using zkClusterData.copy(leader = Some(address))
      }
    case Event(ZkQueryLeadership, zkClusterData) =>
      log.info("[leader] leadership query answered:{} to:{}", zkClusterData.leader, sender().path)
      zkClusterData.leader.foreach(address => sender() ! ZkLeadership(address))
      stay()
    case Event(ZkMembersChanged(members), zkClusterData) =>
      log.info("[leader] membership updated:{}", members)
      if(zkClusterData.members == members){
        //corner case, in which members weren't really changed, avoid redundant re-balances
        stay()
      }
      else {
        val membersLeft = zkClusterData.members.diff(members)
        val partitionWithMemberLost = if(membersLeft.nonEmpty) {
          zkClusterData.partitions.map { case (name, partition) =>
            name -> partition.copy(members = partition.members.diff(membersLeft))
          }
        } else {
          zkClusterData.partitions
        }
        val rebalanced = rebalance(zkClusterData.partitions, partitionWithMemberLost, members)
        stay() using zkClusterData.copy(members = members, partitions = rebalanced)
      }
    case Event(ZkQueryPartition(partitionKey, notification, sizeOpt, props, _), zkClusterData) =>
      log.info("[leader] partition query: {} with expected size {}, current snapshot: {}",
        keyToPath(partitionKey),
        sizeOpt,
        zkClusterData.partitions.map{case (k, v) => keyToPath(k) -> v}
      )
      // There are couple cases regarding the current memory snapshot and the sizeOpt
      // 1. partition not available && sizeOpt not empty => rebalance using sizeOpt.get
      // 2. partition not available && sizeOpt empty => PartitionNotFound
      // 3. partition available && sizeOpt empty => reply the partition
      // 4. partition available && partition.members.size != min(clusterSize, expectedSize) => rebalance using sizeOpt.getOrElse(expectedSize)
      // 5. partition available && partition.members.size == min(clusterSize, expectedSize, sizeOpt.getOrElse(expectedSize))
      // 1) sizeOpt.get == expectedSize => reply the partition
      // 2) sizeOpt.get != expectedSize => resize & reply the partition
      zkClusterData.partitions.get(partitionKey) match {
        case Some(existence @ ZkPartitionData(key, servants, originalExpectedSize, _))
          if servants.size == Math.min(zkClusterData.members.size, sizeOpt.getOrElse(originalExpectedSize))=> // 3, 5.1, 5.2
          log.info("[leader] partition exists:{} -> {}", keyToPath(partitionKey), servants)
          sender() ! ZkPartition(partitionKey, servants, partitionZkPath(partitionKey), notification)
          if (sizeOpt.nonEmpty && sizeOpt.get != originalExpectedSize) {
            zkPartitionsManager ! ZkResizePartition(key, sizeOpt.get)
            stay() using zkClusterData.copy(
              partitions = zkClusterData.partitions
                + (key -> existence.copy(expectedSize = sizeOpt.getOrElse(originalExpectedSize)))
            )
          } else {
            stay()
          }
        case None if sizeOpt.isEmpty => // 2
          log.info("[leader] partition does not exists:{}", keyToPath(partitionKey))
          sender() ! ZkPartitionNotFound(partitionKey)
          stay()
        case partitionOpt => // 1, 4
          log.info("[leader] partition creation or resize:{}", keyToPath(partitionKey))
          val expectedSize = sizeOpt getOrElse partitionOpt.get.expectedSize
          val rebalanced = rebalance(
            zkClusterData.partitions,
            zkClusterData.partitions +
              (partitionKey -> ZkPartitionData(partitionKey, expectedSize = expectedSize, props = props)),
            zkClusterData.members
          )
          sender() ! ZkPartition(
            partitionKey,
            rebalanced.get(partitionKey).map(_.members).getOrElse(Set.empty[Address]),
            partitionZkPath(partitionKey),
            notification
          )
          stay() using zkClusterData.copy(partitions = rebalanced)
      }
    case Event(ZkPartitionsChanged(_, changes), zkClusterData) =>
      log.debug("[leader] I don't really care about changes {} because my snapshot {}",
        changes.map { case (key, members) => keyToPath(key) -> members},
        zkClusterData.partitions.map { case (key, members) => keyToPath(key) -> members}
      )
      stay()
    case Event(remove @ ZkRemovePartition(partitionKey), zkClusterData) =>
      log.info("[leader] remove partition:{} forwarded to partition manager", keyToPath(partitionKey))
      zkPartitionsManager forward remove
      val newPartitions = zkClusterData.partitions - partitionKey
      notifyPartitionDiffs(zkClusterData.partitions, newPartitions)("leader")
      stay() using zkClusterData.copy(partitions = newPartitions)
  })

  onTransition {
    case ZkClusterUninitialized -> ZkClusterActiveAsFollower =>
      //unstash all messages uninitialized state couldn't handle
      unstashAll()
    case ZkClusterUninitialized -> ZkClusterActiveAsLeader =>
      //unstash all messages uninitialized state couldn't handle
      unstashAll()
  }

  private[cluster] def rebalance(current: Map[ByteString, ZkPartitionData],
                                 base: Map[ByteString, ZkPartitionData],
                                 members:Set[Address]): Map[ByteString, ZkPartitionData] = {
    //spareLeader only when there are more than 1 VMs in the cluster
    val candidates = if(rebalanceLogic.spareLeader && members.size > 1) {
      members.filterNot { candidate => stateData.leader contains candidate }
    } else {
      members
    }
    val plan = rebalanceLogic.rebalance(
      rebalanceLogic.compensate(
        base map { case(key, value) => key -> value.members },
        candidates.toSeq,
        (key) => base.get(key).get.expectedSize
      ),
      members
    )
    log.info("[leader] rebalance planned as:{}", plan map { case (key, memberSet) => keyToPath(key) -> memberSet })
    val rebalanced = base map {
      case (key, value) => key -> value.copy(members = plan.getOrElse(key, value.members))
    }
    notifyPartitionDiffs(current, rebalanced)("leader")
    val updates = rebalanced filterNot {
      case (key, value) if current.get(key).nonEmpty => value == current(key)
      case _ => false
    }
    if (updates.nonEmpty) zkPartitionsManager ! ZkRebalance(updates)
    rebalanced
  }

  private[cluster] def notifyPartitionDiffs(originalPartitions: Map[ByteString, ZkPartitionData],
                                            changes: Map[ByteString, ZkPartitionData])
                                           (role: String = "follower") = {
    val partitionsToRemove = originalPartitions.keySet diff changes.keySet map {partitionKey =>
      val dropOffMembers = originalPartitions.get(partitionKey).map(_.members).getOrElse(Set.empty)
      if (dropOffMembers.nonEmpty) Some(ZkPartitionDiff(partitionKey, Set.empty, dropOffMembers)) else None
    } collect {
      case Some(remove) => remove
    } toList

    val partitionDiffs = (changes map {
      case(partitionKey, ZkPartitionData(_, newMembers, _, props)) =>
        val originalMembers = originalPartitions.get(partitionKey).map(_.members).getOrElse(Set.empty[Address])
        val onBoardMembers = newMembers diff originalMembers
        val dropOffMembers = originalMembers diff newMembers
        if (onBoardMembers.nonEmpty || dropOffMembers.nonEmpty) {
          Some(ZkPartitionDiff(partitionKey, onBoardMembers, dropOffMembers, props))
        } else {
          None
        }
    } collect {
      case Some(diff) => diff
    } toList) ++ partitionsToRemove

    if (partitionDiffs.nonEmpty) {
      log.debug("[{}] notify {} about the the partition changes {}",
        role,
        whenPartitionUpdated,
        partitionDiffs.map(diff => (pathToKey(diff.partitionKey), diff.onBoardMembers, diff.dropOffMembers))
      )
      whenPartitionUpdated foreach (_ ! partitionDiffs)
    }
  }
}
