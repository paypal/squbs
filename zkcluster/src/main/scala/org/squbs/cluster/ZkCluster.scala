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
import scala.concurrent.Await
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

          zkClusterActor ! ZkClientUpdated(zkClientWithNs)
          zkMembershipMonitor ! ZkClientUpdated(zkClientWithNs)
          zkPartitionsManager ! ZkClientUpdated(zkClientWithNs)
        case _ =>
      }
    }
  })
  zkClient.start

  //this is the zk client that we'll use, using the namespace reserved throughout
  implicit def zkClientWithNs = zkClient.usingNamespace(zkNamespace)

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

  //all interactions with the zk cluster extension should be through the zkClusterActor below
  val zkClusterActor = system.actorOf(Props.create(classOf[ZkClusterActor], zkClientWithNs, zkAddress, rebalanceLogic, segmentationLogic), "zkCluster")

  //begin the process of electing a leader
  private[cluster] val zkMembershipMonitor = system.actorOf(
    Props(classOf[ZkMembershipMonitor], zkClientWithNs, zkClusterActor, zkAddress, new LeaderLatch(zkClientWithNs, "/leadership")).withDispatcher("pinned-dispatcher"), "zkMembership")
  //begin the process of partitioning management
  private[cluster] val zkPartitionsManager = system.actorOf(
    Props(classOf[ZkPartitionsManager], zkClientWithNs, zkClusterActor, zkAddress, segmentationLogic), "zkPartitions")
}

private[cluster] sealed trait ZkClusterState

private[cluster] case object ZkClusterUninitialized extends ZkClusterState
private[cluster] case object ZkClusterActiveAsLeader extends ZkClusterState
private[cluster] case object ZkClusterActiveAsFollower extends ZkClusterState

private[cluster] case class ZkClusterData(leader: Option[Address],
                                          members: Set[Address],
                                          segmentsToPartitions: Map[String, Set[ByteString]],
                                          partitionsToMembers: Map[ByteString, Set[Address]])

private[cluster] case class ZkLeaderElected(address: Option[Address])
private[cluster] case class ZkMembersChanged(members: Set[Address])
private[cluster] case class ZkRebalance(partitionsToMembers: Map[ByteString, Set[Address]])
private[cluster] case class ZkPartitionsChanged(segment:String, partitions: Map[ByteString, Set[Address]])
private[cluster] case class ZkPartitionOnboard(partitionKey: ByteString, zkPath: String)
private[cluster] case class ZkPartitionDropoff(partitionKey: ByteString, zkPath: String)
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

  def initialize = {

    //watch over leader changes
    val leader = zkClient.getData.usingWatcher(new CuratorWatcher {
      override def process(event: WatchedEvent): Unit = {
        log.info("[membership] leader watch event:{}", event)
        event.getType match {
          case EventType.NodeCreated | EventType.NodeDataChanged =>
            zkClusterActor ! ZkLeaderElected(zkClient.getData.usingWatcher(this).forPath("/leader"))
          case _ =>
        }
      }
    }).forPath("/leader")

    //watch over members changes
    val me = guarantee(s"/members/${keyToPath(zkAddress.toString)}", Some(Array[Byte]()), CreateMode.EPHEMERAL)

    lazy val members = zkClient.getChildren.usingWatcher(new CuratorWatcher {
      override def process(event: WatchedEvent): Unit = {
        log.info("[membership] membership watch event:{}", event)
        event.getType match {
          case EventType.NodeChildrenChanged =>
            refresh(zkClient.getChildren.usingWatcher(this).forPath("/members"))
          case _ =>
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
                                           implicit val segmentationLogic:SegmentationLogic) extends Actor with Logging {

  import segmentationLogic._

  private[this] implicit val log = logger
  private[cluster] var segmentsToPartitions = Map.empty[String, Set[ByteString]]
  private[cluster] var partitionsToMembers = Map.empty[ByteString, Set[Address]]
  private[cluster] var notifyOnDifference = Set.empty[ActorPath]

  def initialize = {
    segmentsToPartitions = zkClient.getChildren.forPath("/segments").map{segment => segment -> watchOverSegment(segment)}.toMap
  }

  override def preStart = {
    initialize
  }

  def watchOverSegment(segment:String) = {

    val segmentZkPath = s"/segments/${keyToPath(segment)}"
    //watch over changes of creation/removal of any partition (watcher over /partitions)
    lazy val segmentWatcher: CuratorWatcher = new CuratorWatcher {
      override def process(event: WatchedEvent): Unit = {
        event.getType match {
          case EventType.NodeChildrenChanged =>
            self ! ZkPartitionsChanged(segment, refresh(zkClient.getChildren.forPath(segmentZkPath), this))
          case _ =>
        }
      }
    }
    //watch over changes of members of a partition (watcher over /partitions/some-partition)
    lazy val partitionWatcher: CuratorWatcher = new CuratorWatcher {
      override def process(event: WatchedEvent): Unit = {
        event.getType match {
          case EventType.NodeChildrenChanged =>
            self ! ZkPartitionsChanged(segment, refresh(zkClient.getChildren.forPath(segmentZkPath), this))
          case _ =>
        }
      }
    }

    def refresh(partitions: Seq[String], partitionWatcher:CuratorWatcher): Map[ByteString, Set[Address]] = {
      partitions.map(partitionZNode => {
        ByteString(pathToKey(partitionZNode)) -> (try {
          zkClient.getChildren.usingWatcher(partitionWatcher).forPath(s"$segmentZkPath/$partitionZNode")
            .filterNot(_ == "$size")
            .map(memberZNode => AddressFromURIString(pathToKey(memberZNode))).toSet
          //the member data stored at znode is implicitly converted to Option[Address] which says where the member is in Akka
        }
        catch{
          case _:NoNodeException => null
          case t:Throwable => log.error("partitions refresh failed due to unknown reason: {}", t); null
        })
      }).filterNot(_._2 == null).toMap
    }

    //initialize with the current set of partitions
    lazy val partitions = zkClient.getChildren.usingWatcher(segmentWatcher).forPath(segmentZkPath)

    //initialize partitionsToMembers immediately
    val partitionsToMembers: Map[ByteString, Set[Address]] = refresh(partitions, partitionWatcher)
    self ! ZkPartitionsChanged(segment, partitionsToMembers)

    partitionsToMembers.keySet
  }

  def receive: Actor.Receive = {

    case ZkClientUpdated(updated) =>
      zkClient = updated
      initialize

    case origin @ ZkPartitionsChanged(segment, change) => //partition changes found in zk
      log.debug("[partitions] partitions change detected from zk: {}", change.map{case (key, members) => keyToPath(key) -> members})

      val numOfNodes = zkClient.getChildren.forPath("/members").size
      val (effects, onboards, dropoffs) = applyChanges(segmentsToPartitions, partitionsToMembers, origin, numOfNodes)
      segmentsToPartitions += segment -> effects.keySet

      if(dropoffs.nonEmpty || onboards.nonEmpty) {
        partitionsToMembers = effects
        //reduced the diff events, notifying only when the expected size have reached! (either the total members or the expected size)
        val diff = onboards.map{alter => alter -> orderByAge(alter, partitionsToMembers.getOrElse(alter, Set.empty)).toSeq}.toMap ++
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

    case ZkRebalance(planned) =>
      log.info("[partitions] rebalance partitions based on plan:{}", planned)
      def addressee(address:Address):Either[ActorRef, ActorSelection] =
        if(address == zkAddress)
          Left(self)
        else
          Right(context.actorSelection(self.path.toStringWithAddress(address)))

      val result = Try {
        planned.foldLeft(true){(result, assign) => {
          val partitionKey = assign._1
          val servants = partitionsToMembers.getOrElse(partitionKey, Set.empty[Address])
          val onboards = assign._2.diff(servants)
          val dropoffs = servants.diff(assign._2)
          val zkPath = partitionZkPath(partitionKey)

          log.debug("[partitions] onboards:{} and dropoffs:{}", onboards, dropoffs)
          implicit val timeout = Timeout(3000, TimeUnit.MILLISECONDS)

          onboards.foldLeft(result) { (successful, it) =>
            successful && (addressee(it) match {
              case Left(me) => me.tell(ZkPartitionOnboard(partitionKey, zkPath), me)
                true
              case Right(other) =>
                Await.result(other ? ZkPartitionOnboard(partitionKey, zkPath), timeout.duration).asInstanceOf[Boolean]
            })
          } && dropoffs.foldLeft(result) { (successful, it) =>
            successful && (addressee(it) match {
              case Left(me) => me.tell(ZkPartitionDropoff(partitionKey, zkPath), me)
                true
              case Right(other) =>
                Await.result(other ? ZkPartitionDropoff(partitionKey, zkPath), timeout.duration).asInstanceOf[Boolean]
            })
          }
        }}
      }
      log.debug("[partitions] rebalance plan done:{}", result)
      sender() ! result

    case ZkRemovePartition(partitionKey) =>
      safelyDiscard(partitionZkPath(partitionKey))
      notifyOnDifference.foreach { listener => context.actorSelection(listener) ! ZkPartitionRemoval(partitionKey)}

    case ZkMonitorPartition(onDifference) =>
      log.debug("[partitions] monitor partitioning from:{}", sender().path)
      notifyOnDifference = notifyOnDifference ++ onDifference

    case ZkStopMonitorPartition(stopOnDifference) =>
      log.debug("[partitions] stop monitor partitioning from:{}", sender().path)
      notifyOnDifference = notifyOnDifference -- stopOnDifference

    case ZkPartitionOnboard(partitionKey, zkPath) => //partition assignment handling
      log.debug("[partitions] assignment:{} with zkPath:{} replying to:{}", keyToPath(partitionKey), zkPath, sender().path)
      guarantee(zkPath, None)
      //mark acceptance
      guarantee(s"$zkPath/${keyToPath(zkAddress.toString)}", Some(Array[Byte]()), CreateMode.EPHEMERAL)
      sender() ! true

    case ZkPartitionDropoff(partitionKey, zkPath) =>
      log.debug("[partitions] release:{} with zkPath:{} replying to:{}", keyToPath(partitionKey), zkPath, sender().path)
      safelyDiscard(s"$zkPath/${keyToPath(zkAddress.toString)}")
      sender() ! true
  }

  private[cluster] def applyChanges(segmentsToPartitions:Map[String, Set[ByteString]],
                                    partitionsToMembers:Map[ByteString, Set[Address]],
                                    changed:ZkPartitionsChanged,
                                    numOfNodes:Int) = {

    val impacted = partitionsToMembers.filterKeys(segmentsToPartitions.getOrElse(changed.segment, Set.empty).contains(_)).keySet
    //https://github.scm.corp.ebay.com/Squbs/chnlsvc/issues/49
    //we'll notify only when the partition has reached its expected size (either the total number of VMs or the required partition size)
    //any change inbetween will be silently ignored, as we know leader will rebalance and trigger another event to reach the expected size eventually
    val onboards = changed.partitions.keySet.filter{partitionKey => changed.partitions.getOrElse(partitionKey, Set.empty).size == Math.min(try{
          bytesToInt(zkClient.getData.forPath(sizeOfParZkPath(partitionKey)))
        } catch {
          case _ => 0 //in case the $size node is being removed
        }, numOfNodes) &&
      partitionsToMembers.getOrElse(partitionKey, Set.empty) != changed.partitions.getOrElse(partitionKey, Set.empty)
    }
    val dropoffs = changed.partitions.keySet.filter{partitionKey => changed.partitions.getOrElse(partitionKey, Set.empty).isEmpty}.filter(impacted.contains(_))

    log.debug("[partitions] applying changes:{} against:{}, onboards:{}, dropoffs:{}",
      changed.partitions.map{case (key, members) => keyToPath(key) -> members},
      partitionsToMembers.filterKeys(impacted.contains(_)).map{case (key, members) => keyToPath(key) -> members},
      onboards,
      dropoffs)

    //drop off members no longer in the partition
    ((partitionsToMembers ++ changed.partitions).filterKeys(!dropoffs.contains(_)),
      onboards -- dropoffs,
      dropoffs)
  }
}

case object ZkRebalanceRetry
/**
 * The main Actor of ZkCluster
 * @param zkClient
 * @param zkAddress
 */
class ZkClusterActor(implicit var zkClient: CuratorFramework,
                     zkAddress:Address,
                     rebalanceLogic:RebalanceLogic,
                     segmentationLogic:SegmentationLogic) extends FSM[ZkClusterState, ZkClusterData] with Stash with Logging {

  import segmentationLogic._

  private[this] implicit val log = logger

  private[cluster] var whenZkClientUpdated = Seq.empty[ActorPath]

  private[cluster] def partitionManager = context.actorSelection("../zkPartitions")

  private[cluster] def requires(partitionKey:ByteString):Int = try{
    bytesToInt(zkClient.getData.forPath(sizeOfParZkPath(partitionKey)))
  }
  catch{
    case _ => 0
  }

  private[cluster] def rebalance(partitionsToMembers:Map[ByteString, Set[Address]], members:Set[Address]):Option[Map[ByteString, Set[Address]]] = {

    val candidates = (if(rebalanceLogic.spareLeader) members.filterNot(_ == stateData.leader.getOrElse(null)) else members).toSeq
    val plan = rebalanceLogic.rebalance(rebalanceLogic.compensate(partitionsToMembers, candidates, requires _), members)

    log.info("[leader] rebalance planned as:{}", plan.map{case (key, members) => keyToPath(key) -> members})
    implicit val timeout = Timeout(10000, TimeUnit.MILLISECONDS)
    Await.result(partitionManager ? ZkRebalance(plan), timeout.duration) match {
      case Success(true) =>
        log.info("[leader] rebalance successfully done")
        Some(plan)
      case _ =>
        log.info("[leader] rebalance timeout")
        None
    }
  }

  private[this] val mandatory:StateFunction = {

    case Event(ZkClientUpdated(updated), _) =>
      zkClient = updated
      whenZkClientUpdated.foreach(context.actorSelection(_) ! updated)
      stay

    case Event(ZkMonitorClient, _) =>
      whenZkClientUpdated = whenZkClientUpdated :+ sender().path
      stay

    case Event(ZkQueryMembership, zkClusterData) =>
      sender() ! ZkMembership(zkClusterData.members)
      stay

    case Event(origin: ZkMonitorPartition, _) =>
      log.info("[follower/leader] monitor partitioning from:{}", sender().path)
      partitionManager forward origin
      stay

    case Event(origin: ZkStopMonitorPartition, _) =>
      log.info("[follower/leader] stop monitor partitioning from:{}", sender().path)
      partitionManager forward origin
      stay

    case Event(ZkListPartitions(member), _) =>
      sender() ! ZkPartitions(stateData.partitionsToMembers.collect{
        case (partitionKey:ByteString, members:Set[Address]) if members.contains(member) => partitionKey
      }.toSeq)
      stay
  }

  private[this] def init:(Map[String, Set[ByteString]], Map[ByteString, Set[Address]]) = {

    val segments = zkClient.getChildren.forPath("/segments").map(pathToKey(_))

    val segmentsToPartitions:Map[String, Seq[String]] = segments.map(segment =>
      segment -> zkClient.getChildren.forPath(s"/segments/${keyToPath(segment)}").map(pathToKey(_)).toSeq
    ).toMap

    val partitionsToMembers = segmentsToPartitions.foldLeft(Map.empty[ByteString, Set[Address]]){(memoize, pair) =>
      memoize ++ pair._2.map(ByteString(_) -> Set.empty[Address])
    }

    (segmentsToPartitions.mapValues(_.map(partition => ByteString(partition)).toSet), partitionsToMembers)
  }

  private[this] val (segmentsToPartitions, partitionsToMembers) = init

  startWith(ZkClusterUninitialized, ZkClusterData(None, Set.empty, segmentsToPartitions, partitionsToMembers))

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
      stay

    case Event(ZkMembersChanged(members), zkClusterData) =>
      log.info("[leader] membership updated:{}", members)

      val dropoffs = zkClusterData.members.diff(members)
      val filtered = if(dropoffs.nonEmpty)
        zkClusterData.partitionsToMembers.mapValues{servants => servants.filterNot(dropoffs.contains(_))}
      else
        zkClusterData.partitionsToMembers

      rebalance(filtered, members) match {
        case Some(rebalanced) =>
          stay using zkClusterData.copy(members = members, partitionsToMembers = rebalanced)
        case None =>
          self ! ZkRebalanceRetry
          stay using zkClusterData.copy(members = members)
      }

    case Event(origin @ ZkQueryPartition(partitionKey, notification, Some(requires), props, _), zkClusterData) =>
      log.info("[leader] partition creation:{}", keyToPath(partitionKey))

      val zkPath = guarantee(partitionZkPath(partitionKey), Some(props), CreateMode.PERSISTENT)
      guarantee(sizeOfParZkPath(partitionKey), Some(requires), CreateMode.PERSISTENT)

      rebalance(zkClusterData.partitionsToMembers + (partitionKey -> Set.empty), zkClusterData.members) match {
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
      partitionManager forward remove
      stay

    case Event(ZkRebalanceRetry, zkClusterData) =>
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
      partitionManager ! ZkMonitorPartition(onDifference = Set(self.path))

    case ZkClusterUninitialized -> ZkClusterActiveAsLeader =>
      //unstash all messages uninitialized state couldn't handle
      unstashAll

    case ZkClusterActiveAsFollower -> ZkClusterActiveAsLeader =>
      //as the leader, i no longer need to handle ZkPartitionsChanged event, as i drive the change instead, ZkPartitionsManager will accept my partitionsToMembers
      partitionManager ! ZkStopMonitorPartition(onDifference = Set(self.path))

    case ZkClusterActiveAsLeader -> ZkClusterActiveAsFollower =>
      //as a follower, i have to listen to the ZkPartitionsChanged event, as it's driven by ZkPartitionsManager and i must update my partitionsToMembers snapshot
      partitionManager ! ZkMonitorPartition(onDifference = Set(self.path))
  }
}

object ZkCluster extends ExtensionId[ZkCluster] with ExtensionIdProvider with Logging {

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

    new ZkCluster(system, zkAddress, zkConnectionString, zkNamespace, DefaultSegmentationLogic(zkSegments), rebalanceLogic = DataCenterAwareRebalanceLogic(spareLeader = zkSpareLeader))
  }

  private[cluster] def external(system:ExtendedActorSystem):Address = Address("akka.tcp", system.name, ConfigUtil.ipv4, system.provider.getDefaultAddress.port.getOrElse(8086))
}