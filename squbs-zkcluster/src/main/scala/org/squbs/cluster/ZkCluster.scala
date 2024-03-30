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

import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.util.Properties
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import org.apache.pekko.actor._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.apache.curator.RetryPolicy
import org.apache.curator.framework.api.CuratorWatcher
import org.apache.curator.framework.state.{ConnectionState, ConnectionStateListener}
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.zookeeper.server.quorum.flexible.QuorumMaj
import org.apache.zookeeper.{CreateMode, WatchedEvent}
import org.squbs.cluster.rebalance.{DataCenterAwareRebalanceLogic, DefaultCorrelation, RebalanceLogic}

import scala.jdk.CollectionConverters._
import scala.language.implicitConversions
import scala.util.Try
import scala.util.control.NonFatal

@deprecated("zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
case class ZkCluster(zkAddress: Address,
                     initConnStr: String,
                     zkNamespace: String,
                     segmentationLogic: SegmentationLogic,
                     retryPolicy: RetryPolicy,
                     rebalanceLogic: RebalanceLogic)
                    (implicit system: ActorSystem) extends Extension with LazyLogging {

  //all interactions with the zk cluster extension should be through the zkClusterActor below
  lazy val zkClusterActor = system.actorOf(Props[ZkClusterActor](), "zkCluster")
  val remoteGuardian = system.actorOf(Props[RemoteGuardian](), "remoteGuardian")

  private[this] implicit val log = logger
  private[this] val curatorFwk = new AtomicReference[CuratorFramework]()
  private[this] val connectString = new AtomicReference[String](initConnStr)
  private[this] val stopped = new AtomicBoolean(false)
  private[this] val shutdownListeners = new ConcurrentLinkedQueue[(CuratorFramework => Unit)]()

  private[this] val connectionStateListener: ConnectionStateListener =
    new ConnectionStateListener {
      override def stateChanged(client: CuratorFramework, newState: ConnectionState): Unit = {
        newState match {
          case ConnectionState.LOST if !stopped.get =>
            logger.error("[zkCluster] connection lost!")
            system.eventStream.publish(ZkLost)
            initialize()
          case ConnectionState.CONNECTED if !stopped.get =>
            logger.info("[zkCluster] connected send out the notification")
            system.eventStream.publish(ZkConnected)
            znodesSetup()(curatorFwkWithNs())
            zkClusterActor ! ZkClientUpdated(curatorFwkWithNs())
          case ConnectionState.SUSPENDED if !stopped.get =>
            logger.info("[zkCluster] connection suspended suspended")
            system.eventStream.publish(ZkSuspended)
          case ConnectionState.RECONNECTED if !stopped.get =>
            logger.info("[zkCluster] reconnected")
            system.eventStream.publish(ZkReconnected)
          case otherState =>
            logger.warn(s"[zkCluster] connection state changed $otherState. What shall I do?")
        }
      }
    }


  private[this] def znodesSetup()(implicit curatorFwk: CuratorFramework) = {
    //make sure /leader, /members, /segments znodes are available
    guarantee("/leader", Some(Array[Byte]()), CreateMode.PERSISTENT)
    guarantee("/members", Some(Array[Byte]()), CreateMode.PERSISTENT)
    guarantee("/segments", Some(Array[Byte]()), CreateMode.PERSISTENT)
    val segmentsSize = curatorFwk.getChildren.forPath("/segments").size()
    if (segmentsSize != segmentationLogic.segmentsSize) {
      0.until(segmentationLogic.segmentsSize).foreach(s => {
        guarantee(s"/segments/segment-$s", Some(Array[Byte]()), CreateMode.PERSISTENT)
      })
    }
  }

  initialize()

  private def initialize() = synchronized {
    val client = CuratorFrameworkFactory.newClient(connectString.get(), retryPolicy)
    client.getConnectionStateListenable.addListener(connectionStateListener)
    client.start()
    client.blockUntilConnected()
    curatorFwk set client
    updateConnectString(configWatcher)
  }

  private lazy val configWatcher = new CuratorWatcher {
    override def process(event: WatchedEvent): Unit = updateConnectString(this)
  }

  private def updateConnectString(watcher: CuratorWatcher) = Try {
    val connStr: String = curatorFwk.get().getConfig.usingWatcher(watcher).forEnsemble()
    if (Option(connStr).nonEmpty && connStr.nonEmpty && connectString.get() != connStr) {
      connectString set connStr
      system.eventStream.publish(ZkConfigChanged(connStr))
      logger.warn("[ZkCluster] detected Zookeeper cluster config change {}", connStr)
    }
  } recover {
    case NonFatal(e) => logger.error(e.getMessage)
  }

  private implicit def bytesToConnectString(bytes: Array[Byte]): String = {
    val properties = new Properties
    properties.load(new ByteArrayInputStream(bytes))
    val newConfig = new QuorumMaj(properties)
    newConfig.getAllMembers.values().asScala.map { server =>
      server.addr.getAddress.getHostAddress + ":" + server.clientAddr.getPort
    } mkString ","
  }

  //this is the zk client that we'll use, using the namespace reserved throughout
  // The reference to CuratorFramework should not be accessible from outside.
  // The way to get the updated reference is to subscribe to ZkClientUpdateEvent
  @deprecated("This method should not be accessible to outside.", "0.9.0")
  def zkClientWithNs: CuratorFramework = curatorFwkWithNs()

  private[cluster] def curatorFwkWithNs() = synchronized {
    Try {
      curatorFwk.get().usingNamespace(zkNamespace)
    } getOrElse {
      curatorFwk.get().close()
      initialize()
      curatorFwk.get().usingNamespace(zkNamespace)
    }
  }

  def addShutdownListener(listener: CuratorFramework => Unit): Unit = shutdownListeners offer listener
  
  private[cluster] def close() = {
    stopped set true
    val client = curatorFwk.get()
    client.getConnectionStateListenable.removeListener(connectionStateListener)
    shutdownListeners.asScala.foreach (_(curatorFwkWithNs()))
    client.close()
  }
}

object ZkCluster extends ExtensionId[ZkCluster] with ExtensionIdProvider with LazyLogging {

  override def lookup(): ExtensionId[_ <: Extension] = ZkCluster

  val fallbackConfig = ConfigFactory.parseString(
    """
      |zkCluster {
      |  segments = 128
      |  spareLeader = false
      |}
    """.stripMargin
  )

  val DEFAULT_REMOTE_PORT = 8086
  val DEFAULT_MAX_RETRIES = 3
  val DEFAULT_BASE_SLEEP_TIME_MS = 1000

  override def createExtension(system: ExtendedActorSystem): ZkCluster = {
    val configuration = system.settings.config withFallback fallbackConfig
    val initConnStr = configuration.getString("zkCluster.connectionString")
    val zkNamespace = configuration.getString("zkCluster.namespace")
    val zkSegments = configuration.getInt("zkCluster.segments")
    val zkSpareLeader = configuration.getBoolean("zkCluster.spareLeader")
    val zkAddress = external(system)
    logger.info("[zkcluster] connection to:{} and namespace:{} with segments:{} using address:{}",
      initConnStr, zkNamespace, zkSegments.toString, zkAddress)
    new ZkCluster(
      zkAddress,
      initConnStr,
      zkNamespace,
      DefaultSegmentationLogic(zkSegments),
      new ExponentialBackoffRetry(DEFAULT_BASE_SLEEP_TIME_MS, DEFAULT_MAX_RETRIES),
      DataCenterAwareRebalanceLogic(spareLeader = zkSpareLeader, correlation = DefaultCorrelation())
    )(system)
  }
  private[cluster] def external(system:ExtendedActorSystem):Address =
    Address("pekko", system.name, InetAddress.getLocalHost.getHostAddress, system.provider.getDefaultAddress.port.getOrElse(DEFAULT_REMOTE_PORT))
}
