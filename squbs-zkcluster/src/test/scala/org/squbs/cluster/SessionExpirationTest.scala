/*
 *  Copyright 2015 PayPal
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

import akka.actor.{Terminated, PoisonPill}
import akka.testkit.ImplicitSender
import org.apache.curator.test.KillSession
import org.scalatest.{FlatSpecLike, Matchers}

import scala.concurrent.duration._
import scala.language.postfixOps

class SessionExpirationTest extends ZkClusterMultiActorSystemTestKit("SessionExpirationTest")
  with ImplicitSender with FlatSpecLike with Matchers {
  override val timeout: FiniteDuration = 60 seconds
  override val clusterSize: Int = 1

  "ZkCluster" should "response to session expiration" in {
    val cluster = ZkCluster(system)

    cluster.zkClusterActor ! ZkQueryLeadership
    val leader = expectMsgType[ZkLeadership](timeout)

    cluster.zkClusterActor ! ZkMonitorClient
    KillSession.kill(cluster.curatorFwkWithNs().getZookeeperClient.getZooKeeper)
    expectMsgType[ZkClientUpdated](timeout).zkClient

    cluster.zkClusterActor ! ZkQueryLeadership
    expectMsgType[ZkLeadership](timeout) should be (leader)

    cluster.addShutdownListener((zkClient) => zkClient.delete.guaranteed.deletingChildrenIfNeeded.forPath("/"))
    watch(cluster.zkClusterActor)
    cluster.zkClusterActor ! PoisonPill
    expectMsgType[Terminated](timeout)
  }
}
