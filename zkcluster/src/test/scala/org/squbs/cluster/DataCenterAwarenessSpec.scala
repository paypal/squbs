package org.squbs.cluster

import org.scalatest.{Matchers, FlatSpec}
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import akka.actor._
import akka.routing.ActorSelectionRoutee
import akka.util.ByteString

/**
 * Created by huzhou on 5/8/14.
 */
class DataCenterAwarenessSpec extends FlatSpec with Matchers with MockitoSugar {


  val myAddress = Address("akka.tcp", "pubsub", "10.100.194.253", 8080)
  val correlates = Seq(Address("akka.tcp", "pubsub", "10.100.65.147", 8080),
                       Address("akka.tcp", "pubsub", "10.100.98.134", 8080))
  val distances  = Seq(Address("akka.tcp", "pubsub", "10.210.45.119", 8080),
                       Address("akka.tcp", "pubsub", "10.210.79.201", 8080))

  "DefaultCorrelation" should "extract ipv4 subnet domain" in {

    val mockAddress = Address("akka.tcp", "pubsub", "10.100.194.253", 8080)

    DefaultCorrelation.common(mockAddress) should equal("10.100")
  }

  "CorrelateRoundRobinRoutingLogic" should "prefer routees that correlate with itself" in {

    val routees = (correlates ++ distances).map(address => {
      val mockActorSelection = mock[ActorSelection]
      when(mockActorSelection.pathString).thenReturn(address.toString)

      ActorSelectionRoutee(mockActorSelection)
    }).toIndexedSeq

    val logic = CorrelateRoundRobinRoutingLogic(myAddress)
    logic.select("whatever", routees) match {
      case ActorSelectionRoutee(selection) =>
        selection.pathString should equal("akka.tcp://pubsub@10.100.65.147:8080")
    }

    logic.select("whatever", routees) match {
      case ActorSelectionRoutee(selection) =>
        selection.pathString should equal("akka.tcp://pubsub@10.100.98.134:8080")
    }

    logic.select("whatever", routees) match {
      case ActorSelectionRoutee(selection) =>
        selection.pathString should equal("akka.tcp://pubsub@10.100.65.147:8080")
    }
  }

  "DefaultDataCenterAwareRebalanceLogic" should "rebalance with correlations in considerations" in {

    val partitionKey = ByteString("some partition")
    val partitionsToMembers = Map(partitionKey -> Set.empty[Address])
    def size(partitionKey:ByteString) = 2

    var compensation = DefaultDataCenterAwareRebalanceLogic.compensate(partitionsToMembers, correlates ++ distances, size _)
    compensation.getOrElse(partitionKey, Set.empty) should equal(Set(correlates.head, distances.head))

    val morePartition = ByteString("another partition")
    compensation = DefaultDataCenterAwareRebalanceLogic.compensate(compensation.updated(morePartition, Set.empty), correlates ++ distances, size _)
    compensation.getOrElse(partitionKey, Set.empty) should equal(Set(correlates.head, distances.head))
    compensation.getOrElse(morePartition, Set.empty) should equal(Set(correlates.head, distances.head))

    val balanced = DefaultDataCenterAwareRebalanceLogic.rebalance(compensation, (correlates ++ distances).toSet)
    balanced.getOrElse(partitionKey, Set.empty) shouldNot equal(balanced.getOrElse(morePartition, Set.empty))
  }

  "DefaultDataCenterAwareRebalanceLogic" should "rebalance after a DC failure recovery" in {

    val partitionKey = ByteString("some partition")
    val partitionsToMembers = Map(partitionKey -> Set.empty[Address])
    def size(partitionKey:ByteString) = 2

    var compensation = DefaultDataCenterAwareRebalanceLogic.compensate(partitionsToMembers, correlates ++ distances, size _)
    compensation.getOrElse(partitionKey, Set.empty) should equal(Set(correlates.head, distances.head))

    val balanced = DefaultDataCenterAwareRebalanceLogic.rebalance(compensation, (correlates ++ distances).toSet)
    balanced.getOrElse(partitionKey, Set.empty).size should equal(2)

    //unfortunately correlates are gone?!
    compensation = DefaultDataCenterAwareRebalanceLogic.compensate(partitionsToMembers.updated(partitionKey, Set(distances.head)), distances, size _)
    compensation.getOrElse(partitionKey, Set.empty) should equal(distances.toSet)

    val rebalanced = DefaultDataCenterAwareRebalanceLogic.rebalance(compensation, distances.toSet)
    rebalanced.getOrElse(partitionKey, Set.empty) should equal(distances.toSet)

    val recovered = DefaultDataCenterAwareRebalanceLogic.rebalance(compensation, (correlates ++ distances).toSet)
    recovered.getOrElse(partitionKey, Set.empty).size should equal(2)
    recovered.getOrElse(partitionKey, Set.empty) shouldNot equal(distances.toSet)
    correlates.contains(recovered.getOrElse(partitionKey, Set.empty).diff(distances.toSet).head) should equal(true)
  }
}
