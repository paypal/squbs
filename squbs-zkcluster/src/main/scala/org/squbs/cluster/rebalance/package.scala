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

import org.apache.pekko.actor.Address
import org.apache.pekko.util.ByteString

import scala.annotation.tailrec

package object rebalance {
  trait RebalanceLogic {
    val spareLeader:Boolean
    /**
     * @return partitionsToMembers compensated when size in service is short compared with what's required
     */
    def compensate(partitionsToMembers: Map[ByteString, Set[Address]], members:Seq[Address], size:(ByteString => Int)):
    Map[ByteString, Set[Address]] = {
      partitionsToMembers.map(assign => {
        val partitionKey = assign._1
        val servants = assign._2.filter(members.contains(_))
        val partitionSize = size(partitionKey)
        servants.size match {
          case sz if sz < partitionSize => //shortage, must be compensated
            partitionKey -> (servants ++ members.filterNot(servants.contains).take(partitionSize - servants.size))
          case sz if sz > partitionSize => //overflow, reduce the servants
            partitionKey -> servants.take(partitionSize)
          case _ =>
            assign
        }
      })
    }

    /**
     * @return partitionsToMembers rebalanced
     */
    def rebalance(partitionsToMembers: Map[ByteString, Set[Address]], members:Set[Address]):
    Map[ByteString, Set[Address]] = {
      val utilization = partitionsToMembers.foldLeft(Map.empty[Address, Seq[ByteString]]){(memoize, assign) =>
        assign._2.foldLeft(memoize){(memoize, member) =>
          memoize.updated(member, memoize.getOrElse(member, Seq.empty) :+ assign._1)
        }
      }
      val ordered = members.toSeq sortWith { (one, two) =>
        utilization.getOrElse(one, Seq.empty).size < utilization.getOrElse(two, Seq.empty).size
      }
      @tailrec def rebalanceRecursively(partitionsToMembers: Map[ByteString, Set[Address]],
                                        utilization: Map[Address, Seq[ByteString]],
                                        ordered:Seq[Address]): Map[ByteString, Set[Address]] = {
        val overflows = utilization.getOrElse(ordered.last, Seq.empty)
        val underflow = utilization.getOrElse(ordered.head, Seq.empty)
        if (overflows.size - underflow.size > 1) {
          val move = overflows.head
          val updatedUtil = utilization.updated(ordered.last, overflows.tail).updated(ordered.head, underflow :+ move)
          var headOrdered = ordered.tail takeWhile { next =>
            updatedUtil.getOrElse(ordered.head, Seq.empty).size < updatedUtil.getOrElse(next, Seq.empty).size
          }
          headOrdered = (headOrdered :+ ordered.head) ++ ordered.tail.drop(headOrdered.size)
          var rearOrdered = headOrdered takeWhile { next =>
            updatedUtil.getOrElse(headOrdered.last, Seq.empty).size > updatedUtil.getOrElse(next, Seq.empty).size
          }
          // Drop the headOrdered.last
          rearOrdered = (rearOrdered :+ headOrdered.last) ++ headOrdered.drop(rearOrdered.size).dropRight(1)
          rebalanceRecursively(partitionsToMembers.updated(move,
            partitionsToMembers.getOrElse(move, Set.empty) + ordered.head - ordered.last), updatedUtil, rearOrdered)
        }
        else
          partitionsToMembers
      }
      rebalanceRecursively(partitionsToMembers, utilization, ordered)
    }
  }

  class DefaultRebalanceLogic(val spareLeader: Boolean) extends RebalanceLogic

  object DefaultRebalanceLogic {
    def apply(spareLeader: Boolean) = new DefaultRebalanceLogic(spareLeader)
  }
}
