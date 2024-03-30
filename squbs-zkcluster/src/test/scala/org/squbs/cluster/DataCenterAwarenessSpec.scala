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

import org.apache.pekko.actor._
import org.apache.pekko.routing.ActorSelectionRoutee
import org.apache.pekko.util.ByteString
import org.mockito.Mockito._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.squbs.cluster.rebalance.{CorrelateRoundRobinRoutingLogic, DataCenterAwareRebalanceLogic, DefaultCorrelation}

class DataCenterAwarenessSpec extends AnyFlatSpec with Matchers with MockitoSugar {


  val myAddress = Address("pekko.tcp", "pubsub", "10.100.194.253", 8080)
  val correlates = Seq(Address("pekko.tcp", "pubsub", "10.100.65.147", 8080),
    Address("pekko.tcp", "pubsub", "10.100.98.134", 8080))
  val distances = Seq(Address("pekko.tcp", "pubsub", "10.210.45.119", 8080),
    Address("pekko.tcp", "pubsub", "10.210.79.201", 8080))

  "DefaultCorrelation" should "extract ipv4 subnet domain" in {

    val mockAddress = Address("pekko.tcp", "pubsub", "10.100.194.253", 8080)

    DefaultCorrelation().common(mockAddress) should equal("pubsub@10.100")
  }

  "CorrelateRoundRobinRoutingLogic" should "prefer routees that correlate with itself" in {

    val routees = (correlates ++ distances).map(address => {
      val mockActorSelection = mock[ActorSelection]
      when(mockActorSelection.pathString).thenReturn(address.toString)

      ActorSelectionRoutee(mockActorSelection)
    }).toIndexedSeq

    val logic = CorrelateRoundRobinRoutingLogic(myAddress, DefaultCorrelation())
    logic.select("whatever", routees) match {
      case ActorSelectionRoutee(selection) =>
        selection.pathString should equal("pekko.tcp://pubsub@10.100.65.147:8080")
    }

    logic.select("whatever", routees) match {
      case ActorSelectionRoutee(selection) =>
        selection.pathString should equal("pekko.tcp://pubsub@10.100.98.134:8080")
    }

    logic.select("whatever", routees) match {
      case ActorSelectionRoutee(selection) =>
        selection.pathString should equal("pekko.tcp://pubsub@10.100.65.147:8080")
    }
  }

  "DefaultDataCenterAwareRebalanceLogic" should "rebalance with correlations in considerations" in {

    val partitionKey = ByteString("some partition")
    val partitionsToMembers = Map(partitionKey -> Set.empty[Address])
    def size(partitionKey:ByteString) = 2

    var compensation = DataCenterAwareRebalanceLogic(DefaultCorrelation())
      .compensate(partitionsToMembers, correlates ++ distances, size)
    compensation.getOrElse(partitionKey, Set.empty) should equal(Set(correlates.head, distances.head))

    val morePartition = ByteString("another partition")
    compensation = DataCenterAwareRebalanceLogic(DefaultCorrelation()).
      compensate(compensation.updated(morePartition, Set.empty), correlates ++ distances, size)
    compensation.getOrElse(partitionKey, Set.empty) should equal(Set(correlates.head, distances.head))
    compensation.getOrElse(morePartition, Set.empty) should equal(Set(correlates.head, distances.head))

    val balanced = DataCenterAwareRebalanceLogic(DefaultCorrelation())
      .rebalance(compensation, (correlates ++ distances).toSet)
    balanced.getOrElse(partitionKey, Set.empty) shouldNot equal(balanced.getOrElse(morePartition, Set.empty))
  }

  "DefaultDataCenterAwareRebalanceLogic" should "rebalance after a DC failure recovery" in {

    val partitionKey = ByteString("some partition")
    val partitionsToMembers = Map(partitionKey -> Set.empty[Address])
    def size(partitionKey:ByteString) = 2

    var compensation = DataCenterAwareRebalanceLogic(DefaultCorrelation())
      .compensate(partitionsToMembers, correlates ++ distances, size)
    compensation.getOrElse(partitionKey, Set.empty) should equal(Set(correlates.head, distances.head))

    val balanced = DataCenterAwareRebalanceLogic(DefaultCorrelation())
      .rebalance(compensation, (correlates ++ distances).toSet)
    balanced.getOrElse(partitionKey, Set.empty) should have size 2

    //unfortunately correlates are gone?!
    compensation = DataCenterAwareRebalanceLogic(DefaultCorrelation()).
      compensate(partitionsToMembers.updated(partitionKey, Set(distances.head)), distances, size)
    compensation.getOrElse(partitionKey, Set.empty) should equal(distances.toSet)

    val rebalanced = DataCenterAwareRebalanceLogic(DefaultCorrelation()).rebalance(compensation, distances.toSet)
    rebalanced.getOrElse(partitionKey, Set.empty) should equal(distances.toSet)

    val recovered = DataCenterAwareRebalanceLogic(DefaultCorrelation())
      .rebalance(compensation, (correlates ++ distances).toSet)
    recovered.getOrElse(partitionKey, Set.empty) should have size 2
    recovered.getOrElse(partitionKey, Set.empty) shouldNot equal(distances.toSet)
    correlates.contains(recovered.getOrElse(partitionKey, Set.empty).diff(distances.toSet).head) should equal(true)
  }
}
