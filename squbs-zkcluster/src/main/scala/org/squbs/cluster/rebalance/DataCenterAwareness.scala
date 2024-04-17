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

package org.squbs.cluster.rebalance

import org.apache.pekko.actor.{ActorSystem, Address, AddressFromURIString}
import org.apache.pekko.dispatch.Dispatchers
import org.apache.pekko.routing.{ActorSelectionRoutee, _}
import org.apache.pekko.util.ByteString
import com.typesafe.scalalogging.LazyLogging

import scala.annotation.tailrec
import scala.collection.immutable

@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
trait Correlation[C] {

  def common(address:Address):C
}

@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
class DefaultCorrelation extends Correlation[String] {
  //for 10.100.254.73 ipv4, we use "10.100" as the common identifier for correlation
  override def common(address:Address) = address.hostPort.split('.').take(2).mkString(".")
}

@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
object DefaultCorrelation {

  def apply() = new DefaultCorrelation

}

@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
class CorrelateRoundRobinRoutingLogic[C](zkAddress:Address, correlation:Correlation[C])
  extends RoutingLogic with LazyLogging {

  val fallback = RoundRobinRoutingLogic()

  override def select(message: Any, routees: immutable.IndexedSeq[Routee]): Routee = {

    val candidates = routees.filter{
      case ActorSelectionRoutee(selection) if selection.anchorPath != null =>
        correlation.common(zkAddress) == correlation.common(selection.anchorPath.address)
      case ActorSelectionRoutee(selection) if !selection.pathString.startsWith("/")=>
        correlation.common(zkAddress) == correlation.common(AddressFromURIString(selection.pathString))
      case _ =>
        true
    }

    val selected = fallback.select(message, if(candidates.nonEmpty) candidates else routees)
    logger.info("[correlate rr] selects {} out of {} among {}", selected, candidates, routees)
    selected
  }
}

@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
object CorrelateRoundRobinRoutingLogic {

  def apply[C](zkAddress:Address, correlation:Correlation[C]) =
    new CorrelateRoundRobinRoutingLogic[C](zkAddress, correlation)

}

@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
final case class CorrelateRoundRobinGroup[C](val routerPaths: (ActorSystem) => immutable.Iterable[String],
                                             override val routerDispatcher: String = Dispatchers.DefaultDispatcherId,
                                             zkAddress:Address,
                                             correlation:Correlation[C]) extends Group {

  override def createRouter(system: ActorSystem): Router =
    new Router(CorrelateRoundRobinRoutingLogic(zkAddress, correlation))

  /**
   * Setting the dispatcher to be used for the router head actor, which handles
   * router management messages
   */
  def withDispatcher(dispatcherId: String): CorrelateRoundRobinGroup[C] = copy(routerDispatcher = dispatcherId)

  override def paths(system: ActorSystem): immutable.Iterable[String] = routerPaths(system)
}

@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
class DataCenterAwareRebalanceLogic[C](correlation:Correlation[C], val spareLeader:Boolean) extends RebalanceLogic {

  @tailrec private[cluster] final def classify(members:Seq[Address], classified:Map[C, Seq[Address]]):
      Map[C, Seq[Address]] =
    if(members.isEmpty)
      classified
    else
      classify(members.tail, classified.updated(correlation.common(members.head),
        classified.getOrElse(correlation.common(members.head), Seq.empty) :+ members.head))

  @tailrec private[cluster] final def rotate(classified:Seq[Seq[Address]], sequence:Seq[Address]):Seq[Address] =
    classified match {
      case Nil => sequence
      case _ => classified.head match {
        case Nil => rotate(classified.tail, sequence)
        case row:Seq[Address] =>
          rotate(classified.tail :+ row.tail/* a rotation of front(remaining) to the rear */, sequence :+ row.head)
      }
    }

  def shuffle(members:Seq[Address]):Seq[Address] =
    rotate(classify(members, Map.empty[C, Seq[Address]]).values.toSeq, Seq.empty[Address])

  /**
   * @return partitionsToMembers compensated when size in service is short compared with what's required
   */
  override def compensate(partitionsToMembers:Map[ByteString, Set[Address]], members:Seq[Address],
                          size: ByteString => Int): Map[ByteString, Set[Address]] =
    DefaultRebalanceLogic(spareLeader).compensate(partitionsToMembers, shuffle(members), size)

  /**
   * @return partitionsToMembers rebalanced
   */
  override def rebalance(partitionsToMembers:Map[ByteString, Set[Address]], members:Set[Address]):
      Map[ByteString, Set[Address]] = {

    //the classified members of all, which is a correlation to members mappings
    val classified = classify(members.toSeq, Map.empty[C, Seq[Address]])

    def rebalanceAcrossCorrelates(partitionsToMembers:Map[ByteString, Set[Address]]):Map[ByteString, Set[Address]] = {

      @tailrec def relocate(relocations:Map[C, Seq[Address]]):Map[C, Seq[Address]] = {

        relocations.toSeq.sortBy(_._2.size) match {
          //when the most loaded correlations have more than one assignment against the least loaded correlation
          case ordered:Seq[(C,Seq[Address])] if ordered.size > 1 && ordered.last._2.size - ordered.head._2.size > 1 =>
            classified.getOrElse(ordered.head._1, Seq.empty).filterNot(ordered.head._2.contains(_)) match {
              case available: Seq[Address] if available.nonEmpty =>
                relocate(relocations
                  .updated(ordered.last._1, ordered.last._2.tail)
                  .updated(ordered.head._1, ordered.head._2 :+ available.head))
              case _ =>
                relocations
            }
          case _ => relocations
        }
      }

      def compensate(beforehand:Map[C, Seq[Address]]):Map[C, Seq[Address]] =
        classified.keySet.diff(beforehand.keySet).foldLeft(beforehand){(memoize, correlation) =>
          memoize.updated(correlation, Seq.empty[Address])
        }

      // for each partition, we classify its current assigned correlates, check against the entire classified and
      // fill zero sized correlates if any then start the relocation across correlates by shift assignment from
      // the most loaded correlation to the least loaded one
      partitionsToMembers map { assign =>
        assign._1 -> relocate(compensate(classify(assign._2.toSeq, Map.empty[C, Seq[Address]]))).flatMap(_._2).toSet
      }
    }

    classified.values.foldLeft(rebalanceAcrossCorrelates(partitionsToMembers)){(memoize, correlates) =>
      DefaultRebalanceLogic(spareLeader).rebalance(memoize, correlates.toSet)
    }
  }
}

@deprecated("The zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
object DataCenterAwareRebalanceLogic {

  def apply[C](correlation:Correlation[C], spareLeader:Boolean = false) =
    new DataCenterAwareRebalanceLogic(correlation, spareLeader)
}
