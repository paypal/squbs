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
import akka.actor.Address
import akka.util.ByteString
import org.apache.curator.framework.CuratorFramework

/** Marker trait for all ZkMessages for easy serialization */
sealed trait ZkMessages
/**
 * request for leader identity of the cluster
 */
case object ZkQueryLeadership extends ZkMessages
/**
 * request for members identities of the cluster
 */
case object ZkQueryMembership extends ZkMessages
/**
 * subscribe to zkclient updates
 */
case object ZkMonitorClient extends ZkMessages
/**
 * response for leader identity query
 * @param address
 */
case class ZkLeadership(address: Address) extends ZkMessages
/**
 * response for members identities query
 * @param members
 */
case class ZkMembership(members: Set[Address]) extends ZkMessages
/**
 * event when zkclient updates
 * @param zkClient
 */
case class ZkClientUpdated(zkClient:CuratorFramework) extends ZkMessages
/**
 * request for partition members
 * @param partitionKey
 * @param notification notify the sender along with query result
 * @param expectedSize create the partition or resize the partition with the suggested size
 * @param props properties of the partition, plain byte array
 * @param members don't assign anything, used internally
 */
case class ZkQueryPartition(partitionKey:ByteString,
                            notification:Option[Any] = None,
                            expectedSize:Option[Int] = None,
                            props:Array[Byte] = Array.empty,
                            members:Set[Address] = Set.empty) extends ZkMessages {
  if (expectedSize.nonEmpty) require(expectedSize.get > 0)
}
/**
 * request to discontinue a partition
 * @param partitionKey
 */
case class ZkRemovePartition(partitionKey:ByteString) extends ZkMessages
/**
 * subscribe to partition updates
 */
case object ZkMonitorPartition extends ZkMessages
/**
 * stop subscription to a partition's updates
 */
case object ZkStopMonitorPartition extends ZkMessages
/**
 * event of partition update
 * @param partitionKey
 * @param onBoardMembers
 * @param dropOffMembers
 */
case class ZkPartitionDiff(partitionKey: ByteString,
                           onBoardMembers: Set[Address],
                           dropOffMembers: Set[Address],
                           props: Array[Byte] = Array.empty) extends ZkMessages
/**
 * event of a partition removal
 * @param partitionKey
 */
case class ZkPartitionRemoval(partitionKey:ByteString) extends ZkMessages
/**
 * response for partition query
 * @param partitionKey
 * @param members
 * @param zkPath
 * @param notification
 */
case class ZkPartition(partitionKey:ByteString,
                       members: Set[Address], //who have been assigned to be part of this partition
                       zkPath:String, //where the partition data is stored
                       notification:Option[Any])//optional notification when the query was issued
  extends ZkMessages
/**
 * response for partition query
 * @param partitionKey
 */
case class ZkPartitionNotFound(partitionKey: ByteString) extends ZkMessages
/**
 * request for VM's enrolled partitions
 * @param address
 */
case class ZkListPartitions(address: Address) extends ZkMessages
/**
 * response for list partitions query
 * @param partitionKeys
 */
case class ZkPartitions(partitionKeys:Seq[ByteString]) extends ZkMessages

/**
 * Lifecycle events corresponding to CONNECTED, RECONNECTED, SUSPENDED, LOST state in Curator Framework
 */
case object ZkConnected extends ZkMessages
case object ZkReconnected extends ZkMessages
case object ZkSuspended extends ZkMessages
case object ZkLost extends ZkMessages
case class ZkConfigChanged(connStr: String) extends ZkMessages
