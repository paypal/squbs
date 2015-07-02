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
import akka.actor.Address
import akka.util.ByteString
import org.apache.curator.framework.CuratorFramework

/**
 * request for leader identity of the cluster
 */
case object ZkQueryLeadership
/**
 * request for members identities of the cluster
 */
case object ZkQueryMembership
/**
 * subscribe to zkclient updates
 */
case object ZkMonitorClient
/**
 * response for leader identity query
 * @param address
 */
case class ZkLeadership(address: Address)
/**
 * response for members identities query
 * @param members
 */
case class ZkMembership(members: Set[Address])
/**
 * event when zkclient updates
 * @param zkClient
 */
case class ZkClientUpdated(zkClient:CuratorFramework)
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
                            props:Array[Byte] = Array[Byte](),
                            members:Set[Address] = Set.empty) {
  if (expectedSize.nonEmpty) require(expectedSize.get > 0)
}
/**
 * request to discontinue a partition
 * @param partitionKey
 */
case class ZkRemovePartition(partitionKey:ByteString)
/**
 * subscribe to partition updates
 */
case object ZkMonitorPartition
/**
 * stop subscription to a partition's updates
 */
case object ZkStopMonitorPartition
/**
 * event of partition update
 * @param partitionKey
 * @param onBoardMembers
 * @param dropOffMembers
 */
case class ZkPartitionDiff(partitionKey: ByteString, 
                           onBoardMembers: Set[Address], 
                           dropOffMembers: Set[Address], 
                           props: Array[Byte] = Array.empty)
/**
 * event of a partition removal
 * @param partitionKey
 */
case class ZkPartitionRemoval(partitionKey:ByteString)
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
/**
 * response for partition query
 * @param partitionKey
 */
case class ZkPartitionNotFound(partitionKey: ByteString)
/**
 * request for VM's enrolled partitions
 * @param address
 */
case class ZkListPartitions(address: Address)
/**
 * response for list partitions query
 * @param partitionKeys
 */
case class ZkPartitions(partitionKeys:Seq[ByteString])

/**
 * Lifecycle events corresponding to CONNECTED, RECONNECTED, SUSPENDED, LOST state in Curator Framework
 */
case object ZkConnected
case object ZkReconnected
case object ZkSuspended
case object ZkLost