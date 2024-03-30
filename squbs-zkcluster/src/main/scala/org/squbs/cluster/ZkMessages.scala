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
import org.apache.pekko.actor.Address
import org.apache.pekko.util.ByteString
import org.apache.curator.framework.CuratorFramework

/** Marker trait for all ZkMessages for easy serialization */
@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
sealed trait ZkMessages
/**
 * request for leader identity of the cluster
 */
@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
case object ZkQueryLeadership extends ZkMessages
/**
 * request for members identities of the cluster
 */
@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")case object ZkQueryMembership extends ZkMessages
/**
 * subscribe to zkclient updates
 */
@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
case object ZkMonitorClient extends ZkMessages
/**
 * response for leader identity query
 * @param address
 */
@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
case class ZkLeadership(address: Address) extends ZkMessages
/**
 * response for members identities query
 * @param members
 */
@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
case class ZkMembership(members: Set[Address]) extends ZkMessages
/**
 * event when zkclient updates
 * @param zkClient
 */
@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
case class ZkClientUpdated(zkClient:CuratorFramework) extends ZkMessages
/**
 * request for partition members
 * @param partitionKey
 * @param notification notify the sender along with query result
 * @param expectedSize create the partition or resize the partition with the suggested size
 * @param props properties of the partition, plain byte array
 * @param members don't assign anything, used internally
 */
@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
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
@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
case class ZkRemovePartition(partitionKey:ByteString) extends ZkMessages
/**
 * subscribe to partition updates
 */
@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
case object ZkMonitorPartition extends ZkMessages
/**
 * stop subscription to a partition's updates
 */
@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
case object ZkStopMonitorPartition extends ZkMessages
/**
 * event of partition update
 * @param partitionKey
 * @param onBoardMembers
 * @param dropOffMembers
 */
@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
case class ZkPartitionDiff(partitionKey: ByteString,
                           onBoardMembers: Set[Address],
                           dropOffMembers: Set[Address],
                           props: Array[Byte] = Array.empty) extends ZkMessages
/**
 * event of a partition removal
 * @param partitionKey
 */
@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
case class ZkPartitionRemoval(partitionKey:ByteString) extends ZkMessages
/**
 * response for partition query
 * @param partitionKey
 * @param members
 * @param zkPath
 * @param notification
 */
@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
case class ZkPartition(partitionKey:ByteString,
                       members: Set[Address], //who have been assigned to be part of this partition
                       zkPath:String, //where the partition data is stored
                       notification:Option[Any])//optional notification when the query was issued
  extends ZkMessages
/**
 * response for partition query
 * @param partitionKey
 */
@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
case class ZkPartitionNotFound(partitionKey: ByteString) extends ZkMessages
/**
 * request for VM's enrolled partitions
 * @param address
 */
@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
case class ZkListPartitions(address: Address) extends ZkMessages
/**
 * response for list partitions query
 * @param partitionKeys
 */
@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
case class ZkPartitions(partitionKeys:Seq[ByteString]) extends ZkMessages

/**
 * Lifecycle events corresponding to CONNECTED, RECONNECTED, SUSPENDED, LOST state in Curator Framework
 */
@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
case object ZkConnected extends ZkMessages

@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
case object ZkReconnected extends ZkMessages

@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
case object ZkSuspended extends ZkMessages

@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
case object ZkLost extends ZkMessages

@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
case class ZkConfigChanged(connStr: String) extends ZkMessages
