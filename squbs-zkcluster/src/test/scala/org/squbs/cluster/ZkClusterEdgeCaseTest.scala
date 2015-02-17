package org.squbs.cluster

import akka.testkit.ImplicitSender
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll, Matchers, FlatSpecLike}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
/**
 * Created by zhuwang on 1/28/15.
 */
class ZkClusterEdgeCaseTest extends ZkClusterMultiActorSystemTestKit("ZkClusterEdgeCaseTest")
  with ImplicitSender with FlatSpecLike with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {
  
  override val timeout: FiniteDuration = 30 seconds
  
  override val clusterSize: Int = 3
  
  override def afterEach(): Unit = {
    println("------------------------------------------------------------------------------------------")
    Thread.sleep(timeout.toMillis / 10)
  }
  
  override def beforeAll = startCluster
  
  override def afterAll = shutdownCluster
  
  "ZkCluster" should "respond to partition creation query whose size is larger than the cluster" in {
    val parKey = ByteString("myPar")
    zkClusterExts(pickASystemRandomly()) tell (ZkQueryPartition(parKey, expectedSize = Some(clusterSize + 1)), self)
    val members = expectMsgType[ZkPartition](timeout).members
    members.map(_.system).toSet should be ((0 until clusterSize) map int2SystemName toSet)
    Thread.sleep(timeout.toMillis / 10)
    zkClusterExts foreach {
      case (_, ext) => ext tell (ZkQueryPartition(parKey), self)
        expectMsgType[ZkPartition](timeout).members.map(_.system).toSet should be ((0 until clusterSize) map int2SystemName toSet)
    }
  }
  
  "ZkCluster" should "respond to partition creation query whose size is exactly the cluster size" in {
    val parKey = ByteString("myPar")
    zkClusterExts(pickASystemRandomly()) tell (ZkQueryPartition(parKey, expectedSize = Some(clusterSize)), self)
    val members = expectMsgType[ZkPartition](timeout).members
    members.map(_.system).toSet should be ((0 until clusterSize) map int2SystemName toSet)
    Thread.sleep(timeout.toMillis / 10)
    zkClusterExts foreach {
      case (_, ext) => ext tell (ZkQueryPartition(parKey), self)
        expectMsgType[ZkPartition](timeout).members.map(_.system).toSet should be ((0 until clusterSize) map int2SystemName toSet)
    }
  }
  
  "ZkCluster" should "be able to resize a all members partition to a larger size" in {
    val parKey = ByteString("myPar")
    zkClusterExts(pickASystemRandomly()) tell (ZkQueryPartition(parKey, expectedSize = Some(clusterSize + 2)), self)
    val members = expectMsgType[ZkPartition](timeout).members
    members.map(_.system).toSet should be ((0 until clusterSize) map int2SystemName toSet)
    Thread.sleep(timeout.toMillis / 10)
    zkClusterExts foreach {
      case (_, ext) => ext tell (ZkQueryPartition(parKey), self)
        expectMsgType[ZkPartition](timeout).members.map(_.system).toSet should be ((0 until clusterSize) map int2SystemName toSet)
    }
  }
  "ZkCluster" should "be able to resize a partition from the size larger than cluster size to the cluster size" in {
    val parKey = ByteString("myPar")
    zkClusterExts(pickASystemRandomly()) tell (ZkQueryPartition(parKey, expectedSize = Some(clusterSize)), self)
    val members = expectMsgType[ZkPartition](timeout).members
    members.map(_.system).toSet should be ((0 until clusterSize) map int2SystemName toSet)
    Thread.sleep(timeout.toMillis / 10)
    zkClusterExts foreach {
      case (_, ext) => ext tell (ZkQueryPartition(parKey), self)
        expectMsgType[ZkPartition](timeout).members.map(_.system).toSet should be ((0 until clusterSize) map int2SystemName toSet)
    }
  }
  
  "ZkCluster" should "rebalance if the partition size decreased" in {
    val parKey = ByteString("myPar")
    zkClusterExts(pickASystemRandomly()) tell (ZkQueryPartition(parKey, expectedSize = Some(clusterSize - 1)), self)
    expectMsgType[ZkPartition](timeout).members.size should be (clusterSize - 1)
    Thread.sleep(timeout.toMillis / 10)
    zkClusterExts foreach {
      case (_, ext) => ext tell (ZkQueryPartition(parKey), self)
        expectMsgType[ZkPartition](timeout).members.size should be (clusterSize - 1)
    }
  }
  "ZkCluster" should "rebalance if the partition size increased" in {
    val parKey = ByteString("myPar")
    zkClusterExts(pickASystemRandomly()) tell (ZkQueryPartition(parKey, expectedSize = Some(clusterSize)), self)
    expectMsgType[ZkPartition](timeout).members.size should be (clusterSize)
    Thread.sleep(timeout.toMillis / 10)
    zkClusterExts foreach {
      case (_, ext) => ext tell (ZkQueryPartition(parKey), self)
        expectMsgType[ZkPartition](timeout).members.size should be (clusterSize)
    }
  }
  
  "ZkCluster" should "rebalance if the full member partition if follower dies" in {
    // query the leader
    zkClusterExts(pickASystemRandomly()) tell (ZkQueryLeadership, self)
    val leaderName = expectMsgType[ZkLeadership](timeout).address.system
    println(s"Now leader is $leaderName")
    // kill any follower
    val unluckyGuy = pickASystemRandomly(Some(leaderName))
    killSystem(unluckyGuy)
    Thread.sleep(timeout.toMillis / 10)
    // rebalanced partition should be consistent across cluster
    val parKey = ByteString("myPar")
    zkClusterExts foreach {
      case (_, ext) => ext tell (ZkQueryPartition(parKey), self)
        val members = expectMsgType[ZkPartition](timeout).members
        members.size should be (clusterSize - 1)
        members.map(_.system) should not contain(unluckyGuy)
    }
  }
  "ZkCluster" should "rebalance the full member partition if leader dies" in {
    // query the leader
    zkClusterExts(pickASystemRandomly()) tell (ZkQueryLeadership, self)
    val leaderName = expectMsgType[ZkLeadership](timeout).address.system
    println(s"Now leader is $leaderName")
    // kill the leader
    killSystem(leaderName)
    Thread.sleep(timeout.toMillis / 10)
    // rebalanced partition should be consistent across cluster
    val parKey = ByteString("myPar")
    zkClusterExts foreach {
      case (_, ext) => ext tell (ZkQueryPartition(parKey), self)
        val members = expectMsgType[ZkPartition](timeout).members
        members.size should be (clusterSize - 2)
        members.map(_.system) should not contain(leaderName)
    }
  }
}