# Clustering squbs Services using ZooKeeper

**Important Note:** _The ZooKeeper support in squbs is deprecated. Please use pekko clusters directly or move to more modern
cluster coordinators._

## Overview

squbs achieves clustering of services through the zkcluster module. zkcluster is an [pekko extension](http://doc.pekko.io/docs/pekko/snapshot/scala/extending-pekko.html) which leverages [ZooKeeper](https://zookeeper.apache.org/) to manage pekko cluster and partitions.
It's similar to [pekko Cluster](http://doc.pekko.io/docs/pekko/snapshot/common/cluster.html) for the functions of leadership and membership management.
However, it's richer as it provides partitioning support and eliminates the need of `entry-nodes`.

## Configuration

We'll need a `squbsconfig/zkcluster.conf` file under the runtime directory. It should provide the following properties:

* connectionString: a string delimiting all zookeeper nodes of an ensemble with comma
* namespace: a string that is a valid path of znode, which will be the parent of all znodes created thereafter
* segments: number of partition segments to scale the number of partitions

The following is an example of a `zkcluster.conf`  file content:

```
zkCluster {
    connectionString = "zk-node-01.squbs.org:2181,zk-node-02.squbs.org:2181,zk-node-03.squbs.org:2181"
    namespace = "clusteredservicedev"
    segments = 128
}
```

## User Guide

Start by simply registering the extension as all normal pekko extension. Then you access the `zkClusterActor` and use it as follows:

```scala
val zkClusterActor = ZkCluster(system).zkClusterActor

// Query the members in the cluster
zkClusterActor ! ZkQueryMembership

// Matching the response
case ZkMembership(members:Set[Address]) =>


// Query leader in the cluster
zkClusterActor ! ZkQueryLeadership
// Matching the response
case ZkLeadership(leader:Address) =>

// Query partition (expectedSize = None), create or resize (expectedSize = Some[Int])
zkClusterActor ! ZkQueryPartition(partitionKey:ByteString, notification:Option[Any] = None, expectedSize:Option[Int] = None, props:Array[Byte] = Array[Byte]())
// Matching the response
case ZkPartition(partitionKey:ByteString, members: Set[Address], zkPath:String, notification:Option[Any]) =>
case ZkPartitionNotFound(partitionKey: ByteString) =>


// Monitor or stop monitoring the partition change
zkClusterActor ! ZkMonitorPartition
zkClusterActor ! ZkStopMonitorPartition
// Matching the response
case ZkPartitionDiff(partitionKey: ByteString, onBoardMembers: Set[Address], dropOffMembers: Set[Address], props: Array[Byte] = Array.empty) =>

// Removing partition
zkClusterActor ! ZkRemovePartition(partitionKey:ByteString)
// Matching the response
case ZkPartitionRemoval(partitionKey:ByteString) =>


// List the partitions hosted by a certain member
zkClusterActor ! ZkListPartitions(address: Address)
// Matching the response
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
```

## Dependencies

Add the following dependency to your build.sbt or scala build file:

```scala
"org.squbs" %% "squbs-zkcluster" % squbsVersion
```

## Design

Read this if you're making changes of zkcluster

* Membership is based on `zookeeper` ephemeral nodes, closed session would alter leader with `ZkMembershipChanged`.
* Leadership is based on `curator` framework's `LeaderLatch`, new election will broadcast `ZkLeaderElected` to all nodes.
* Partitions are calculated by the leader and write to the znode by `ZkPartitionsManager` in leader node.
* Partitions modification is only done by the leader, who asks its `ZkPartitionsManager` to enforce the modification.
* `ZkPartitionsManager` of follower nodes will watch the znode change in Zookeeper. Once the leader change the paritions after rebalancing, `ZkPartitionsManager` in follower nodes will get notified and update their memory snapshot of the partition information.
* Whoever needs to be notified by the partitions change `ZkPartitionDiff` should send `ZkMonitorPartition` to the cluster actor getting registered.

`ZkMembershipMonitor` is the actor type handling membership & leadership.

`ZkPartitionsManager` is the actor handling partitions management.

`ZkClusterActor` is the interfacing actor users should be sending queries to.
