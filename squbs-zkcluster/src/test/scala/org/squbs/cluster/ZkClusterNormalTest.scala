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

import org.apache.pekko.util.ByteString
import org.squbs.cluster.test.{ZkClusterMultiActorSystemTestKit, ZkClusterTestHelper}

class ZkClusterNormalTest extends ZkClusterMultiActorSystemTestKit("ZkClusterNormalTest") with ZkClusterTestHelper {

  "ZkCluster" should "elect the leader and sync with all the members" in {
    // query the leader from any member
    val anyMember = pickASystemRandomly()
    val leader = getLeader(anyMember)
    // leader information should be in sync across all members
    checkLeaderInSync(leader)
  }

  "ZkCluster" should "not change leader if add or remove follower" in {
    // query the leader
    val leader = getLeader(systemName(0))
    // kill any follower
    val killed = killFollower(leader)
    // leader should not change across the cluster
    checkLeaderInSync(leader)
    // add back the follower
    bringUpSystem(killed)
    // leader should not change across the cluster
    checkLeaderInSync(leader)
  }

  "ZkCluster" should "change leader if the leader left" in {
    // query the leader
    val leader = getLeader(systemName(0))
    // kill the leader
    val killed = killLeader(leader)
    // a new leader should be elected among the remaining followers
    val newLeader = getLeader(pickASystemRandomly())
    newLeader should not be leader
    // the remaining members should have the same information about the new leader
    checkLeaderInSync(newLeader)
    // add back the previous leader
    bringUpSystem(killed)
    // the new leader should not change across the cluster
    checkLeaderInSync(newLeader)
  }

  "ZkCluster" should "keep members set in sync" in {
    // members information should be in sync across all members
    checkMembersInSync(originalMembers)
  }

  "ZkCluster" should "update the members set if a follower left" in {
    // query the leader
    val leader = getLeader(systemName(0))
    // kill any follower
    val killed = killFollower(leader)
    // now the every one should get members set up to date
    checkMembersInSync(originalMembers - killed)
    // add back the previous follower
    bringUpSystem(killed)
    // now the every one should get members set up to date
    checkMembersInSync(originalMembers)
  }

  "ZkCluster" should "update the members set if a leader left" in {
    // query the leader
    val leader = getLeader(systemName(0))
    // kill leader
    val killed = killLeader(leader)
    // now the every one should get members set up to date
    checkMembersInSync(originalMembers - killed)
    // add back the previous leader
    bringUpSystem(killed)
    // now the every one should get members set up to date
    checkMembersInSync(originalMembers)
  }

  "ZkCluster leader" should "be able to create, resize and delete partition" in {
    // query the leader
    val leaderName = getLeader(systemName(0)).address.system
    // send partition creation query directly to leader
    val parKey = ByteString("myPar")
    zkClusterExts(leaderName) tell (ZkQueryPartition(parKey, Some("created"), Some(2)), self)
    val partitionInfo = expectMsgType[ZkPartition](timeout)
    Thread.sleep(500)
    // the partition information should be consistent across the cluster
    zkClusterExts foreach {
      case (name, ext) => ext tell (ZkQueryPartition(parKey), self)
        expectMsgType[ZkPartition](timeout).members should be (partitionInfo.members)
    }
    // send partition resize query directly to leader
    zkClusterExts(leaderName) tell (ZkQueryPartition(parKey, expectedSize = Some(3)), self)
    val resized = expectMsgType[ZkPartition](timeout)
    resized.members should have size 3
    Thread.sleep(500)
    // the resized partition information should be consistent across the cluster
    zkClusterExts foreach {
      case (name, ext) => ext tell (ZkQueryPartition(parKey, expectedSize = Some(3)), self)
        expectMsgType[ZkPartition](timeout).members should be (resized.members)
    }
    // send partition remove query directly to leader
    zkClusterExts(leaderName) tell (ZkRemovePartition(parKey), self)
    expectMsgType[ZkPartitionRemoval].partitionKey should be (parKey)
    Thread.sleep(500)
    // query the partition again
    zkClusterExts(leaderName) tell (ZkQueryPartition(parKey), self)
    expectMsgType[ZkPartitionNotFound](timeout).partitionKey should be (parKey)
    // the partition should be removed from the snapshots in every member
    zkClusterExts foreach {
      case (name, ext) => ext tell (ZkQueryPartition(parKey), self)
        expectMsgType[ZkPartitionNotFound](timeout).partitionKey should be (parKey)
    }
  }

  "ZkCluster follower" should "respond to create, resize and delete partition" in {
    // query the leader
    val leaderName = getLeader(systemName(0)).address.system
    // pick up any member other than the leader
    val followerName = pickASystemRandomly(Some(leaderName))
    // send partition creation query to follower
    val parKey = ByteString("myPar")
    zkClusterExts(followerName) tell (ZkQueryPartition(parKey, Some("created"), Some(2)), self)
    val partitionInfo = expectMsgType[ZkPartition](timeout)
    Thread.sleep(500)
    // the partition information should be consistent across the cluster
    zkClusterExts foreach {
      case (name, ext) => ext tell (ZkQueryPartition(parKey), self)
        expectMsgType[ZkPartition](timeout).members should be (partitionInfo.members)
    }
    // send partition resize query directly to leader
    zkClusterExts(followerName) tell (ZkQueryPartition(parKey, expectedSize = Some(3)), self)
    val resized = expectMsgType[ZkPartition](timeout)
    resized.members should have size 3
    Thread.sleep(500)
    // the resized partition information should be consistent across the cluster
    zkClusterExts foreach {
      case (name, ext) => ext tell (ZkQueryPartition(parKey, expectedSize = Some(3)), self)
        expectMsgType[ZkPartition](timeout).members should be (resized.members)
    }
    // send partition remove query follower
    zkClusterExts(followerName) tell (ZkRemovePartition(parKey), self)
    expectMsgType[ZkPartitionRemoval].partitionKey should be (parKey)
    Thread.sleep(500)
    // query the partition again
    zkClusterExts(followerName) tell (ZkQueryPartition(parKey), self)
    expectMsgType[ZkPartitionNotFound](timeout).partitionKey should be (parKey)
    // the partition should be removed from the snapshots in every member
    zkClusterExts foreach {
      case (name, ext) => ext tell (ZkQueryPartition(parKey), self)
        expectMsgType[ZkPartitionNotFound](timeout).partitionKey should be (parKey)
    }
  }

  "ZkCluster" should "rebalance each partition when follower left or came back" in {
    // create 2 partitions
    val par1 = ByteString("myPar1")
    val par2 = ByteString("myPar2")
    zkClusterExts(pickASystemRandomly()) tell (ZkQueryPartition(par1, Some("created"), Some(3)), self)
    expectMsgType[ZkPartition](timeout)
    zkClusterExts(pickASystemRandomly()) tell (ZkQueryPartition(par2, Some("created"), Some(3)), self)
    expectMsgType[ZkPartition](timeout)
    Thread.sleep(500)
    // query the leader
    val leader = getLeader(systemName(0))
    // kill the follower
    val killed = killFollower(leader)
    // the rebalanced partition should be consistent across the cluster
    zkClusterExts foreach {
      case (name, ext) =>
        ext tell (ZkQueryPartition(par1), self)
        val par1Info = expectMsgType[ZkPartition](timeout)
        par1Info.members should have size 3
        par1Info.members.find(_.system == killed) should be (None)
        ext tell (ZkQueryPartition(par2), self)
        val par2Info = expectMsgType[ZkPartition](timeout)
        par2Info.members should have size 3
        par2Info.members.find(_.system == killed) should be (None)
    }
    // bring up the follower
    bringUpSystem(killed)
    // the rebalanced partition should be consistent across the cluster
    zkClusterExts foreach {
      case (name, ext) =>
        ext tell (ZkQueryPartition(par1), self)
        val par1Info = expectMsgType[ZkPartition](timeout)
        par1Info.members should have size 3
        ext tell (ZkQueryPartition(par2), self)
        val par2Info = expectMsgType[ZkPartition](timeout)
        par2Info.members should have size 3
    }
  }

  "ZkCluster" should "rebalance each partition when leader left or came back" in {
    // create 2 partitions
    val par1 = ByteString("myPar1")
    val par2 = ByteString("myPar2")
    zkClusterExts(pickASystemRandomly()) tell (ZkQueryPartition(par1, Some("created"), Some(3)), self)
    expectMsgType[ZkPartition](timeout)
    zkClusterExts(pickASystemRandomly()) tell (ZkQueryPartition(par2, Some("created"), Some(3)), self)
    expectMsgType[ZkPartition](timeout)
    Thread.sleep(500)
    // query the leader
    val leader = getLeader(systemName(0))
    // kill the leader
    val killed = killLeader(leader)
    // the rebalanced partition should be consistent across the cluster
    zkClusterExts foreach {
      case (name, ext) =>
        ext tell (ZkQueryPartition(par1), self)
        val par1Info = expectMsgType[ZkPartition](timeout)
        par1Info.members should have size 3
        par1Info.members.find(_.system == killed) should be (None)
        ext tell (ZkQueryPartition(par2), self)
        val par2Info = expectMsgType[ZkPartition](timeout)
        par2Info.members should have size 3
        par2Info.members.find(_.system == killed) should be (None)
    }
    // bring up the follower
    bringUpSystem(killed)
    // the rebalanced partition should be consistent across the cluster
    zkClusterExts foreach {
      case (name, ext) =>
        ext tell (ZkQueryPartition(par1), self)
        val par1Info = expectMsgType[ZkPartition](timeout)
        par1Info.members should have size 3
        ext tell (ZkQueryPartition(par2), self)
        val par2Info = expectMsgType[ZkPartition](timeout)
        par2Info.members should have size 3
    }
  }

}
