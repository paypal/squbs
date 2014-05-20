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
* segments: number of partition segments to scale the number of partitions: https://github.scm.corp.ebay.com/huzhou/pubsubak/issues/11

~~~
zkCluster {
    connectionString = "zk-node-phx-0-213163.phx-os1.stratus.dev.ebay.com:2181,zk-node-phx-1-213164.phx-os1.stratus.dev.ebay.com:2181,zk-node-phx-2-213165.phx-os1.stratus.dev.ebay.com:2181"
    namespace = "pubsubdev"
    segments = 128
}
~~~

User Guide
----------

simply register the extension as all normal akka extension
~~~scala
val zkClusterActor = ZkCluster(system).zkClusterActor

zkClusterActor ! ZkQueryMembership
zkClusterActor ! ZkQueryLeadership
zkClusterActor ! ZkQueryPartition(partitionKey:ByteString, createOnMiss:Option[Int])
zkClusterActor ! ZkMonitorPartition(paths:Set[ActorPath)
~~~

in turn you'll receive responding messages in your actor
~~~scala
message match {
    case ZkMembership(members:Set[Address) =>
    case ZkLeadership(leader:Address) =>
    case ZkPartition(partitionKey:ByteString, members:Set[Address]) =>
    case ZkPartitionDiff(partitionsToMembers:Map[ByteString, Set[Address]) =>
}
~~~

Design
------

Read if you're making changes of zkcluster
* membership is based on `zookeeper` ephemeral nodes, closed session would alter leader with `ZkMembershipChanged`
* leadership is based on `curator` framework's `LeaderLatch`, new election will broadcast `ZkLeaderElected` to all nodes
* partitions are based on ephemeral nodes, leader manages the initial partition creation and the rebalancing thereafter
* partitions modification is only done by the leader, who asks its `ZkPartitionsManager` to enforce the modification
* `ZkPartitionsManager` of leader communicates with all other `ZkPartitionsManager` from every follower to `onboard` or `dropoff` members of a partition using `akka-remote`
* then the znode changes will trigger watchers from all nodes and the partitions become in sync finally
* whoever needs to be notified by the partitions change `ZkPartitionDiff` should send `ZkMonitorPartition` to the cluster actor getting registered

`ZkMembershipMonitor` is the actor type who handles membership & leadership
`ZkPartitionsManager` is the one who handles partitions management and leader/follower communication
`ZkClusterActor` is the interfacing actor user should be interacting with only
