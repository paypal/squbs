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

import java.util.concurrent.atomic.AtomicBoolean

import org.apache.pekko.actor._
import org.apache.pekko.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.api.CuratorWatcher
import org.apache.zookeeper.Watcher.Event.EventType
import org.apache.zookeeper.{CreateMode, WatchedEvent}

import scala.jdk.CollectionConverters._
import scala.language.postfixOps
import scala.util.control.NonFatal
import scala.util.{Failure, Try}

private[cluster] case class ZkRebalance(planedPartitions: Map[ByteString, ZkPartitionData])
private[cluster] case class ZkPartitionsChanged(segment:String, partitions: Map[ByteString, ZkPartitionData])
private[cluster] case class ZkResizePartition(partitionKey: ByteString, size: Int)
private[cluster] case class ZkSegmentChanged(segment: String, changes: Set[ByteString])
private[cluster] case class ZkOnBoardPartitions(onBoards: Set[ByteString])
private[cluster] case class ZkDropOffPartitions(dropOffs: Set[ByteString])

/**
 * The major responsibility of ZkPartitionsManager is to maintain partitions
 */
private[cluster] class ZkPartitionsManager extends Actor with Stash with LazyLogging {
  private[this] val zkCluster = ZkCluster(context.system)
  import zkCluster._

  private[this] implicit val segLogic = segmentationLogic
  import segLogic._

  import ZkPartitionsManager._

  private[this] implicit val log = logger
  private[this] var segmentsToPartitions = Map.empty[String, Set[ByteString]]
  private[this] var partitionWatchers = Map.empty[String, CuratorWatcher]
  private[this] val stopped = new AtomicBoolean(false)

  override def postStop(): Unit = stopped set true

  private def initialize()(implicit curatorFwk: CuratorFramework) = {
    segmentsToPartitions = curatorFwk.getChildren.forPath("/segments").asScala.map{
      segment => segment -> watchOverSegment(segment)
    }.toMap
  }

  private def watchOverSegment(segment:String)(implicit curatorFwk: CuratorFramework) = {
    val segmentZkPath = s"/segments/${keyToPath(segment)}"
    //watch over changes of creation/removal of any partition (watcher over /partitions)
    lazy val segmentWatcher: CuratorWatcher = new CuratorWatcher {
      override def process(event: WatchedEvent): Unit = {
        event.getType match {
          case EventType.NodeChildrenChanged if !stopped.get =>
            self ! ZkSegmentChanged(
              segment,
              curatorFwk.getChildren.usingWatcher(segmentWatcher).forPath(segmentZkPath)
                .asScala
                .map { p => ByteString(pathToKey(p)) }.toSet
            )
          case _ =>
        }
      }
    }

    //watch over changes of members of a partition (watcher over /partitions/some-partition)
    lazy val partitionWatcher: CuratorWatcher = new CuratorWatcher {
      override def process(event: WatchedEvent): Unit = {
        event.getType match {
          case EventType.NodeDataChanged if !stopped.get =>
            val sectors = event.getPath.split("[/]")
            val partitionKey = ByteString(pathToKey(sectors(sectors.length - 2)))
            sectors(sectors.length - 1) match {
              case "servants" | "$size" =>
                watchOverPartition(segment, partitionKey, partitionWatcher) foreach { partitionData =>
                  whenPartitionChanged(segment, partitionData)
                }
              case _ =>
            }
          case _ =>
        }
      }
    }

    partitionWatchers += segment -> partitionWatcher
    //initialize with the current set of partitions
    curatorFwk.getChildren.usingWatcher(segmentWatcher).forPath(segmentZkPath).asScala.map{p =>
      val partitionKey = ByteString(pathToKey(p))
      partitionKey -> watchOverPartition(segment, partitionKey, partitionWatcher)
    }.collect{
      case (partitionKey, Some(partitionData)) => partitionKey
    }.toSet
  }

  private def watchOverPartition(segment: String,
                                 partitionKey: ByteString,
                                 partitionWatcher: CuratorWatcher)
                                (implicit curatorFwk: CuratorFramework): Option[ZkPartitionData] = {
    Try {
      guarantee(servantsOfParZkPath(partitionKey), None, CreateMode.PERSISTENT)
      guarantee(sizeOfParZkPath(partitionKey), None, CreateMode.PERSISTENT)
      val servants =
        curatorFwk.getData.usingWatcher(partitionWatcher).forPath(servantsOfParZkPath(partitionKey)).toAddressSet
      val expectedSize =
        curatorFwk.getData.usingWatcher(partitionWatcher).forPath(sizeOfParZkPath(partitionKey)).toInt
      ZkPartitionData(partitionKey, servants, partitionSize(partitionKey), expectedSize)
    } recoverWith {
      case NonFatal(t) =>
        log.error("partitions refresh failed due to unknown reason: {}", t.getMessage)
        Failure(t)
    } toOption
  }

  private def whenPartitionChanged(segment: String, change: ZkPartitionData) = {
    log.debug("[partitions] partitions change detected from zk: {}",
      keyToPath(change.partitionKey) -> change
    )
    zkClusterActor ! ZkPartitionsChanged(segment, Map(change.partitionKey -> change))
  }

  def receive: Receive = receiveZkClientUpdate

  lazy val receiveZkClientUpdate: Receive = {
    case ZkClientUpdated(updated) =>
      implicit val curatorFwk = updated
      initialize()
      context become { receiveZkClientUpdate orElse receivePartitionChange() }
  }

  def receivePartitionChange()(implicit curatorFwk: CuratorFramework): Receive = {
    case ZkSegmentChanged(segment, changes) =>
      log.debug("[partitions] segment change detected from zk: {}", segment -> (changes map (keyToPath(_))))
      val onBoardPartitions = changes.diff(segmentsToPartitions.getOrElse(segment, Set.empty))
        .map(partitionKey => partitionKey -> watchOverPartition(segment, partitionKey, partitionWatchers(segment)))
        .collect{case (key, Some(partition)) => key -> partition}.toMap
      val dropOffPartitions = segmentsToPartitions.getOrElse(segment, Set.empty) diff changes
      segmentsToPartitions += (segment -> changes)
      log.info("[partitions] create partitions {}, remove partitions {}",
        onBoardPartitions.map(entry => keyToPath(entry._1)),
        dropOffPartitions.map(entry => keyToPath(entry))
      )
      if (onBoardPartitions.nonEmpty) {
        zkClusterActor ! ZkPartitionsChanged(segment, onBoardPartitions)
      }
      if (dropOffPartitions.nonEmpty) {
        zkClusterActor ! ZkPartitionsChanged(segment,
          dropOffPartitions.map(key => key -> ZkPartitionData(key, expectedSize = 0)).toMap
        )
      }

    case ZkRebalance(updates) =>
      log.info("[partitions] update partitions based on plan:{}", updates.values)
      updates foreach { entry => updatePartition(entry._1, entry._2)}

    case ZkRemovePartition(partitionKey) =>
      log.debug("[partitions] remove partition {}", keyToPath(partitionKey))
      safelyDiscard(partitionZkPath(partitionKey))
      sender() ! ZkPartitionRemoval(partitionKey)

    case ZkResizePartition(partitionKey, size) =>
      guarantee(sizeOfParZkPath(partitionKey), Some(size), CreateMode.PERSISTENT)

  }

  private def updatePartition(partitionKey: ByteString, partitionData: ZkPartitionData)
                             (implicit curatorFwk: CuratorFramework) = {
    guarantee(partitionZkPath(partitionKey), Some(partitionData.props), CreateMode.PERSISTENT)
    guarantee(servantsOfParZkPath(partitionKey), Some(partitionData.members), CreateMode.PERSISTENT)
    if (partitionData.expectedSize != partitionSize(partitionKey)) {
      guarantee(sizeOfParZkPath(partitionKey), Some(partitionData.expectedSize), CreateMode.PERSISTENT)
    }
  }

}

@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
object ZkPartitionsManager {

  def loadPartitions()(implicit zkClient: CuratorFramework,
                       segmentationLogic: SegmentationLogic): Map[ByteString, ZkPartitionData] = {
    import segmentationLogic._
    zkClient.getChildren.forPath("/segments").asScala.flatMap { segment =>
      zkClient.getChildren.forPath(s"/segments/$segment").asScala
    }.map { key =>
      val parKey = ByteString(pathToKey(key))
      val size = partitionSize(parKey)
      val members = partitionServants(parKey)
      val props = Try(zkClient.getData.forPath(partitionZkPath(parKey))) getOrElse Array.empty
      parKey -> ZkPartitionData(parKey, members, size, props)
    }.toMap
  }

  private def partitionServants(partitionKey: ByteString)
                               (implicit zkClient: CuratorFramework,
                                segmentationLogic: SegmentationLogic): Set[Address] = {
    import segmentationLogic._
    Try {
      zkClient.getData.forPath(servantsOfParZkPath(partitionKey)).toAddressSet
    } getOrElse Set.empty
  }

  private def partitionSize(partitionKey: ByteString)
                           (implicit zkClient: CuratorFramework,
                            segmentationLogic: SegmentationLogic): Int = {
    import segmentationLogic._
    Try {
      zkClient.getData.forPath(sizeOfParZkPath(partitionKey)).toInt
    } getOrElse 0
  }
}
