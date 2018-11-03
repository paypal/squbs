package org.squbs.cluster

import java.util.concurrent.TimeUnit

import akka.testkit.ImplicitSender
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.zookeeper.CreateMode
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FlatSpecLike, Matchers}

import scala.concurrent.duration._
/**
 * Created by zhuwang on 1/28/15.
 */
class ZkClusterInitTest extends ZkClusterMultiActorSystemTestKit("ZkClusterInitTest") with LazyLogging
  with ImplicitSender with FlatSpecLike with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {
  import org.squbs.cluster.ZkClusterMultiActorSystemTestKit._
  
  override val timeout: FiniteDuration = 30 seconds
  
  override val clusterSize: Int = 6
  
  override def afterEach(): Unit = {
    println("------------------------------------------------------------------------------------------")
    Thread.sleep(1000)
  }
  
  val par1 = ByteString("myPar1")
  val par2 = ByteString("myPar2")
  val par3 = ByteString("myPar3")
  
  implicit val log = logger
  implicit def string2ByteArray(s: String) = s.toCharArray map (c => c.toByte)
  implicit def ByteArray2String(array: Array[Byte]) = (for (i <- 0 until array.length) yield array(i).toChar).mkString
  
  override def beforeAll = {
    // Don't need to start the cluster for now
    // We preset the data in Zookeeper instead.
    val zkClient = CuratorFrameworkFactory.newClient(
      zkConfig.getString("zkCluster.connectionString"),
      new ExponentialBackoffRetry(1000, 3))
    zkClient.start
    zkClient.blockUntilConnected(30, TimeUnit.SECONDS) shouldBe true
    implicit val zkClientWithNS = zkClient.usingNamespace(zkConfig.getString("zkCluster.namespace"))
    guarantee("/leader", Some(Array[Byte]()), CreateMode.PERSISTENT)
    guarantee("/members", Some(Array[Byte]()), CreateMode.PERSISTENT)
    guarantee("/segments", Some(Array[Byte]()), CreateMode.PERSISTENT)
    guarantee("/segments/segment-0", Some(Array[Byte]()), CreateMode.PERSISTENT)
    guarantee(s"/segments/segment-0/${keyToPath(par1)}", Some("myPar1"), CreateMode.PERSISTENT)
    guarantee(s"/segments/segment-0/${keyToPath(par1)}/servants", None, CreateMode.PERSISTENT)
    guarantee(s"/segments/segment-0/${keyToPath(par1)}/$$size", Some(3), CreateMode.PERSISTENT)
    guarantee(s"/segments/segment-0/${keyToPath(par2)}", Some("myPar2"), CreateMode.PERSISTENT)
    guarantee(s"/segments/segment-0/${keyToPath(par2)}/servants", None, CreateMode.PERSISTENT)
    guarantee(s"/segments/segment-0/${keyToPath(par2)}/$$size", Some(3), CreateMode.PERSISTENT)
    guarantee(s"/segments/segment-0/${keyToPath(par3)}", Some("myPar3"), CreateMode.PERSISTENT)
    guarantee(s"/segments/segment-0/${keyToPath(par3)}/servants", None, CreateMode.PERSISTENT)
    guarantee(s"/segments/segment-0/${keyToPath(par3)}/$$size", Some(3), CreateMode.PERSISTENT)
    zkClient.close
  }
  
  override def afterAll = shutdownCluster
  
  "ZkCluster" should "list the partitions" in {
    startCluster
    zkClusterExts foreach {
      case (_, ext) => ext tell (ZkListPartitions(ext.zkAddress), self)
        println(expectMsgType[ZkPartitions](timeout))
    }
  }
  
  "ZkCluster" should "load persisted partition information and sync across the cluster" in {
    zkClusterExts foreach {
      case (_, ext) => ext tell (ZkQueryPartition(par1), self)
        expectMsgType[ZkPartition](timeout).members.size should be (3)
    }
    zkClusterExts foreach {
      case (_, ext) => ext tell (ZkQueryPartition(par2), self)
        expectMsgType[ZkPartition](timeout).members.size should be (3)
    }
    zkClusterExts foreach {
      case (_, ext) => ext tell (ZkQueryPartition(par3), self)
        expectMsgType[ZkPartition](timeout).members.size should be (3)
    }
  }
  
  "ZkCluster" should "list all the members across the cluster" in {
    val members = zkClusterExts.map(_._2.zkAddress).toSet
    zkClusterExts foreach {
      case (_, ext) => ext tell (ZkQueryMembership, self)
        expectMsgType[ZkMembership](timeout).members should be (members)
    }
  }
}
