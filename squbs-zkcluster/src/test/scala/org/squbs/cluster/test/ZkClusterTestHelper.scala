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

package org.squbs.cluster.test

import org.apache.pekko.testkit.ImplicitSender
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.squbs.cluster._
import org.squbs.testkit.Timeouts._

trait ZkClusterTestHelper extends AnyFlatSpecLike with ImplicitSender with Matchers
  with BeforeAndAfterAll with BeforeAndAfterEach { me: ZkClusterMultiActorSystemTestKit =>

  override def beforeAll(): Unit = startCluster()

  override def afterAll(): Unit = shutdownCluster()

  override val timeout = awaitMax

  override val clusterSize: Int = 6

  override def afterEach(): Unit = {
    Thread.sleep(500)
  }

  protected def checkLeaderInSync(leader: ZkLeadership) = {
    zkClusterExts foreach {
      case (name, ext) => ext tell (ZkQueryLeadership, self)
        expectMsg(timeout, leader)
    }
  }

  protected def checkMembersInSync(expected: Set[String]) = {
    zkClusterExts foreach {
      case (name, ext) => ext tell (ZkQueryMembership, self)
        expectMsgType[ZkMembership](timeout).members map (_.system) should be (expected)
    }
  }

  protected def killLeader(leader: ZkLeadership) = {
    val toBeKilled = leader.address.system
    killSystem(toBeKilled)
    Thread.sleep(500)
    toBeKilled
  }

  protected def killFollower(leader: ZkLeadership) = {
    val leaderName = leader.address.system
    val toBeKilled = pickASystemRandomly(Some(leaderName))
    killSystem(toBeKilled)
    Thread.sleep(500)
    toBeKilled
  }

  protected def getLeader(name: String) = {
    zkClusterExts(name) tell (ZkQueryLeadership, self)
    expectMsgType[ZkLeadership](timeout)
  }

  protected val originalMembers = (0 until clusterSize).map(systemName).toSet
}
