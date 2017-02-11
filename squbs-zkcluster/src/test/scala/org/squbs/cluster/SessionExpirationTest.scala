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

import org.apache.curator.test.KillSession
import org.squbs.cluster.test.{ZkClusterMultiActorSystemTestKit, ZkClusterTestHelper}

import scala.concurrent.duration._
import scala.language.postfixOps

class SessionExpirationTest extends ZkClusterMultiActorSystemTestKit("SessionExpirationTest")
  with ZkClusterTestHelper {

  override val timeout: FiniteDuration = 60 seconds
  override val clusterSize: Int = 1


  "ZkCluster" should "response to session expiration" in {
    val cluster = zkClusterExts.values.head

    cluster.zkClusterActor ! ZkQueryLeadership
    val leader = expectMsgType[ZkLeadership](timeout)

    cluster.zkClusterActor ! ZkMonitorClient
    KillSession.kill(cluster.curatorFwkWithNs().getZookeeperClient.getZooKeeper)
    expectMsgType[ZkClientUpdated](timeout).zkClient

    cluster.zkClusterActor ! ZkQueryLeadership
    expectMsgType[ZkLeadership](timeout) should be (leader)
  }
}
