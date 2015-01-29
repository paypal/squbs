package org.squbs.cluster

import akka.actor._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.apache.curator.RetryPolicy
import org.apache.curator.framework.state.{ConnectionState, ConnectionStateListener}
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.zookeeper.CreateMode

import scala.collection.JavaConversions._

/**
 * Created by huzhou on 3/25/14.
 */
case class ZkCluster(zkAddress: Address,
                     zkConnectionString: String,
                     zkNamespace: String,
                     segmentationLogic: SegmentationLogic,
                     retryPolicy: RetryPolicy = new ExponentialBackoffRetry(1000, 3),
                     rebalanceLogic: RebalanceLogic = DataCenterAwareRebalanceLogic(spareLeader = false))
                    (implicit system: ActorSystem) extends Extension with LazyLogging {
  
  private[this] implicit val log = logger
  private[this] var zkClient = CuratorFrameworkFactory.newClient(zkConnectionString, retryPolicy)
  private[this] var stopped = false

  lazy val zkClusterActor = system.actorOf(Props[ZkClusterActor], "zkCluster")
  
  zkClient.getConnectionStateListenable.addListener(new ConnectionStateListener {
    override def stateChanged(client: CuratorFramework, newState: ConnectionState): Unit = {
      newState match {
        case ConnectionState.LOST if !stopped =>

          logger.error("[zkCluster] connection lost!")
          zkClient = CuratorFrameworkFactory.newClient(zkConnectionString, retryPolicy)
          zkClient.getConnectionStateListenable.addListener(this)
          zkClient.start
          zkClient.blockUntilConnected

          initialize

          zkClusterActor ! ZkClientUpdated(zkClientWithNs)
        case _ =>
      }
    }
  })
  zkClient.start
  zkClient.blockUntilConnected


  //this is the zk client that we'll use, using the namespace reserved throughout
  implicit def zkClientWithNs = zkClient.usingNamespace(zkNamespace)
  
  initialize

  //all interactions with the zk cluster extension should be through the zkClusterActor below
  private[this] def initialize = {
    //make sure /leader, /members, /segments znodes are available
    guarantee("/leader",   Some(Array[Byte]()), CreateMode.PERSISTENT)
    guarantee("/members",  Some(Array[Byte]()), CreateMode.PERSISTENT)
    guarantee("/segments", Some(Array[Byte]()), CreateMode.PERSISTENT)

    val segmentsSize = zkClientWithNs.getChildren.forPath("/segments").size()
    if (segmentsSize != segmentationLogic.segmentsSize) {
      0.until(segmentationLogic.segmentsSize).foreach(s => {
        guarantee(s"/segments/segment-$s", Some(Array[Byte]()), CreateMode.PERSISTENT)
      })
    }
  }

  def close = {
    stopped = true
    zkClient.close
  }
}

object ZkCluster extends ExtensionId[ZkCluster] with ExtensionIdProvider with LazyLogging {

  override def lookup(): ExtensionId[_ <: Extension] = ZkCluster

  override def createExtension(system: ExtendedActorSystem): ZkCluster = {
    val configuration = system.settings.config withFallback(ConfigFactory.parseMap(Map(
      "zkCluster.segments" -> Int.box(128),
      "zkCluster.spareLeader" -> Boolean.box(false))))

    val zkConnectionString = configuration.getString("zkCluster.connectionString")
    val zkNamespace = configuration.getString("zkCluster.namespace")
    val zkSegments = configuration.getInt("zkCluster.segments")
    val zkSpareLeader = configuration.getBoolean("zkCluster.spareLeader")
    val zkAddress = external(system)
    logger.info("[zkcluster] connection to:{} and namespace:{} with segments:{} using address:{}", 
      zkConnectionString, zkNamespace, zkSegments.toString, zkAddress)

    new ZkCluster(
      zkAddress, 
      zkConnectionString, 
      zkNamespace,
      DefaultSegmentationLogic(zkSegments),
      rebalanceLogic = DataCenterAwareRebalanceLogic(spareLeader = zkSpareLeader)
    )(system)
  }

  private[cluster] def external(system:ExtendedActorSystem):Address = 
    Address("akka.tcp", system.name, ConfigUtil.ipv4, system.provider.getDefaultAddress.port.getOrElse(8086))
}