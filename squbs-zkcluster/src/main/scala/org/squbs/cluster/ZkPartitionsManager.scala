package org.squbs.cluster

import akka.actor._
import akka.pattern._
import akka.util.{ByteString, Timeout}
import com.typesafe.scalalogging.LazyLogging
import org.apache.curator.framework.api.CuratorWatcher
import org.apache.zookeeper.KeeperException.NoNodeException
import org.apache.zookeeper.Watcher.Event.EventType
import org.apache.zookeeper.{CreateMode, WatchedEvent}

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
 * Created by zhuwang on 1/26/15.
 */

private[cluster] case class ZkRebalance(planedPartitions: Map[ByteString, ZkPartitionData],
                                        partitionsUpdateForMembers: Map[Address, (Set[ByteString], Set[ByteString])])
private[cluster] case class ZkSegmentChanged(segment:String, partitions:Set[ByteString])
private[cluster] case class ZkPartitionsChanged(segment:String, partitions: Map[ByteString, ZkPartitionData])
private[cluster] case class ZkResizePartition(partitionKey: ByteString, size: Int)
private[cluster] case class ZkUpdatePartitions(onBoardPartitions: Set[ByteString],
                                               dropOffPartitions: Set[ByteString])
private[cluster] case class ZkPurgePartition(partition: ZkPartitionData)

/**
 * The major responsibility of ZkPartitionsManager is to maintain partitions
 */
private[cluster] class ZkPartitionsManager extends Actor with LazyLogging {

  private[this] val zkCluster = ZkCluster(context.system)
  import zkCluster._
  import segmentationLogic._

  private[this] implicit val log = logger
  private[cluster] var partitionsToProtect = Set.empty[ByteString]
  private[cluster] var segmentsToPartitions = Map.empty[String, Set[ByteString]]
  private[cluster] var partitionWatchers = Map.empty[String, CuratorWatcher]
  private[cluster] var stopped = false

//  class PartitionsInfoBean extends PartitionsInfoMXBean {
//    override def getPartitions: util.List[PartitionInfo] = partitionsToMembers map {
//      case (name, members) => PartitionInfo(name, partitionZkPath(name), members.mkString(","))
//    } toList
//  }

  def initialize = {
    segmentsToPartitions = zkClientWithNs.getChildren.forPath("/segments").map{
      segment => segment -> watchOverSegment(segment)
    }.toMap
  }

  override def preStart = {
    initialize
//    register(new PartitionsInfoBean, prefix + partitionsInfoName)
  }

  override def postStop = {
    stopped = true
//    unregister(prefix + partitionsInfoName)
  }

  def watchOverSegment(segment:String) = {

    val segmentZkPath = s"/segments/${keyToPath(segment)}"
    //watch over changes of creation/removal of any partition (watcher over /partitions)
    lazy val segmentWatcher: CuratorWatcher = new CuratorWatcher {
      override def process(event: WatchedEvent): Unit = {
        event.getType match {
          case EventType.NodeChildrenChanged if !stopped =>
            self ! ZkSegmentChanged(
              segment,
              zkClientWithNs.getChildren.usingWatcher(segmentWatcher).forPath(segmentZkPath)
                .map { p => ByteString(pathToKey(p))}.toSet
            )
          case _ =>
        }
      }
    }
    //watch over changes of members of a partition (watcher over /partitions/some-partition)
    lazy val partitionWatcher: CuratorWatcher = new CuratorWatcher {
      override def process(event: WatchedEvent): Unit = {
        event.getType match {
          case EventType.NodeChildrenChanged if !stopped =>
            val sectors = event.getPath.split("[/]")
            val partitionKey = ByteString(pathToKey(sectors(sectors.length - 1)))

            watchOverPartition(segment, partitionKey, this) match {
              case Some(partitionData) =>
                self ! ZkPartitionsChanged(segment, Map(partitionKey -> partitionData))
              case _ =>
            }
          case _ =>
        }
      }
    }

    //initialize with the current set of partitions
    lazy val partitions = zkClientWithNs.getChildren.usingWatcher(segmentWatcher).forPath(segmentZkPath).map{p =>
      val partitionKey = ByteString(pathToKey(p))
      partitionKey -> watchOverPartition(segment, partitionKey, partitionWatcher)
    }.collect{
      case (partitionKey, Some(partitionData)) => partitionKey -> partitionData
    }.toMap

    partitionWatchers += segment -> partitionWatcher

    if (partitions.nonEmpty) self ! ZkPartitionsChanged(segment, partitions)

    partitions.keySet
  }

  def watchOverPartition(segment: String, partitionKey:ByteString, partitionWatcher:CuratorWatcher):Option[ZkPartitionData] = {
    try {
      val partitionZkPath = s"/segments/$segment/${keyToPath(partitionKey)}"
      val members = (stopped match {
        case true => zkClientWithNs.getChildren
        case false => zkClientWithNs.getChildren.usingWatcher(partitionWatcher)
      }).forPath(partitionZkPath)
      if (members.nonEmpty)
        Some(ZkPartitionData(partitionKey,
          members.filterNot(_ == "$size").map(m => AddressFromURIString(pathToKey(m))).toSet,
          partitionSize(partitionKey),
          zkClientWithNs.getData.forPath(partitionZkPath)
        ))
      else 
        None
      //the member data stored at znode is implicitly converted to Option[Address] which says where the member is in Akka
    }
    catch {
      case _: NoNodeException => None
      case t: Throwable => log.error("partitions refresh failed due to unknown reason: {}", t); None
    }
  }
  
  private def partitionSize(partitionKey: ByteString) = {
    try{
      bytesToInt(zkClientWithNs.getData.forPath(sizeOfParZkPath(partitionKey)))
    }
    catch{
      case _:Throwable => 0
    }
  }

  def receive: Actor.Receive = {

    case ZkClientUpdated(updated) =>
      initialize

    case ZkSegmentChanged(segment, change) =>
      log.debug("[partitions] segment change detected from zk: {}", segment -> (change map (keyToPath(_))))
      
      val onBoardPartitions = change.diff(segmentsToPartitions.getOrElse(segment, Set.empty))
        .map(partitionKey => (partitionKey -> watchOverPartition(segment, partitionKey, partitionWatchers(segment))))
        .collect{case (key, Some(partition)) => key -> partition}.toMap
      segmentsToPartitions += (segment -> change)
      log.info("[partitions] new partitions {} created", onBoardPartitions.map(entry => keyToPath(entry._1)))

    case ZkPartitionsChanged(segment, changes) => //partition changes found in zk
      log.debug("[partitions] partitions change detected from zk: {}", 
        changes.map{case (key, members) => keyToPath(key) -> members})

      val currentMemberSize = zkClientWithNs.getChildren.forPath("/members").size
      val realChanges = changes filterNot {case (key, ZkPartitionData(_, members, _, _)) =>
        if(partitionsToProtect.contains(key) && !members.contains(zkAddress)) {
          // I should be in the partition but the recent change says I am not
          // I need to register myself to the parition again and not nofiy anyone
          val zkPathRestore = s"${partitionZkPath(key)}/${keyToPath(zkAddress.toString)}"
          log.warn("[partitions] partitions change caused by loss of ephemeral znode:{} out of:{}, restoring it:{}", zkAddress, members, zkPathRestore)
          guarantee(zkPathRestore, Some(Array[Byte]()), CreateMode.EPHEMERAL)
          true
        } else false
      } collect {
        case (key, change) if change.members.isEmpty 
          || change.members.size == Math.min(currentMemberSize, change.expectedSize) => key -> change
      }
      
      log.debug("[partitions] real parition changes found {}",
        realChanges.map{case (key, members) => keyToPath(key) -> members})
      
      if (realChanges.nonEmpty) zkClusterActor ! ZkPartitionsChanged(segment, realChanges)

    case rebalance @ ZkRebalance(planned, partitionsUpdateForMembers) =>
      log.info("[partitions] rebalance partitions based on plan:{} and members change:{}", 
        planned.map{case (key, value) => keyToPath(key) -> (value.members, value.expectedSize)}, 
        partitionsUpdateForMembers.map{
          case (m, (onBoards, dropOffs)) => m -> (onBoards.map(keyToPath(_)), dropOffs.map(keyToPath(_)))
        }
      )
      // create the parition znode and its size node
      planned foreach {
        case (partitionKey, partitionData) =>
          guarantee(partitionZkPath(partitionKey), Some(partitionData.props), CreateMode.PERSISTENT)
          guarantee(sizeOfParZkPath(partitionKey), Some(partitionData.expectedSize), CreateMode.PERSISTENT)
      }

      import context.dispatcher
      implicit val timeout:Timeout = 15.seconds
      val futures = partitionsUpdateForMembers map {
        case (member, (onBoardPartitions, dropOffPartitions)) =>
          log.debug("[partitions] member {} - onBoards: {} and dropOffs: {}",
          member,
          onBoardPartitions.map(keyToPath(_)),
          dropOffPartitions.map(keyToPath(_)))
          val updateMessage = ZkUpdatePartitions(onBoardPartitions, dropOffPartitions)
          (addressee(member) match {
            case Left(me) => me.tell(updateMessage, me)
              log.debug("[partitions] update me to onboards:{} and dropoffs:{}",
              onBoardPartitions.map(keyToPath(_)),
              dropOffPartitions.map(keyToPath(_))
          )
              Future(true)
            case Right(other) =>
              log.debug("[partitions] update {} to onboards:{} and dropoffs:{}",
              other,
              onBoardPartitions.map(keyToPath(_)),
              dropOffPartitions.map(keyToPath(_))
          )
              (other ? updateMessage).mapTo[Boolean]
          })
      }
      Future.sequence(futures) onComplete {
        case Success(results) if results.forall(x => x) =>
          log.info("[partition] rebalance success with plan {}",
            planned.map { case (key, value) => keyToPath(key) ->(value.members, value.expectedSize)}
          )
        case Success(_) =>
          log.error("[partition] some members failed to answer the UpdatePartitions message. Retry in 3 seconds")
          context.system.scheduler.scheduleOnce(3 seconds)(self ! rebalance)
        case Failure(e) => log.error("[partition] rebalance failed because of {}. Retry in 3 seconds", e.getMessage)
          context.system.scheduler.scheduleOnce(3 seconds)(self ! rebalance)
      }

    case purge @ ZkPurgePartition(ZkPartitionData(partitionKey, members, expectedSize, _)) =>
      log.debug("[partitions] remove partition {}", 
        keyToPath(partitionKey) -> (members, expectedSize)
      )
      import context.dispatcher
      implicit val timeout:Timeout = 15.seconds
      val purgeMessage = ZkUpdatePartitions(Set.empty, Set(partitionKey))
      val futures = members map { member =>
        addressee(member) match {
          case Left(me) => 
            log.debug("[paritions] tell me to remove {}", keyToPath(partitionKey))
            me.tell(purgeMessage, me)
            Future(true)
          case Right(other) =>
            log.debug("[paritions] tell {} to remove {}", other, keyToPath(partitionKey))
            (other ? purgeMessage).mapTo[Boolean]
        }
      }
      val replyTo = sender
      Future.sequence(futures) onComplete {
        case Success(results) if results.forall(x => x) =>
          safelyDiscard(partitionZkPath(partitionKey))
          replyTo ! ZkPartitionRemoval(partitionKey)
          log.info("[partitions] successfully removed partition {}", keyToPath(partitionKey))
        case Success(_) =>
          log.error("[partitions] some members failed to answer the UpdatePartitions message. Retry in 3 seconds")
          context.system.scheduler.scheduleOnce(3 seconds)(self ! purge)
        case Failure(e) => log.error("[partition] rebalance failed because of {}. Retry in 3 seconds", e.getMessage)
          context.system.scheduler.scheduleOnce(3 seconds)(self ! purge)
      }

    case ZkUpdatePartitions(onboards, dropoffs) =>
      onboards.foreach{ partitionKey =>
        log.debug("[partitions] assignment:{} replying to:{}", keyToPath(partitionKey), sender().path)
        //mark acceptance
        guarantee(
          s"${partitionZkPath(partitionKey)}/${keyToPath(zkAddress.toString)}", 
          Some(Array[Byte]()), 
          CreateMode.EPHEMERAL
        )
        partitionsToProtect += partitionKey
      }
      dropoffs.foreach{ partitionKey =>
        log.debug("[partitions] release:{} replying to:{}", keyToPath(partitionKey), sender().path)
        safelyDiscard(s"${partitionZkPath(partitionKey)}/${keyToPath(zkAddress.toString)}")
        partitionsToProtect -= partitionKey
      }
      sender() ! true

    case ZkResizePartition(partitionKey, size) =>
      guarantee(sizeOfParZkPath(partitionKey), Some(size), CreateMode.PERSISTENT)
  }

  private def addressee(address:Address):Either[ActorRef, ActorSelection] =
    if(address == zkAddress)
      Left(self)
    else
      Right(context.actorSelection(self.path.toStringWithAddress(address)))
}
