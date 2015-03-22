package org.squbs.cluster

import java.util.concurrent.atomic.AtomicBoolean

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
  private[this] val stopped = new AtomicBoolean(false)
  private[this] var shutdownListeners = List.empty[() => Unit]

  zkClient.getConnectionStateListenable.addListener(new ConnectionStateListener {
    override def stateChanged(client: CuratorFramework, newState: ConnectionState): Unit = {
      newState match {
        case ConnectionState.LOST if !stopped.get =>
          logger.error("[zkCluster] connection lost!")
          system.eventStream.publish(ZkLost)
          zkClient = CuratorFrameworkFactory.newClient(zkConnectionString, retryPolicy)
          zkClient.getConnectionStateListenable.addListener(this)
          zkClient.start
          zkClient.blockUntilConnected
        case ConnectionState.CONNECTED if !stopped.get =>
          logger.info("[zkCluster] connected send out the notification")
          system.eventStream.publish(ZkConnected)
          initialize
          zkClusterActor ! ZkClientUpdated(zkClientWithNs)
        case ConnectionState.SUSPENDED if !stopped.get =>
          logger.info("[zkCluster] connection suspended suspended")
          system.eventStream.publish(ZkSuspended)
        case ConnectionState.RECONNECTED if !stopped.get =>
          logger.info("[zkCluster] reconnected")
          system.eventStream.publish(ZkReconnected)
          zkClusterActor ! ZkClientUpdated(zkClientWithNs)
        case otherState => 
          logger.warn(s"[zkCluster] connection state changed $otherState. What shall I do?")
      }
    }
  })

  zkClient.start
  zkClient.blockUntilConnected

  //this is the zk client that we'll use, using the namespace reserved throughout
  implicit def zkClientWithNs = zkClient.usingNamespace(zkNamespace)

  //all interactions with the zk cluster extension should be through the zkClusterActor below
  lazy val zkClusterActor = system.actorOf(Props[ZkClusterActor], "zkCluster")
  
  val remoteGuardian = system.actorOf(Props[RemoteGuardian], "remoteGuardian")
  
  private[this] def initialize = {
    //make sure /leader, /members, /segments znodes are available
    guarantee("/leader", Some(Array[Byte]()), CreateMode.PERSISTENT)
    guarantee("/members", Some(Array[Byte]()), CreateMode.PERSISTENT)
    guarantee("/segments", Some(Array[Byte]()), CreateMode.PERSISTENT)
    val segmentsSize = zkClientWithNs.getChildren.forPath("/segments").size()
    if (segmentsSize != segmentationLogic.segmentsSize) {
      0.until(segmentationLogic.segmentsSize).foreach(s => {
        guarantee(s"/segments/segment-$s", Some(Array[Byte]()), CreateMode.PERSISTENT)
      })
    }
  }
  
  def addShutdownListener(listener: () => Unit) = shutdownListeners = listener :: shutdownListeners
  
  private[cluster] def close = {
    stopped set true
    shutdownListeners foreach (_())
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