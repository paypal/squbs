package org.squbs.cluster

import java.io.File
import java.util.concurrent.TimeUnit
import org.apache.zookeeper.KeeperException.NoNodeException
import org.apache.zookeeper.{WatchedEvent, CreateMode}
import org.apache.zookeeper.Watcher.Event.EventType
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.RetryPolicy
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.framework.recipes.leader.LeaderLatch
import org.apache.curator.framework.state.{ConnectionState, ConnectionStateListener}
import org.apache.curator.framework.api._
import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.concurrent.{Future, Await}
import scala.util.{Success, Try}
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.slf4j.Logging
import org.squbs.unicomplex.{Unicomplex, ConfigUtil}

/**
 * Created by huzhou on 3/25/14.
 */
class ZkCluster(system: ActorSystem,
                val zkAddress: Address,
                zkConnectionString: String,
                zkNamespace: String,
                implicit val segmentationLogic: SegmentationLogic,
                retryPolicy: RetryPolicy = new ExponentialBackoffRetry(1000, 3),
                rebalanceLogic:RebalanceLogic = DataCenterAwareRebalanceLogic(spareLeader = false)) extends Extension with Logging {

  private[this] implicit val log = logger
  private[this] var zkClient = CuratorFrameworkFactory.newClient(zkConnectionString, retryPolicy)

  zkClient.getConnectionStateListenable.addListener(new ConnectionStateListener {
    override def stateChanged(client: CuratorFramework, newState: ConnectionState): Unit = {
      newState match {
        case ConnectionState.LOST =>

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
  val zkClusterActor = system.actorOf(Props.create(classOf[ZkClusterActor], zkAddress, rebalanceLogic, segmentationLogic), "zkCluster")

  private[cluster] def initialize = {

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
}

private[cluster] sealed trait ZkClusterState

private[cluster] case object ZkClusterUninitialized extends ZkClusterState
private[cluster] case object ZkClusterActiveAsLeader extends ZkClusterState
private[cluster] case object ZkClusterActiveAsFollower extends ZkClusterState

private[cluster] case class ZkClusterData(leader: Option[Address],
                                          members: Set[Address],
                                          segmentsToPartitions: Map[String, Set[ByteString]],
                                          partitionsToMembers: Map[ByteString, Set[Address]])

private[cluster] object ZkClusterData {

  def apply(leader:Option[Address],
            members: Set[Address],
            init:(Map[String, Set[ByteString]], Map[ByteString, Set[Address]])) = new ZkClusterData(leader, members, init._1, init._2)
}

private[cluster] case class ZkLeaderElected(address: Option[Address])
private[cluster] case class ZkMembersChanged(members: Set[Address])
private[cluster] case class ZkRebalance(partitionsToMembers: Map[ByteString, Set[Address]], members:Set[Address])
private[cluster] case class ZkSegmentChanged(segment:String, partitions:Set[ByteString])
private[cluster] case class ZkPartitionsChanged(segment:String, partitions: Map[ByteString, Set[Address]])
private[cluster] case class ZkUpdatePartitions(onboards:Map[ByteString, String], dropoffs:Map[ByteString, String])
private[cluster] case object ZkAcquireLeadership

/**
 * the membership monitor has a few responsibilities, most importantly to enroll the leadership competition and get membership, leadership information immediately after change
 * @param zkClient
 * @param zkClusterActor
 * @param zkAddress
 * @param zkLeaderLatch
 */
private[cluster] class ZkMembershipMonitor(implicit var zkClient: CuratorFramework,
                                           zkClusterActor: ActorRef,
                                           zkAddress: Address,
                                           var zkLeaderLatch: LeaderLatch) extends Actor with Logging {

  private[this] implicit val log = logger
  private[this] var stopped = false

  def initialize = {

    //watch over leader changes
    val leader = zkClient.getData.usingWatcher(new CuratorWatcher {
      override def process(event: WatchedEvent): Unit = {
        log.info("[membership] leader watch event:{} when stopped:{}", event, stopped.toString)
        if(!stopped) {
          event.getType match {
            case EventType.NodeCreated | EventType.NodeDataChanged =>
              zkClusterActor ! ZkLeaderElected(zkClient.getData.usingWatcher(this).forPath("/leader"))
            case _ =>
          }
        }
      }
    }).forPath("/leader")

    //watch over members changes
    val me = guarantee(s"/members/${keyToPath(zkAddress.toString)}", Some(Array[Byte]()), CreateMode.EPHEMERAL)
    // Watch and recreate member node because it's possible for ephemeral node to be deleted while session is
    // still alive (https://issues.apache.org/jira/browse/ZOOKEEPER-1740)
    zkClient.getData.usingWatcher(new CuratorWatcher {
      def process(event: WatchedEvent): Unit = {
        log.info("[membership] self watch event: {} when stopped:{}", event, stopped.toString)
        if(!stopped) {
          event.getType match {
            case EventType.NodeDeleted =>
              log.info("[membership] member node was deleted unexpectedly, recreate")
              zkClient.getData.usingWatcher(this).forPath(guarantee(me, Some(Array[Byte]()), CreateMode.EPHEMERAL))
            case _ =>
          }
        }
      }
    }).forPath(me)

    lazy val members = zkClient.getChildren.usingWatcher(new CuratorWatcher {
      override def process(event: WatchedEvent): Unit = {
        log.info("[membership] membership watch event:{} when stopped:{}", event, stopped.toString)
        if(!stopped) {
          event.getType match {
            case EventType.NodeChildrenChanged =>
              refresh(zkClient.getChildren.usingWatcher(this).forPath("/members"))
            case _ =>
          }
        }
      }
    }).forPath("/members")

    def refresh(members:Seq[String]) = {
      zkClusterActor ! ZkMembersChanged(members.map(m => AddressFromURIString(pathToKey(m))).toSet)
      self ! ZkAcquireLeadership
    }

    refresh(members)

    self ! ZkAcquireLeadership
    zkClusterActor ! ZkLeaderElected(leader)
  }

  override def preStart = {

    //enroll in the leadership competition
    zkLeaderLatch.start

    initialize
  }

  override def postStop = {
    stopped = true
    //stop the leader latch to quit the competition
    zkLeaderLatch.close
  }

  def receive: Actor.Receive = {

    case ZkClientUpdated(updated) =>
      zkClient = updated
      zkLeaderLatch.close

      zkLeaderLatch = new LeaderLatch(zkClient, "/leadership")
      zkLeaderLatch.start
      initialize

    case ZkAcquireLeadership =>
      //repeatedly enroll in the leadership competition once the last attempt fails

      val oneSecond = 1.second
      zkLeaderLatch.await(oneSecond.length, oneSecond.unit) match {
        case true =>
          log.info("[membership] leadership acquired @ {}", zkAddress)
          guarantee("/leader", Some(zkAddress))
        case false =>
      }
  }
}

/**
 * The major responsibility of ZkPartitionsManager is to maintain partitions
 * @param zkClient
 * @param zkClusterActor
 * @param zkAddress
 */
private[cluster] class ZkPartitionsManager(implicit var zkClient: CuratorFramework,
                                           zkClusterActor: ActorRef,
                                           zkAddress: Address,
                                           rebalanceLogic: RebalanceLogic,
                                           implicit val segmentationLogic:SegmentationLogic) extends Actor with Logging {

  import ZkPartitionsManager._
  import segmentationLogic._

  private[this] implicit val log = logger
  private[cluster] var segmentsToPartitions = Map.empty[String, Set[ByteString]]
  private[cluster] var partitionsToMembers = Map.empty[ByteString, Set[Address]]
  private[cluster] var partitionWatchers = Map.empty[String, CuratorWatcher]
  private[cluster] var stopped = false

  def initialize = {
    segmentsToPartitions = zkClient.getChildren.forPath("/segments").map{segment => segment -> watchOverSegment(segment)}.toMap
  }

  override def preStart = {
    initialize
  }

  override def postStop = {
    stopped = true
  }

  def watchOverPartition(segment:String, partitionKey:ByteString, partitionWatcher:CuratorWatcher):Option[Set[Address]] = {

    try {
      Some((if(stopped) zkClient.getChildren else zkClient.getChildren.usingWatcher(partitionWatcher)).forPath(partitionZkPath(partitionKey))
        .filterNot(_ == "$size")
        .map(m => AddressFromURIString(pathToKey(m))).toSet)
      //the member data stored at znode is implicitly converted to Option[Address] which says where the member is in Akka
    }
    catch {
      case _: NoNodeException => None
      case t: Throwable => log.error("partitions refresh failed due to unknown reason: {}", t); None
    }
  }

  def watchOverSegment(segment:String) = {

    val segmentZkPath = s"/segments/${keyToPath(segment)}"
    //watch over changes of creation/removal of any partition (watcher over /partitions)
    lazy val segmentWatcher: CuratorWatcher = new CuratorWatcher {
      override def process(event: WatchedEvent): Unit = {
        event.getType match {
          case EventType.NodeChildrenChanged if !stopped =>
            self ! ZkSegmentChanged(segment, zkClient.getChildren.usingWatcher(segmentWatcher).forPath(segmentZkPath).map { p => ByteString(pathToKey(p))}.toSet)
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
              case Some(members) =>
                self ! ZkPartitionsChanged(segment, partitionsToMembers + (partitionKey -> members))
              case _ =>
            }
          case _ =>
        }
      }
    }

    //initialize with the current set of partitions
    lazy val partitions = zkClient.getChildren.usingWatcher(segmentWatcher).forPath(segmentZkPath)
    //initialize partitionsToMembers immediately
    lazy val partitionsToMembers: Map[ByteString, Set[Address]] = partitions.map{p => val partitionKey = ByteString(pathToKey(p))
      partitionKey -> watchOverPartition(segment, partitionKey, partitionWatcher)
    }.collect{
      case (partitionKey, Some(members)) => partitionKey -> members
    }.toMap

    partitionWatchers += segment -> partitionWatcher

    self ! ZkPartitionsChanged(segment, partitionsToMembers)

    partitionsToMembers.keySet
  }

  def receive: Actor.Receive = {

    case ZkClientUpdated(updated) =>
      zkClient = updated
      initialize

    case ZkSegmentChanged(segment, change) =>
      val invalidates = segmentsToPartitions.getOrElse(segment, Set.empty).diff(change)
      self ! ZkPartitionsChanged(segment, change.diff(segmentsToPartitions.getOrElse(segment, Set.empty)).foldLeft(partitionsToMembers){(memoize, partitionKey) =>
        watchOverPartition(segment, partitionKey, partitionWatchers(segment)) match {
          case Some(members) =>
            memoize + (partitionKey -> members)
          case _ =>
            memoize
        }
      } -- invalidates)

    case origin @ ZkPartitionsChanged(segment, change) => //partition changes found in zk
      log.debug("[partitions] partitions change detected from zk: {}", change.map{case (key, members) => keyToPath(key) -> members})

      change.foreach{case (key, members) =>
        //in case of a real dropoff, ZkUpdatePartitions is to be handled
        //if in prior to this detection, then partitionsToProtect will exclude the dropoff member
        //if afterwards, the actual dropoff will then remove the znode again
        if(partitionsToProtect.contains(key) && !members.contains(zkAddress)) {
          val zkPathRestore = s"${partitionZkPath(key)}/${keyToPath(zkAddress.toString)}"
          log.warn("[partitions] partitions change caused by loss of ephemeral znode:{} out of:{}, restoring it:{}", zkAddress, members, zkPathRestore)
          guarantee(zkPathRestore, Some(Array[Byte]()), CreateMode.EPHEMERAL)
        }
      }

      val numOfNodes = zkClient.getChildren.forPath("/members").size
      //correction of https://github.scm.corp.ebay.com/Squbs/chnlsvc/pull/79
      //numOfNodes as participants should be 1 less than total count iff rebalanceLogic spares the leader
      //numOfNodes should be 1 at least (if spareLeader is true and there is only one node as leader, let leader serve)
      val (effects, onboards, dropoffs) = applyChanges(segmentsToPartitions, partitionsToMembers, origin, Math.max(1, if(rebalanceLogic.spareLeader) numOfNodes - 1 else numOfNodes))
      segmentsToPartitions += segment -> effects.keySet

      if(dropoffs.nonEmpty || onboards.nonEmpty) {
        partitionsToMembers = effects
        //reduced the diff events, notifying only when the expected size have reached! (either the total members or the expected size)
        val diff = onboards.map{alter => alter -> orderByAge(alter, partitionsToMembers.getOrElse(alter, Set.empty))}.toMap ++
          dropoffs.map{dropoff => dropoff -> Seq.empty}
        val zkPaths = diff.keySet.map { partitionKey => partitionKey -> partitionZkPath(partitionKey)}.toMap

        log.debug("[partitions] change consolidated as:{} and notifying:{}", diff.map{case (key, members) => keyToPath(key) -> members}, notifyOnDifference)
        if(diff.nonEmpty){
          notifyOnDifference.foreach { listener => context.actorSelection(listener) ! ZkPartitionDiff(diff, zkPaths)}
        }
      }
      else{
        log.debug("[partitions] change ignored as no difference was found and notifying no one")
      }

    case ZkQueryPartition(partitionKey, notification, _, _, _) =>
      log.info("[partitions] partition: {} identified", keyToPath(partitionKey))
      //notification is the attachment part of the partition query, it will allow callback styled message handling at the sender()
      sender() ! ZkPartition(partitionKey, orderByAge(partitionKey, partitionsToMembers.getOrElse(partitionKey, Set.empty)), partitionZkPath(partitionKey), notification)

    case ZkRebalance(planned, alives) =>
      log.info("[partitions] rebalance partitions based on plan:{} and alives:{}", planned.map{case (key, members) => keyToPath(key) -> members}, alives)
      def addressee(address:Address):Either[ActorRef, ActorSelection] =
        if(address == zkAddress)
          Left(self)
        else
          Right(context.actorSelection(self.path.toStringWithAddress(address)))

      val result = Try {

        import context.dispatcher
        implicit val timeout:Timeout = 10.seconds
        Await.result(Future.sequence(planned.foldLeft(Map.empty[Address, (Map[ByteString, String], Map[ByteString, String])]){(impacts, assign) =>
          val partitionKey = assign._1
          val servants = partitionsToMembers.getOrElse(partitionKey, Set.empty[Address]).filter(alives.contains(_))
          val realones = zkClient.getChildren.forPath(partitionZkPath(partitionKey)).filterNot(_ == "$size").map(m => AddressFromURIString(pathToKey(m))).filter(alives.contains(_)).toSet
          val onboards = assign._2.diff(servants) ++ assign._2.diff(realones)
          val dropoffs = servants.diff(assign._2) ++ realones.diff(assign._2)
          //take realones into consideration, as the previous rebalance might yet get a universal agreement, simply force the plan again here.
          val zkPath = partitionZkPath(partitionKey)
          log.debug("[partitions] {} - onboards:{} and dropoffs:{}", keyToPath(partitionKey), onboards, dropoffs)

          val halfway = onboards.foldLeft(impacts){(impacts, member) =>
            val impactOnMember = impacts.getOrElse(member, (Map.empty[ByteString, String], Map.empty[ByteString, String]))
            impacts.updated(member, impactOnMember.copy(_1 = impactOnMember._1.updated(partitionKey, zkPath)))
          }

          dropoffs.foldLeft(halfway){(impacts, member) =>
            val impactOnMember = impacts.getOrElse(member, (Map.empty[ByteString, String], Map.empty[ByteString, String]))
            impacts.updated(member, impactOnMember.copy(_2 = impactOnMember._2.updated(partitionKey, zkPath)))
          }
        }.map{case (member, impact) =>
          (addressee(member) match {
            case Left(me) => me.tell(ZkUpdatePartitions(impact._1, impact._2), me)
              log.debug("[partitions] update me to onboard:{} and dropoff:{}", impact._1.map{assign => (keyToPath(assign._1), assign._2)}, impact._2.map{assign => (keyToPath(assign._1), assign._2)})
              Future(true)
            case Right(other) =>
              log.debug("[partitions] update {} to onboard:{} and dropoff:{}", other, impact._1.map{assign => (keyToPath(assign._1), assign._2)}, impact._2.map{assign => (keyToPath(assign._1), assign._2)})
              (other ? ZkUpdatePartitions(impact._1, impact._2)).mapTo[Boolean]
          })
        }), timeout.duration).foldLeft(true){(successful, individual) => successful && individual}
      }
      log.debug("[partitions] rebalance plan done:{}", result)
      sender() ! result

    case ZkRemovePartition(partitionKey) =>
      log.debug("[partitions] remove partition {}", keyToPath(partitionKey))
      safelyDiscard(partitionZkPath(partitionKey))
      zkClient.getChildren.forPath("/members").map(m => AddressFromURIString(pathToKey(m)) match {
        case address if address == zkAddress =>
          self ! ZkPartitionRemoval(partitionKey)
        case remote =>
          context.actorSelection(self.path.toStringWithAddress(remote)) ! ZkPartitionRemoval(partitionKey)
      })

    case ZkPartitionRemoval(partitionKey) =>
      log.debug("[partitions] partition {} was removed", keyToPath(partitionKey))
      notifyOnDifference.foreach { listener => context.actorSelection(listener) ! ZkPartitionRemoval(partitionKey)}

    case ZkMonitorPartition(onDifference) =>
      log.debug("[partitions] monitor partitioning from:{}", sender().path)
      notifyOnDifference = notifyOnDifference ++ onDifference

    case ZkStopMonitorPartition(stopOnDifference) =>
      log.debug("[partitions] stop monitor partitioning from:{}", sender().path)
      notifyOnDifference = notifyOnDifference -- stopOnDifference

    case ZkUpdatePartitions(onboards, dropoffs) =>
      onboards.foreach{case (partitionKey, zkPath) =>
        log.debug("[partitions] assignment:{} with zkPath:{} replying to:{}", keyToPath(partitionKey), zkPath, sender().path)
        guarantee(zkPath, None)
        //mark acceptance
        guarantee(s"$zkPath/${keyToPath(zkAddress.toString)}", Some(Array[Byte]()), CreateMode.EPHEMERAL)

        partitionsToProtect += partitionKey
      }
      dropoffs.foreach{case (partitionKey, zkPath) =>
        log.debug("[partitions] release:{} with zkPath:{} replying to:{}", keyToPath(partitionKey), zkPath, sender().path)
        safelyDiscard(s"$zkPath/${keyToPath(zkAddress.toString)}")

        partitionsToProtect -= partitionKey
      }
      sender() ! true
  }

  private[cluster] def applyChanges(segmentsToPartitions:Map[String, Set[ByteString]],
                                    partitionsToMembers:Map[ByteString, Set[Address]],
                                    changed:ZkPartitionsChanged,
                                    numOfNodes:Int) = {

    val impacted = partitionsToMembers.filterKeys(segmentsToPartitions.getOrElse(changed.segment, Set.empty).contains(_)).keySet
    //https://github.scm.corp.ebay.com/Squbs/chnlsvc/issues/49
    //we'll notify only when the partition has reached its expected size (either the total number of VMs (-1 iff spareLeader) or the required partition size)
    //any change inbetween will be silently ignored, as we know leader will rebalance and trigger another event to reach the expected size eventually
    //NOTE, the size must be tested with `EQUAL` other than `LESS OR EQUAL`, due to a corner case, where onboard members happen ahead of dropoff members in a shift (no size change)
    val onboards = changed.partitions.keySet.filter{partitionKey => changed.partitions.getOrElse(partitionKey, Set.empty).size == Math.min(try{
          bytesToInt(zkClient.getData.forPath(sizeOfParZkPath(partitionKey)))
        } catch {
          case _:Throwable => 0 //in case the $size node is being removed
        }, numOfNodes) &&
      partitionsToMembers.getOrElse(partitionKey, Set.empty) != changed.partitions.getOrElse(partitionKey, Set.empty)
    }
    val dropoffs = changed.partitions.keySet.filter{partitionKey => changed.partitions.getOrElse(partitionKey, Set.empty).isEmpty}.filter(impacted.contains(_))

    log.debug("[partitions] applying changes:{} against:{}, impacted:{}, onboards:{}, dropoffs:{}",
      changed.partitions.map{case (key, members) => keyToPath(key) -> members},
      partitionsToMembers.filterKeys(impacted.contains(_)).map{case (key, members) => keyToPath(key) -> members},
      impacted.map(keyToPath(_)),
      onboards.map(keyToPath(_)),
      dropoffs.map(keyToPath(_)))

    //drop off members no longer in the partition
    ((partitionsToMembers ++ changed.partitions).filterKeys(!dropoffs.contains(_)),
      onboards -- dropoffs,
      dropoffs)
  }
}

object ZkPartitionsManager {

  /**
   * both notifyOnDifference & partionsToProtect are moved out of the actor
   * for PartitionsManager to survive cluster/manager failure, notify targets should be preserved.
   *
   */
  private[cluster] var notifyOnDifference = Set.empty[ActorPath]
  private[cluster] var partitionsToProtect = Set.empty[ByteString]

}

case object ZkRebalanceRetry
/**
 * The main Actor of ZkCluster
 * @param zkClient
 * @param zkAddress
 */
class ZkClusterActor(zkAddress:Address,
                     rebalanceLogic:RebalanceLogic,
                     implicit val segmentationLogic:SegmentationLogic) extends FSM[ZkClusterState, ZkClusterData] with Stash with Logging {

  import segmentationLogic._
  import ZkCluster._

  private[this] implicit val log = logger

  implicit def zkClient: CuratorFramework = ZkCluster(context.system).zkClientWithNs

  //begin the process of electing a leader
  private val zkMembershipMonitor = context.actorOf(
    Props(classOf[ZkMembershipMonitor], zkClient, self, zkAddress, new LeaderLatch(zkClient, "/leadership")).withDispatcher("pinned-dispatcher"), "zkMembership")
  //begin the process of partitioning management
  private val zkPartitionsManager = context.actorOf(
    Props(classOf[ZkPartitionsManager], zkClient, self, zkAddress, rebalanceLogic, segmentationLogic), "zkPartitions")

  private[this] val mandatory:StateFunction = {

    case Event(updatedEvent @ ZkClientUpdated(updated), _) =>
      zkMembershipMonitor ! updatedEvent
      zkPartitionsManager ! updatedEvent
      whenZkClientUpdated.foreach(context.actorSelection(_) ! updatedEvent)
      stay

    case Event(ZkMonitorClient, _) =>
      whenZkClientUpdated = whenZkClientUpdated :+ sender().path
      stay

    case Event(ZkQueryMembership, zkClusterData) =>
      sender() ! ZkMembership(zkClusterData.members)
      stay

    case Event(origin: ZkMonitorPartition, _) =>
      log.info("[follower/leader] monitor partitioning from:{}", sender().path)
      zkPartitionsManager forward origin
      stay

    case Event(origin: ZkStopMonitorPartition, _) =>
      log.info("[follower/leader] stop monitor partitioning from:{}", sender().path)
      zkPartitionsManager forward origin
      stay

    case Event(ZkListPartitions(member), _) =>
      sender() ! ZkPartitions(stateData.partitionsToMembers.collect{
        case (partitionKey:ByteString, members:Set[Address]) if members.contains(member) => partitionKey
      }.toSeq)
      stay
  }

  //the reason we put startWith into #preStart is to allow postRestart to trigger new FSM actor when recover from error
  override def preStart = startWith(ZkClusterUninitialized, ZkClusterData(None, Set.empty, partitionsInitialize))

  when(ZkClusterUninitialized)(mandatory orElse {

    case Event(ZkLeaderElected(Some(address)), zkClusterData) =>
      log.info("[uninitialized] leader elected:{} and my zk address:{}", address, zkAddress)
      if(address.hostPort == zkAddress.hostPort)
        rebalance(zkClusterData.partitionsToMembers, zkClusterData.members) match {
          case Some(rebalanced) =>
            goto(ZkClusterActiveAsLeader) using zkClusterData.copy(leader = Some(address),
              partitionsToMembers = rebalanced)
          case None =>
            self ! ZkRebalanceRetry
            goto(ZkClusterActiveAsLeader) using zkClusterData.copy(leader = Some(address))
        }

      else
        goto(ZkClusterActiveAsFollower) using zkClusterData.copy(leader = Some(address))

    case Event(ZkMembersChanged(members), zkClusterData) =>
      log.info("[uninitialized] membership updated:{}", members)
      stay using zkClusterData.copy(members = members)

    case Event(_, _) =>
      stash
      stay
  })

  when(ZkClusterActiveAsFollower)(mandatory orElse {

    case Event(ZkLeaderElected(Some(address)), zkClusterData) =>
      if(address.hostPort == zkAddress.hostPort)
        rebalance(zkClusterData.partitionsToMembers, zkClusterData.members) match {
          case Some(rebalanced) =>
            goto (ZkClusterActiveAsLeader) using zkClusterData.copy (leader = Some (address),
              partitionsToMembers = rebalanced)
          case None =>
            self ! ZkRebalanceRetry
            goto (ZkClusterActiveAsLeader) using zkClusterData.copy (leader = Some (address))
        }
      else
        stay

    case Event(ZkQueryLeadership, zkClusterData) =>
      log.info("[follower] leadership query answered:{} to:{}", zkClusterData.leader, sender().path)
      zkClusterData.leader.foreach(address => sender() ! ZkLeadership(address))
      whenZkLeadershipUpdated += sender().path
      stay

    case Event(ZkMembersChanged(members), zkClusterData) =>
      log.info("[follower] membership updated:{}", members)
      stay using zkClusterData.copy(members = members)

    case Event(ZkPartitionDiff(diff, _), zkClusterData) =>
      stay using zkClusterData.copy(partitionsToMembers = diff.foldLeft(zkClusterData.partitionsToMembers){(memoize, change) => memoize.updated(change._1, change._2.toSet)})

    case Event(origin @ ZkQueryPartition(key, _, Some(size), props, members), zkClusterData) =>
      log.info("[follower] partition query forwarded to leader:{}", zkClusterData.leader)
      zkClusterData.leader.foreach(address => {
        context.actorSelection(self.path.toStringWithAddress(address)) forward origin
      })
      stay

    case Event(origin @ ZkQueryPartition(partitionKey, notification, None, _, _), zkClusterData) =>
      zkClusterData.partitionsToMembers.get(partitionKey) match {
        case Some(servants) if servants.nonEmpty => //use the snapshot mapping as long as it's available
          sender() ! ZkPartition(partitionKey, orderByAge(partitionKey, servants), partitionZkPath(partitionKey), notification)
        case _ => //local mapping wasn't available yet, have to go to leader for source of truth
          zkClusterData.leader.foreach(address => {
            context.actorSelection(self.path.toStringWithAddress(address)) forward origin
          })
      }
      stay

    case Event(resize:ZkResizePartition, zkClusterData) =>
      zkClusterData.leader.foreach(address => {
        context.actorSelection(self.path.toStringWithAddress(address)) forward resize
      })
      stay

    case Event(remove:ZkRemovePartition, zkClusterData) =>
      zkClusterData.leader.foreach(address => {
        context.actorSelection(self.path.toStringWithAddress(address)) forward remove
      })
      stay
  })

  when(ZkClusterActiveAsLeader)(mandatory orElse {

    case Event(ZkLeaderElected(Some(address)), zkClusterData) =>
      if (address.hostPort == zkAddress.hostPort)
        stay
      else
        goto(ZkClusterActiveAsFollower) using zkClusterData.copy(leader = Some(address))

    case Event(ZkQueryLeadership, zkClusterData) =>
      log.info("[leader] leadership query answered:{} to:{}", zkClusterData.leader, sender().path)
      zkClusterData.leader.foreach(address => sender() ! ZkLeadership(address))
      whenZkLeadershipUpdated += sender().path
      stay

    case Event(ZkMembersChanged(members), zkClusterData) =>
      log.info("[leader] membership updated:{}", members)

      if(zkClusterData.members == members){
        //corner case, in which members weren't really changed, avoid redundant rebalances
        stay
      }
      else {
        val dropoffs = zkClusterData.members.diff(members)
        val excluded = if(dropoffs.nonEmpty)
          zkClusterData.partitionsToMembers.mapValues{servants => servants.filterNot(dropoffs.contains(_))}
        else
          zkClusterData.partitionsToMembers

        rebalance(excluded, members) match {
          case Some(rebalanced) =>
            stay using zkClusterData.copy(members = members, partitionsToMembers = rebalanced)
          case None =>
            self ! ZkRebalanceRetry
            stay using zkClusterData.copy(members = members)
        }
      }

    case Event(origin @ ZkQueryPartition(partitionKey, notification, Some(expectedSize), props, _), zkClusterData) =>

      val zkPath = guarantee(partitionZkPath(partitionKey), Some(props), CreateMode.PERSISTENT)
      zkClusterData.partitionsToMembers.get(partitionKey) match {
        case Some(members) if members.nonEmpty && members.size == expectedSize =>
          logger.info("[leader] partition already exists:{} -> {}", keyToPath(partitionKey), members)
          //when the partition already exists, use the snapshot partition view, waiting for further notification for members change
          sender() ! ZkPartition(partitionKey, orderByAge(partitionKey, members), zkPath, notification)
          stay
        case _ =>
          log.info("[leader] partition creation:{}", keyToPath(partitionKey))
          //partition is to be recovered
          guarantee(sizeOfParZkPath(partitionKey), Some(expectedSize), CreateMode.PERSISTENT)
          rebalance(zkClusterData.partitionsToMembers + (partitionKey -> zkClusterData.partitionsToMembers.getOrElse(partitionKey, Set.empty)), zkClusterData.members) match {
            case Some(rebalanced) =>
              try {
                stay using zkClusterData.copy(partitionsToMembers = rebalanced)
              }
              finally{
                sender() ! ZkPartition(partitionKey, orderByAge(partitionKey, rebalanced.getOrElse(partitionKey, Set.empty)), zkPath, notification)
              }
            case None =>
              //try handling this request once again
              self forward origin
              stay
          }
      }

    case Event(ZkQueryPartition(partitionKey, notification, None, _, _), zkClusterData) =>
      log.info("[leader] partition query:{} handled by leader cluster actor", keyToPath(partitionKey))
      sender() ! ZkPartition(partitionKey,
        orderByAge(partitionKey, zkClusterData.partitionsToMembers.getOrElse(partitionKey, Set.empty)),
        partitionZkPath(partitionKey),
        notification)
      stay

    case Event(ZkResizePartition(partitionKey, sizeOf), zkClusterData) =>
      log.info("[leader] resize partition:{} forwarded to partition manager", keyToPath(partitionKey))
      guarantee(sizeOfParZkPath(partitionKey), Some(intToBytes(sizeOf)), CreateMode.PERSISTENT)
      rebalance(zkClusterData.partitionsToMembers, zkClusterData.members) match {
        case Some(rebalanced) =>
          stay using zkClusterData.copy (partitionsToMembers = rebalanced)
        case None =>
          self ! ZkRebalanceRetry
          stay
      }

    case Event(remove:ZkRemovePartition, zkClusterData) =>
      log.info("[leader] remove partition:{} forwarded to partition manager", keyToPath(remove.partitionKey))
      zkPartitionsManager forward remove
      stay

    case Event(ZkRebalanceRetry, zkClusterData) =>
      log.info("[leader] rebalance retry after previous failure attempt")
      rebalance(zkClusterData.partitionsToMembers, zkClusterData.members) match {
        case Some(rebalanced) =>
          stay using zkClusterData.copy(partitionsToMembers = rebalanced)
        case None =>
          self ! ZkRebalanceRetry
          stay
      }
  })

  onTransition {
    case ZkClusterUninitialized -> ZkClusterActiveAsFollower =>
      //unstash all messages uninitialized state couldn't handle
      unstashAll
      //as a follower, i have to listen to the ZkPartitionsChanged event, as it's driven by ZkPartitionsManager and i must update my partitionsToMembers snapshot
      zkPartitionsManager ! ZkMonitorPartition(onDifference = Set(self.path))

    case ZkClusterUninitialized -> ZkClusterActiveAsLeader =>
      //unstash all messages uninitialized state couldn't handle
      unstashAll

    case ZkClusterActiveAsFollower -> ZkClusterActiveAsLeader =>
      //as the leader, i no longer need to handle ZkPartitionsChanged event, as i drive the change instead, ZkPartitionsManager will accept my partitionsToMembers
      zkPartitionsManager ! ZkStopMonitorPartition(onDifference = Set(self.path))
      whenZkLeadershipUpdated.foreach{context.actorSelection(_) ! ZkLeadership(zkAddress)}

    case ZkClusterActiveAsLeader -> ZkClusterActiveAsFollower =>
      //as a follower, i have to listen to the ZkPartitionsChanged event, as it's driven by ZkPartitionsManager and i must update my partitionsToMembers snapshot
      zkPartitionsManager ! ZkMonitorPartition(onDifference = Set(self.path))
  }

  private[this] def partitionsInitialize:(Map[String, Set[ByteString]], Map[ByteString, Set[Address]]) = {

    val segments = zkClient.getChildren.forPath("/segments").map(pathToKey(_))

    val segmentsToPartitions:Map[String, Seq[String]] = segments.map(segment =>
      segment -> zkClient.getChildren.forPath(s"/segments/${keyToPath(segment)}").map(pathToKey(_)).toSeq
    ).toMap

    val partitionsToMembers = segmentsToPartitions.foldLeft(Map.empty[ByteString, Set[Address]]){(memoize, pair) =>
      memoize ++ pair._2.map(ByteString(_) -> Set.empty[Address])
    }

    (segmentsToPartitions.mapValues(_.map(partition => ByteString(partition)).toSet), partitionsToMembers)
  }

  private[cluster] def partitionSize(partitionKey:ByteString):Int = try{
    bytesToInt(zkClient.getData.forPath(sizeOfParZkPath(partitionKey)))
  }
  catch{
    case _:Throwable => 0
  }

  private[cluster] def rebalance(partitionsToMembers:Map[ByteString, Set[Address]], members:Set[Address]):Option[Map[ByteString, Set[Address]]] = {

    //spareLeader only when there're more than 1 VMs in the cluster
    val candidates = if(rebalanceLogic.spareLeader && members.size > 1) members.filterNot{candidate => stateData.leader.exists(candidate == _)} else members
    val plan = rebalanceLogic.rebalance(rebalanceLogic.compensate(partitionsToMembers, candidates.toSeq, partitionSize _), members)

    log.info("[leader] rebalance planned as:{}", plan.map{case (key, members) => keyToPath(key) -> members})
    implicit val timeout:akka.util.Timeout = 30.seconds
    Await.result(zkPartitionsManager ? ZkRebalance(plan, members), timeout.duration) match {
      case Success(true) =>
        log.info("[leader] rebalance successfully done")
        Some(plan)
      case _ =>
        log.info("[leader] rebalance timeout")
        None
    }
  }
}

object ZkCluster extends ExtensionId[ZkCluster] with ExtensionIdProvider with Logging {

  private[cluster] var whenZkClientUpdated = Seq.empty[ActorPath]
  private[cluster] var whenZkLeadershipUpdated = Set.empty[ActorPath]

  override def lookup(): ExtensionId[_ <: Extension] = ZkCluster

  override def createExtension(system: ExtendedActorSystem): ZkCluster = {

    val source = new File(Unicomplex(system).externalConfigDir, "zkcluster.conf")
    logger.info("[zkcluster] reading configuration from:{}", source.getAbsolutePath)
    val configuration = ConfigFactory.parseFile(source) withFallback(ConfigFactory.parseMap(Map(
      "zkCluster.segments" -> Int.box(128),
      "zkCluster.spareLeader" -> Boolean.box(false))))

    val zkConnectionString = configuration.getString("zkCluster.connectionString")
    val zkNamespace = configuration.getString("zkCluster.namespace")
    val zkSegments = configuration.getInt("zkCluster.segments")
    val zkSpareLeader = configuration.getBoolean("zkCluster.spareLeader")
    val zkAddress = external(system)
    logger.info("[zkcluster] connection to:{} and namespace:{} with segments:{} using address:{}", zkConnectionString, zkNamespace, zkSegments.toString, zkAddress)

    new ZkCluster(system, zkAddress, zkConnectionString, zkNamespace, DefaultSegmentationLogic(zkSegments).asInstanceOf[SegmentationLogic], rebalanceLogic = DataCenterAwareRebalanceLogic(spareLeader = zkSpareLeader))
  }

  private[cluster] def external(system:ExtendedActorSystem):Address = Address("akka.tcp", system.name, ConfigUtil.ipv4, system.provider.getDefaultAddress.port.getOrElse(8086))
}