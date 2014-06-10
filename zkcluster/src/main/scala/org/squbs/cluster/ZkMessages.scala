package org.squbs.cluster

import org.apache.curator.framework.CuratorFramework
import akka.actor.{ActorPath, Address}
import akka.util.ByteString

/**
 * Created by huzhou on 6/9/14.
 */

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
 * @param notification a pending message
 * @param createOnMiss how many members are required for the partition to be built when it's missing
 * @param props
 * @param members don't assign anything, used internally
 */
case class ZkQueryPartition(partitionKey:ByteString, //partition key
                            notification:Option[Any] = None, //notify the sender() along with query result
                            createOnMiss:Option[Int] = None, //create partition when it's missing or not, and the size in case it's to be created
                            props:Array[Byte] = Array[Byte](), //properties of the partition, plain byte array
                            members:Set[Address] = Set.empty) //used internally

/**
 * request to expand or shrink a partition
 * @param partitionKey
 * @param sizeOf
 */
case class ZkResizePartition(partitionKey:ByteString, sizeOf:Int)

/**
 * request to discontinue a partition
 * @param partitionKey
 */
case class ZkRemovePartition(partitionKey:ByteString)

/**
 * subscribe to partition updates
 * @param onDifference
 */
case class ZkMonitorPartition(onDifference:Set[ActorPath] = Set.empty) //notify me when partitions have changes @see ZkPartitionsChanged

/**
 * stop subscription to a partition's updates
 * @param onDifference
 */
case class ZkStopMonitorPartition(onDifference:Set[ActorPath] = Set.empty) //stop notify me when partitions have changes @see ZkPartitionsChanged

/**
 * event of a partition updates
 * @param diff
 * @param zkPaths
 */
case class ZkPartitionDiff(diff:Map[ByteString, Seq[Address]], zkPaths:Map[ByteString, String])

/**
 * response for partition query
 * @param partitionKey
 * @param members
 * @param zkPath
 * @param notification
 */
case class ZkPartition(partitionKey:ByteString,
                       members: Seq[Address],   //who have been assigned to be part of this partition
                       zkPath:String,           //where the partition data is stored
                       notification:Option[Any])//optional notification when the query was issued

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