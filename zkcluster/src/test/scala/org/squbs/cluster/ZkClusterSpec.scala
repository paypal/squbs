package org.squbs.cluster

import org.scalatest.{FlatSpecLike, BeforeAndAfterAll, Matchers}
import scala.concurrent.duration._
import java.io.File
import com.google.common.io.Files
import com.google.common.base.Charsets
import akka.testkit.{TestKit, ImplicitSender}
import akka.util.ByteString
import akka.actor.{Address, ActorSystem}
import org.squbs.unicomplex.{Unicomplex, ConfigUtil}

/**
 * Created by huzhou on 5/12/14.
 */
class ZkClusterSpec extends TestKit(ActorSystem("zkcluster")) with FlatSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll {

  var preserve:Option[String] = None
  val conf = new File(Unicomplex(system).externalConfigDir, "zkcluster.conf")

  override def beforeAll = {

    if(conf.exists){
      import scala.collection.JavaConversions._
      preserve = Some(Files.readLines(conf, Charsets.UTF_8).mkString("\n"))
    }
    Files.createParentDirs(conf)
    Files.write(
      s"""
          |zkCluster {
          |    connectionString = "zk-node-phx-0-213163.phx-os1.stratus.dev.ebay.com:2181,zk-node-phx-1-213164.phx-os1.stratus.dev.ebay.com:2181,zk-node-phx-2-213165.phx-os1.stratus.dev.ebay.com:2181"
          |    namespace = "pubsubunitest-${System.nanoTime}"
          |    segments = 16
          |}
        """.stripMargin, conf, Charsets.UTF_8)
  }

  override def afterAll = {

    ZkCluster.safelyDiscard("")(ZkCluster(system).zkClientWithNs)

    preserve match {
      case None => conf.delete
      case Some(value) => Files.write(value, conf, Charsets.UTF_8)
    }

    system.shutdown()
  }

  "ZkCluster" should "start and connect to zookeeper" in {

    val zk = ZkCluster(system)
    zk shouldNot be(null)

    zk.zkAddress shouldNot be(null)
    zk.zkClientWithNs shouldNot be(null)

    zk.zkMembershipMonitor shouldNot be(null)
    zk.zkPartitionsManager shouldNot be(null)
    zk.zkClusterActor shouldNot be(null)
  }

  "ZkAddress" should "be the ip address of myself" in {

    ZkCluster(system).zkAddress.host should equal(Some(ConfigUtil.ipv4))
  }

  "ZkClusterActor" should "respond with basic queries" in {

    val cluster = ZkCluster(system).zkClusterActor

    cluster ! ZkQueryLeadership
    expectMsgType[ZkLeadership](1.minute)

    cluster ! ZkQueryMembership
    expectMsgType[ZkMembership].members.nonEmpty should equal(true)
  }

  "ZkClusterActor" should "create partition ondemand" in {

    val cluster = ZkCluster(system).zkClusterActor
    val partitionKey = ByteString(s"pk-${System.nanoTime}")

    cluster ! ZkQueryPartition(partitionKey)
    expectMsgType[ZkPartition].members.isEmpty should equal(true)

    cluster ! ZkQueryPartition(partitionKey, None, Some(1))
    expectMsgType[ZkPartition].members.size should equal(1)

    cluster ! ZkQueryPartition(partitionKey, Some("notify me"))
    val withNotifyMe = expectMsgType[ZkPartition]
    withNotifyMe.members.size should equal(1)
    withNotifyMe.notification should equal(Some("notify me"))
  }

  "ZkClusterActor" should "allow monitor of partitions changes" in {

    import scala.collection.JavaConversions._

    val timeout = 360.second

    val extension = ZkCluster(system)
    val cluster = extension.zkClusterActor
    val partitionKey = ByteString(s"pk-${System.nanoTime}")

    cluster ! ZkQueryPartition(partitionKey, None, Some(1))
    expectMsgType[ZkPartition](timeout).members.size should equal(1)

    cluster ! ZkMonitorPartition(Set(self.path))

    val zkPath = extension.segmentationLogic.partitionZkPath(partitionKey)
    val members = extension.zkClientWithNs.getChildren.forPath(zkPath)
    members.contains("$size") should equal(true)
    members.remove("$size")

    if (members.nonEmpty) {
      extension.zkClientWithNs.delete.forPath(s"$zkPath/${members.head}")
      expectMsgType[ZkPartitionDiff](timeout).diff should equal(Map(partitionKey -> Seq.empty[Address]))
    }
  }
}
