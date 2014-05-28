package org.squbs.cluster

import java.io.File
import java.nio.ByteBuffer
import com.google.common.base.Charsets
import java.net.{NetworkInterface, URLDecoder, URLEncoder, InetAddress}
import org.apache.zookeeper.KeeperException.{NoNodeException, NodeExistsException}
import org.apache.zookeeper.{WatchedEvent, CreateMode}
import org.apache.zookeeper.Watcher.Event.EventType
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.RetryPolicy
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.framework.recipes.leader.LeaderLatch
import org.apache.curator.framework.state.{ConnectionState, ConnectionStateListener}
import org.apache.curator.framework.api._
import scala.Some
import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.JavaConversions._
import scala.concurrent.duration._
import akka.actor._
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.slf4j.Logging

/**
 * Created by huzhou on 3/25/14.
 */
class ZkCluster(system: ActorSystem,
                val zkAddress: Address,
                zkConnectionString: String,
                zkNamespace: String,
                implicit val segmentationLogic: SegmentationLogic,
                retryPolicy: RetryPolicy = new ExponentialBackoffRetry(1000, 3),
                rebalanceLogic:RebalanceLogic = DefaultDataCenterAwareRebalanceLogic) extends Extension with Logging {

  import ZkCluster._

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

  0.until(segmentationLogic.segmentsSize).foreach(s => {
    guarantee(s"/segments/segment-$s", Some(Array[Byte]()), CreateMode.PERSISTENT)
  })

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

case object ZkQueryLeadership
case object ZkQueryMembership
case object ZkMonitorClient

case class ZkClientUpdated(zkClient:CuratorFramework)
case class ZkLeadership(address: Address)
case class ZkMembership(members: Set[Address])

case class ZkQueryPartition(partitionKey:ByteString, //partition key
                            notification:Option[Any] = None, //notify the sender() along with query result
                            createOnMiss:Option[Int] = None, //create partition when it's missing or not, and the size in case it's to be created
                            props:Array[Byte] = Array[Byte](), //properties of the partition, plain byte array
                            members:Set[Address] = Set.empty) //used internally

case class ZkResizePartition(partitionKey:ByteString, sizeOf:Int)

case class ZkRemovePartition(partitionKey:ByteString)

case class ZkMonitorPartition(onDifference:Set[ActorPath] = Set.empty) //notify me when partitions have changes @see ZkPartitionsChanged

case class ZkStopMonitorPartition(onDifference:Set[ActorPath] = Set.empty) //stop notify me when partitions have changes @see ZkPartitionsChanged

case class ZkPartitionDiff(diff:Map[ByteString, Seq[Address]], zkPaths:Map[ByteString, String])

case class ZkPartition(partitionKey:ByteString,
                       members: Seq[Address],   //who have been assigned to be part of this partition
                       zkPath:String,           //where the partition data is stored
                       notification:Option[Any])//optional notification when the query was issued

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

  import ZkCluster._

  override def preStart = {

    //enroll in the leadership competition
    zkLeaderLatch.start

    //watch over leader changes
    val leader = zkClient.getData.usingWatcher(new CuratorWatcher {
      override def process(event: WatchedEvent): Unit = {
        logger.info("[membership] leader watch event:{}", event)
        event.getType match {
          case EventType.NodeCreated | EventType.NodeDataChanged =>
            zkClusterActor ! ZkLeaderElected(zkClient.getData.usingWatcher(this).forPath("/leader"))
          case _ =>
        }
      }
    }).forPath("/leader")

    //watch over members changes
    val me = guarantee(s"/members/${keyToPath(zkAddress.toString)}", Some(Array[Byte]()), CreateMode.EPHEMERAL)
    zkClient.sync.forPath(me)

    lazy val members = zkClient.getChildren.usingWatcher(new CuratorWatcher {
      override def process(event: WatchedEvent): Unit = {
        logger.info("[membership] membership watch event:{}", event)
        event.getType match {
          case EventType.NodeChildrenChanged =>
            refresh(zkClient.getChildren.usingWatcher(this).forPath("/members"))
          case _ =>
        }
      }
    }).forPath("/members")

    def refresh(members:Seq[String]) = zkClusterActor ! ZkMembersChanged(members.map(m => AddressFromURIString(pathToKey(m))).toSet)

    refresh(members)

    self ! ZkAcquireLeadership
    zkClusterActor ! ZkLeaderElected(leader)
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

    case ZkAcquireLeadership =>
      //repeatedly enroll in the leadership competition once the last attempt fails
      import scala.concurrent.ExecutionContext.Implicits.global

      val oneSecond = 1.second
      zkLeaderLatch.await(oneSecond.length, oneSecond.unit) match {
        case true =>
          logger.info("[membership] leadership acquired @ {}", zkAddress)
          guarantee("/leader", Some(zkAddress))
        case false =>
          context.system.scheduler.scheduleOnce(100.millis, self, ZkAcquireLeadership)
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

  import ZkCluster._
  import segmentationLogic._

  var segmentsToPartitions = Map.empty[String, Set[ByteString]]
  var partitionsToMembers = Map.empty[ByteString, Set[Address]]
  var notifyOnDifference = Set.empty[ActorPath]

  override def preStart = {
    segmentsToPartitions = zkClient.getChildren.forPath("/segments").map{segment => segment -> watchOverSegment(segment)}.toMap
  }

  def watchOverSegment(segment:String) = {

    val segmentZkPath = s"/segments/${keyToPath(segment)}"
    //watch over changes of creation/removal of any partition (watcher over /partitions)
    lazy val segmentWatcher: CuratorWatcher = new CuratorWatcher {
      override def process(event: WatchedEvent): Unit = {
        event.getType match {
          case EventType.NodeChildrenChanged =>
            self ! ZkPartitionsChanged(segment, refresh(zkClient.getChildren.usingWatcher(this).forPath(segmentZkPath), partitionWatcher))
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
          case t:Throwable => logger.error("partitions refresh failed due to unknown reason: {}", t); null
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

    case origin @ ZkPartitionsChanged(segment, change) => //partition changes found in zk
      logger.debug("[partitions] partitions change detected from zk: {}", change.map(pair => keyToPath(pair._1) -> pair._2))

      val (effects, onboards, dropoffs) = applyChanges(segmentsToPartitions, partitionsToMembers, origin)
      val difference = dropoffs.nonEmpty || onboards.exists{partitionKey => partitionsToMembers.get(partitionKey) != effects.get(partitionKey)}

      if(difference) {
        partitionsToMembers = effects

        val diff = onboards.map { alter => alter -> orderByAge(alter, partitionsToMembers.getOrElse(alter, Set.empty)).toSeq}.toMap ++ dropoffs.map { dropoff => dropoff -> Seq.empty}
        val zkPaths = diff.keySet.map { partitionKey => partitionKey -> partitionZkPath(partitionKey)}.toMap

        logger.debug("[partitions] change consolidated as:{} and notifying:{}", diff, notifyOnDifference)
        notifyOnDifference.foreach { listener => context.actorSelection(listener) ! ZkPartitionDiff(diff, zkPaths)}
      }
      else{
        logger.debug("[partitions] change ignored as no difference was found and notifying no one")
      }

    case ZkQueryPartition(partitionKey, notification, _, _, _) =>
      logger.info("[partitions] partition: {} identified", keyToPath(partitionKey))
      //notification is the attachment part of the partition query, it will allow callback styled message handling at the sender()
      sender() ! ZkPartition(partitionKey, orderByAge(partitionKey, partitionsToMembers.getOrElse(partitionKey, Set.empty)), partitionZkPath(partitionKey), notification)

    case ZkRebalance(planned) =>
      logger.info("[partitions] rebalance partitions based on plan:{}", planned)
      def addressee(address:Address) =
        if(address == zkAddress)
          context.actorSelection(self.path)
        else
          context.actorSelection(self.path.toStringWithAddress(address))

      planned.foreach(assign => {
        val partitionKey = assign._1
        val servants = partitionsToMembers.getOrElse(partitionKey, Set.empty[Address])
        val onboards = assign._2.diff(servants)
        val dropoffs = servants.diff(assign._2)
        val zkPath = partitionZkPath(partitionKey)

        logger.debug("[partitions] onboards:{} and dropoffs:{}", onboards, dropoffs)
        onboards.foreach{it => addressee(it) ! ZkPartitionOnboard(partitionKey, zkPath)}
        dropoffs.foreach{it => addressee(it) ! ZkPartitionDropoff(partitionKey, zkPath)}
      })

    case ZkRemovePartition(partitionKey) =>
      safelyDiscard(partitionZkPath(partitionKey))
      safelyDiscard(sizeOfParZkPath(partitionKey))
      sender() ! ZkPartition(partitionKey, Seq.empty, partitionZkPath(partitionKey), None)

    case ZkMonitorPartition(onDifference) =>
      logger.debug("[partitions] monitor partitioning from:{}", sender().path)
      notifyOnDifference = notifyOnDifference ++ onDifference

    case ZkStopMonitorPartition(stopOnDifference) =>
      logger.debug("[partitions] stop monitor partitioning from:{}", sender().path)
      notifyOnDifference = notifyOnDifference -- stopOnDifference

    case ZkPartitionOnboard(partitionKey, zkPath) => //partition assignment handling
      logger.debug("[partitions] assignment:{} with zkPath:{}", keyToPath(partitionKey), zkPath)
      guarantee(zkPath, None)
      //mark acceptance
      guarantee(s"$zkPath/${keyToPath(zkAddress.toString)}", Some(Array[Byte]()), CreateMode.EPHEMERAL)

    case ZkPartitionDropoff(partitionKey, zkPath) =>
      logger.debug("[partitions] release:{} with zkPath:{}", keyToPath(partitionKey), zkPath)
      safelyDiscard(s"$zkPath/${keyToPath(zkAddress.toString)}")
  }
}

/**
 * The main Actor of ZkCluster
 * @param zkClient
 * @param zkAddress
 */
class ZkClusterActor(implicit var zkClient: CuratorFramework,
                     zkAddress:Address,
                     rebalanceLogic:RebalanceLogic,
                     segmentationLogic:SegmentationLogic) extends FSM[ZkClusterState, ZkClusterData] with Stash with Logging {

  import ZkCluster._
  import segmentationLogic._

  var whenZkClientUpdated = Seq.empty[ActorPath]

  def partitionManager = context.actorSelection("../zkPartitions")

  def requires(partitionKey:ByteString):Int = bytesToInt(zkClient.getData.forPath(sizeOfParZkPath(partitionKey)))

  def rebalance(partitionsToMembers:Map[ByteString, Set[Address]], members:Set[Address]) = {

    val plan = rebalanceLogic.rebalance(rebalanceLogic.compensate(partitionsToMembers, members.toSeq, requires _), members)

    logger.info("[leader] rebalance planned as:{}", plan)
    partitionManager ! ZkRebalance(plan)

    plan
  }

  val mandatory:StateFunction = {

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
      logger.info("[follower/leader] monitor partitioning from:{}", sender().path)
      partitionManager forward origin
      stay

    case Event(origin: ZkStopMonitorPartition, _) =>
      logger.info("[follower/leader] stop monitor partitioning from:{}", sender().path)
      partitionManager forward origin
      stay
  }

  def init:(Map[String, Set[ByteString]], Map[ByteString, Set[Address]]) = {

    val segments = zkClient.getChildren.forPath("/segments").map(pathToKey(_))

    val segmentsToPartitions:Map[String, Seq[String]] = segments.map(segment =>
      segment -> zkClient.getChildren.forPath(s"/segments/${keyToPath(segment)}").map(pathToKey(_)).toSeq
    ).toMap

    val partitionsToMembers = segmentsToPartitions.foldLeft(Map.empty[ByteString, Set[Address]]){(memoize, pair) =>
      memoize ++ pair._2.map(ByteString(_) -> Set.empty[Address])
    }

    (segmentsToPartitions.mapValues(_.map(partition => ByteString(partition)).toSet), partitionsToMembers)
  }

  val initialized = init

  startWith(ZkClusterUninitialized, ZkClusterData(None, Set.empty, initialized._1, initialized._2))

  when(ZkClusterUninitialized)(mandatory orElse {

    case Event(ZkLeaderElected(Some(address)), zkClusterData) =>
      logger.info("[uninitialized] leader elected:{} and my zk address:{}", address, zkAddress)
      if(address.hostPort == zkAddress.hostPort)
        goto(ZkClusterActiveAsLeader) using zkClusterData.copy(leader = Some(address),
          partitionsToMembers = rebalance(zkClusterData.partitionsToMembers, zkClusterData.members))
      else
        goto(ZkClusterActiveAsFollower) using zkClusterData.copy(leader = Some(address))

    case Event(ZkMembersChanged(members), zkClusterData) =>
      logger.info("[uninitialized] membership updated:{}", members)
      stay using zkClusterData.copy(members = members)

    case Event(_, _) =>
      stash
      stay
  })

  when(ZkClusterActiveAsFollower)(mandatory orElse {

    case Event(ZkLeaderElected(Some(address)), zkClusterData) =>
      if(address.hostPort == zkAddress.hostPort)
        goto(ZkClusterActiveAsLeader) using zkClusterData.copy(leader = Some(address),
          partitionsToMembers = rebalance(zkClusterData.partitionsToMembers, zkClusterData.members))
      else
        stay

    case Event(ZkQueryLeadership, zkClusterData) =>
      logger.info("[follower] leadership query answered:{} to:{}", zkClusterData.leader, sender().path)
      zkClusterData.leader.foreach(address => sender() ! ZkLeadership(address))
      stay

    case Event(ZkMembersChanged(members), zkClusterData) =>
      logger.info("[follower] membership updated:{}", members)
      stay using zkClusterData.copy(members = members)

    case Event(ZkPartitionDiff(diff, _), zkClusterData) =>
      stay using zkClusterData.copy(partitionsToMembers = diff.foldLeft(zkClusterData.partitionsToMembers){(memoize, change) => memoize.updated(change._1, change._2.toSet)})

    case Event(origin @ ZkQueryPartition(key, _, Some(size), props, members), zkClusterData) =>
      logger.info("[follower] partition query forwarded to leader:{}", zkClusterData.leader)
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
      logger.info("[leader] leadership query answered:{} to:{}", zkClusterData.leader, sender().path)
      zkClusterData.leader.foreach(address => sender() ! ZkLeadership(address))
      stay

    case Event(ZkMembersChanged(members), zkClusterData) =>
      logger.info("[leader] membership updated:{}", members)

      val dropoffs = zkClusterData.members.diff(members)
      val filtered = if(dropoffs.nonEmpty)
        zkClusterData.partitionsToMembers.mapValues{servants => servants.filterNot(dropoffs.contains(_))}
      else
        zkClusterData.partitionsToMembers

      stay using zkClusterData.copy(members = members, partitionsToMembers = rebalance(filtered, members))

    case Event(ZkQueryPartition(partitionKey, notification, Some(requires), props, _), zkClusterData) =>
      logger.info("[leader] partition creation:{}", keyToPath(partitionKey))

      val zkPath = guarantee(partitionZkPath(partitionKey), Some(props), CreateMode.PERSISTENT)
      guarantee(sizeOfParZkPath(partitionKey), Some(requires), CreateMode.PERSISTENT)

      val plan = rebalance(zkClusterData.partitionsToMembers + (partitionKey -> Set.empty), zkClusterData.members)
      try {
        stay using zkClusterData.copy(partitionsToMembers = plan)
      }
      finally{
        sender() ! ZkPartition(partitionKey, orderByAge(partitionKey, plan.getOrElse(partitionKey, Set.empty)), zkPath, notification)
      }

    case Event(ZkQueryPartition(partitionKey, notification, None, _, _), zkClusterData) =>
      logger.info("[leader] partition query:{} handled by leader cluster actor", keyToPath(partitionKey))
      sender() ! ZkPartition(partitionKey,
        orderByAge(partitionKey, zkClusterData.partitionsToMembers.getOrElse(partitionKey, Set.empty)),
        partitionZkPath(partitionKey),
        notification)
      stay

    case Event(ZkResizePartition(partitionKey, sizeOf), zkClusterData) =>
      logger.info("[leader] resize partition:{} forwarded to partition manager", keyToPath(partitionKey))
      guarantee(sizeOfParZkPath(partitionKey), Some(intToBytes(sizeOf)), CreateMode.PERSISTENT)
      stay using zkClusterData.copy(partitionsToMembers = rebalance(zkClusterData.partitionsToMembers, zkClusterData.members))

    case Event(remove:ZkRemovePartition, zkClusterData) =>
      logger.info("[leader] remove partition:{} forwarded to partition manager", keyToPath(remove.partitionKey))
      partitionManager forward remove
      stay
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

trait RebalanceLogic {

  /**
   * @return partitionsToMembers compensated when size in service is short compared with what's required
   */
  def compensate(partitionsToMembers:Map[ByteString, Set[Address]], members:Seq[Address], size:(ByteString => Int)):Map[ByteString, Set[Address]] = {

    partitionsToMembers.map(assign => {
      val partitionKey = assign._1
      val servants = assign._2
      val requires = size(partitionKey)

      servants.size match {
        case size:Int if size < requires => //shortage, must be compensated
          partitionKey -> (servants ++ members.filterNot(servants.contains(_)).take(requires - servants.size))
        case size:Int if size > requires => //overflow, reduce the servants
          partitionKey -> servants.take(requires)
        case _ =>
          assign
      }
    })
  }

  /**
   * @return partitionsToMembers rebalanced
   */
  def rebalance(partitionsToMembers:Map[ByteString, Set[Address]], members:Set[Address]):Map[ByteString, Set[Address]] = {

    val utilization = partitionsToMembers.foldLeft(Map.empty[Address, Seq[ByteString]]){(memoize, assign) =>
      assign._2.foldLeft(memoize){(memoize, member) =>
        memoize.updated(member, memoize.getOrElse(member, Seq.empty) :+ assign._1)
      }
    }

    val ordered = members.toSeq.sortWith((one, two) => utilization.getOrElse(one, Seq.empty).size < utilization.getOrElse(two, Seq.empty).size)

    @tailrec def rebalanceRecursively(partitionsToMembers:Map[ByteString, Set[Address]],
                                      utilization:Map[Address, Seq[ByteString]],
                                      ordered:Seq[Address]):Map[ByteString, Set[Address]] = {

      val overflows = utilization.getOrElse(ordered.last, Seq.empty)
      val underflow = utilization.getOrElse(ordered.head, Seq.empty)

      if (overflows.size - underflow.size > 1) {
        val move = overflows.head
        val updatedUtil = utilization.updated(ordered.last, overflows.tail).updated(ordered.head, underflow :+ move)
        var headOrdered = ordered.tail.takeWhile(next => updatedUtil.getOrElse(ordered.head, Seq.empty).size < updatedUtil.getOrElse(next, Seq.empty).size)
        headOrdered = (headOrdered :+ ordered.head) ++ ordered.tail.drop(headOrdered.size)
        var rearOrdered = headOrdered.takeWhile(next => updatedUtil.getOrElse(headOrdered.last, Seq.empty).size > updatedUtil.getOrElse(next, Seq.empty).size)
        rearOrdered = (rearOrdered :+ headOrdered.last) ++ headOrdered.drop(rearOrdered.size).dropRight(1)/*drop the headOrdered.last*/

        rebalanceRecursively(partitionsToMembers.updated(move, partitionsToMembers.getOrElse(move, Set.empty) + ordered.head - ordered.last), updatedUtil, rearOrdered)
      }
      else
        partitionsToMembers
    }

    rebalanceRecursively(partitionsToMembers, utilization, ordered)
  }
}

trait SegmentationLogic {

  import ZkCluster._

  val segmentsSize:Int

  def segmentation(partitionKey:ByteString) = s"segment-${Math.abs(partitionKey.hashCode) % segmentsSize}"

  def partitionZkPath(partitionKey:ByteString) = s"/segments/${segmentation(partitionKey)}/${keyToPath(partitionKey)}"

  def sizeOfParZkPath(partitionKey:ByteString) = s"${partitionZkPath(partitionKey)}/$$size"
}

object ZkCluster extends ExtensionId[ZkCluster] with ExtensionIdProvider with Logging {

  override def lookup(): ExtensionId[_ <: Extension] = ZkCluster

  override def createExtension(system: ExtendedActorSystem): ZkCluster = {

    val source = new File("squbsconfig", "zkcluster.conf")
    logger.info("[zkcluster] reading configuration from:{}", source.getAbsolutePath)
    val configuration = ConfigFactory.parseFile(source)

    val zkConnectionString = configuration.getString("zkCluster.connectionString")
    val zkNamespace = configuration.getString("zkCluster.namespace")
    val zkSegments = configuration.getInt("zkCluster.segments")
    val zkAddress = external(system)
    logger.info("[zkcluster] connection to:{} and namespace:{} with segments:{} using address:{}", zkConnectionString, zkNamespace, zkSegments.toString, zkAddress)

    new ZkCluster(system, zkAddress, zkConnectionString, zkNamespace, DefaultSegmentationLogic(zkSegments))
  }

  object DefaultRebalanceLogic extends RebalanceLogic

  case class DefaultSegmentationLogic(segmentsSize:Int) extends SegmentationLogic

  def guarantee(path:String, data:Option[Array[Byte]], mode:CreateMode = CreateMode.EPHEMERAL)(implicit zkClient:CuratorFramework):String = {
    try{
      data match {
        case None => zkClient.create.withMode(mode).forPath(path)
        case Some(bytes) => zkClient.create.withMode(mode).forPath(path, bytes)
      }
    }
    catch{
      case e: NodeExistsException => {
        if(data.nonEmpty && data.get.length > 0){
          zkClient.setData.forPath(path, data.get)
        }
        path
      }
      case e: Throwable => {
        logger.info("leader znode creation failed due to %s\n", e)
        path
      }
    }
  }

  def safelyDiscard(path:String, recursive:Boolean = true)(implicit zkClient:CuratorFramework):String = {
    import scala.collection.JavaConversions._
    try{
      if(recursive)
        zkClient.getChildren.forPath(path).foreach(child => safelyDiscard(s"$path/$child", recursive))

      zkClient.delete.forPath(path)
      path
    }
    catch{
      case e: NoNodeException =>
        path
      case e: Throwable =>
        path
    }
  }

  private[cluster] def orderByAge(partitionKey:ByteString, members:Set[Address])(implicit zkClient:CuratorFramework, zkSegmentationLogic:SegmentationLogic):Seq[Address] = {

    if(members.isEmpty)
      Seq.empty[Address]
    else {
      val zkPath = zkSegmentationLogic.partitionZkPath(partitionKey)
      val ages = zkClient.getChildren.forPath(zkPath).filterNot(_ == "$size")
        .map(child =>
        AddressFromURIString.parse(pathToKey(child)) -> zkClient.checkExists.forPath(s"$zkPath/$child").getCtime).toMap
      //this is to ensure that the partitions query result will always give members in the order of oldest to youngest
      //this should make data sync easier, the newly onboard member should always consult with the 1st member in the query result to sync with.
      members.toSeq.sortBy(ages.getOrElse(_, 0L))
    }
  }

  private[cluster] def applyChanges(segmentsToPartitions:Map[String, Set[ByteString]],
                                    partitionsToMembers:Map[ByteString, Set[Address]],
                                    changed:ZkPartitionsChanged) = {
    val impacted = partitionsToMembers.filterKeys(segmentsToPartitions.getOrElse(changed.segment, Set.empty).contains(_)).keySet
    val onboards = changed.partitions.keySet
    val dropoffs = impacted.diff(changed.partitions.keySet)

    //drop off members no longer in the partition
    (partitionsToMembers.filterKeys(!dropoffs.contains(_))
      .map(assign => assign._1 -> (if(changed.partitions.getOrElse(assign._1, Set.empty).nonEmpty) changed.partitions(assign._1) else assign._2)) ++ onboards.map(assign => assign -> changed.partitions(assign)),
      onboards,
      dropoffs)
  }

  def ipv4 = {
    val addresses = mutable.Set.empty[String]
    val enum = NetworkInterface.getNetworkInterfaces
    while (enum.hasMoreElements) {
      val addrs = enum.nextElement.getInetAddresses
      while (addrs.hasMoreElements) {
        addresses += addrs.nextElement.getHostAddress
      }
    }

    val pattern = "\\d+\\.\\d+\\.\\d+\\.\\d+".r
    val matched = addresses.filter({
      case pattern() => true
      case _ => false
    })
      .filter(_ != "127.0.0.1")

    matched.head
  }

  private[cluster] def myAddress = InetAddress.getLocalHost.getCanonicalHostName match {
    case "localhost" => ipv4
    case h:String => h
  }

  private[cluster] def external(system:ExtendedActorSystem):Address = Address("akka.tcp", system.name, ipv4, system.provider.getDefaultAddress.port.getOrElse(8086))

  def keyToPath(name:String):String = URLEncoder.encode(name, "utf-8")

  def pathToKey(name:String):String = URLDecoder.decode(name, "utf-8")

  private[cluster] val BYTES_OF_INT = Integer.SIZE / java.lang.Byte.SIZE

  implicit def intToBytes(integer:Int):Array[Byte] = {
    val buf = ByteBuffer.allocate(BYTES_OF_INT)
    buf.putInt(integer)
    buf.rewind
    buf.array()
  }

  implicit def bytesToInt(bytes:Array[Byte]) = ByteBuffer.wrap(bytes).getInt

  implicit def bytesToUtf8(bytes:Array[Byte]):String = new String(bytes, Charsets.UTF_8)

  implicit def byteStringToUtf8(bs:ByteString):String = new String(bs.toArray, Charsets.UTF_8)

  implicit def addressToBytes(address:Address):Array[Byte] = {
    address.toString.getBytes(Charsets.UTF_8)
  }

  implicit def bytesToAddress(bytes:Array[Byte]):Option[Address] = {
    bytes match {
      case null => None
      case _ if bytes.length == 0 => None
      case _ => {
        val uri = new String(bytes, Charsets.UTF_8)
        Some(AddressFromURIString(uri))
      }
    }
  }

  implicit def bytesToByteString(bytes:Array[Byte]):ByteString = {
    ByteString(bytes)
  }
}