Overview
========

zkcluster is an `akka` extension which leverages `zookeeper` to manage akka cluster & partitions.
it's similar to `akka-cluster` module for the sake of leadership & membership management.
it's richer as it provides partitioning support, and eliminates the need of `entry-nodes`

Configuration
-------------

we'll need a `/squbsconfig/zkcluster.conf` under runtime directory, it should provide the following properties:
* connectionString: a string delimiting all zookeeper nodes of an ensemble with comma
* namespace: a string that is a valid path of znode, which will be the parent of all znodes created thereafter
* segments: number of partition segments to scale the number of partitions: https://github.corp.ebay.com/huzhou/pubsubak/issues/11

~~~
zkCluster {
    connectionString = "zk-node-phx-0-213163.phx-os1.stratus.dev.ebay.com:2181,zk-node-phx-1-213164.phx-os1.stratus.dev.ebay.com:2181,zk-node-phx-2-213165.phx-os1.stratus.dev.ebay.com:2181"
    namespace = "channelservicedev"
    segments = 128
}
~~~

User Guide
----------

simply register the extension as all normal akka extension
~~~scala
val zkClusterActor = ZkCluster(system).zkClusterActor

// query the members in the cluster
zkClusterActor ! ZkQueryMembership
case ZkMembership(members:Set[Address]) =>

// query leader in the cluster
zkClusterActor ! ZkQueryLeadership
case ZkLeadership(leader:Address) =>

// query partition (expectedSize = None), create or resize (expectedSize = Some[Int])
zkClusterActor ! ZkQueryPartition(partitionKey:ByteString, notification:Option[Any] = None, expectedSize:Option[Int] = None, props:Array[Byte] = Array[Byte]())
case ZkPartition(partitionKey:ByteString, members: Set[Address], zkPath:String, notification:Option[Any]) =>
case ZkPartitionNotFound(partitionKey: ByteString)

// monitor/stop monitor the partition change
zkClusterActor ! ZkMonitorPartition
zkClusterActor ! ZkStopMonitorPartition
case ZkPartitionDiff(partitionKey: ByteString, onBoardMembers: Set[Address], dropOffMembers: Set[Address], props: Array[Byte] = Array.empty) =>

// remove partition
zkClusterActor ! ZkRemovePartition(partitionKey:ByteString)
case ZkPartitionRemoval(partitionKey:ByteString) =>

// list the partitions hosted by a certain member
zkClusterActor ! ZkListPartitions(address: Address)
case ZkPartitions(partitionKeys:Seq[ByteString]) =>

// monitor the zookeeper connection state
val eventStream = context.system.eventStream
eventStream.subscribe(self, ZkConnected.getClass)
eventStream.subscribe(self, ZkReconnected.getClass)
eventStream.subscribe(self, ZkLost.getClass)
eventStream.subscribe(self, ZkSuspended.getClass)

// quit the cluster
zkCluster(system).zkClusterActor ! PoisonPill
// add listener when quitting the cluster
zkCluster(system).addShutdownListener(listener: () => Unit)
~~~

Design
------

Read if you're making changes of zkcluster
* membership is based on `zookeeper` ephemeral nodes, closed session would alter leader with `ZkMembershipChanged`
* leadership is based on `curator` framework's `LeaderLatch`, new election will broadcast `ZkLeaderElected` to all nodes
* partitions are calculated by the leader and write to the znode by `ZkPartitionsManager` in leader node.
* partitions modification is only done by the leader, who asks its `ZkPartitionsManager` to enforce the modification
* `ZkPartitionsManager` of follower nodes will watch the znode change in Zookeeper. Once the leader change the paritions after rebalancing, `ZkPartitionsManager` in follower nodes will get notified and update their memory snapshot of the partitions information.
* whoever needs to be notified by the partitions change `ZkPartitionDiff` should send `ZkMonitorPartition` to the cluster actor getting registered

`ZkMembershipMonitor` is the actor type who handles membership & leadership.

`ZkPartitionsManager` is the one who handles partitions management.

`ZkClusterActor` is the interfacing actor user should be sending queries to.
