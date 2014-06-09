package org.squbs.cluster

import org.apache.curator.framework.CuratorFramework
import akka.actor.{ActorPath, Address}
import akka.util.ByteString

/**
 * Created by huzhou on 6/9/14.
 */

case object ZkQueryLeadership
case object ZkQueryMembership
case object ZkMonitorClient

case class ZkClientUpdated(zkClient:CuratorFramework)
case class ZkLeadership(address: Address)
case class ZkMembership(members: Set[Address])

case class ZkQueryPartition(partitionKey:ByteString, //partition key
                            notification:Option[Any] = None, //notify the sender() along with query result
                            createOnMiss:Option[Int] = None, //create partition when it's missing or not, and the size in case it's to be created
                            props:Array[Byte] = Array[Byte](), //properties of the partition, plain byte array
                            members:Set[Address] = Set.empty) //used internally

case class ZkResizePartition(partitionKey:ByteString, sizeOf:Int)

case class ZkRemovePartition(partitionKey:ByteString)

case class ZkMonitorPartition(onDifference:Set[ActorPath] = Set.empty) //notify me when partitions have changes @see ZkPartitionsChanged

case class ZkStopMonitorPartition(onDifference:Set[ActorPath] = Set.empty) //stop notify me when partitions have changes @see ZkPartitionsChanged

case class ZkPartitionDiff(diff:Map[ByteString, Seq[Address]], zkPaths:Map[ByteString, String])

case class ZkPartition(partitionKey:ByteString,
                       members: Seq[Address],   //who have been assigned to be part of this partition
                       zkPath:String,           //where the partition data is stored
                       notification:Option[Any])//optional notification when the query was issued