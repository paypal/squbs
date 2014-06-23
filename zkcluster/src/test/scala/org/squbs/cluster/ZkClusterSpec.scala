package org.squbs.cluster

import java.io.File

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.ByteString
import com.google.common.base.Charsets
import com.google.common.io.Files
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import org.squbs.unicomplex.{ConfigUtil, Unicomplex}

import scala.concurrent.duration._

/**
 * Created by huzhou on 5/12/14.
 */
class ZkClusterSpec extends TestKit(ActorSystem("zkcluster")) with FlatSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll {

  var preserve:Option[String] = None
  val conf = new File(Unicomplex(system).externalConfigDir, "zkcluster.conf")
  var zk:ZkActorForTestOnly = null

  override def beforeAll = {

    import scala.collection.JavaConversions._
    if(conf.exists){
      preserve = Some(Files.readLines(conf, Charsets.UTF_8).mkString("\n"))
    }
    Files.createParentDirs(conf)
    Files.write(
      s"""
          |zkCluster {
          |    connectionString = "localhost:2181"
          |    namespace = "zkclusterunitest-${System.nanoTime}"
          |    segments = 16
          |}
        """.stripMargin, conf, Charsets.UTF_8)

    zk = new ZkActorForTestOnly(Unicomplex(system).externalConfigDir)
    zk.startup(2181)
  }

  override def afterAll = {

    val zkClient = ZkCluster(system).zkClientWithNs

    system.shutdown

    safelyDiscard("")(zkClient)

    zk.postStop

    try {
      Runtime.getRuntime.exec("netstat -anp | grep :2181 | grep ESTABLISHED | awk {'print $7}' | awk -F '/' {'print $1'} | xargs kill -9")
    }
    catch{
      case e:Exception => //ignored
    }

    system.awaitTermination

    //find zk process if it sticks


    preserve match {
      case None => conf.delete
      case Some(value) => Files.write(value, conf, Charsets.UTF_8)
    }
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

    implicit val timeout = 60.second

    val extension = ZkCluster(system)
    val cluster = extension.zkClusterActor
    val partitionKey = ByteString(s"pk-${System.nanoTime}")

    cluster ! ZkQueryPartition(partitionKey, None, Some(1))
    expectMsgType[ZkPartition](timeout).members.size should equal(1)

    cluster ! ZkMonitorPartition(Set(self.path))
    expectMsgType[ZkPartitionDiff].diff should equal(Map(partitionKey -> Seq(extension.zkAddress)))

    val zkPath = extension.segmentationLogic.partitionZkPath(partitionKey)
    val members = extension.zkClientWithNs.getChildren.forPath(zkPath)
    members.contains("$size") should equal(true)
    members.remove("$size")

    if (members.nonEmpty) {
      extension.zkClientWithNs.delete.forPath(s"$zkPath/${members.head}")
      expectMsgType[ZkPartitionDiff].diff should equal(Map(partitionKey -> Seq.empty[Address]))
    }

    cluster ! ZkListPartitions(extension.zkAddress)
    expectMsgType[ZkPartitions] shouldNot equal(Seq.empty[ByteString])
  }
}
