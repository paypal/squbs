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

import akka.actor._
import akka.remote.QuarantinedEvent

import scala.util.Try

/**
 * The RemoteGuardian subscribe to QuarantinedEvent
 * If it cannot reach a number of remote systems, probably itself runs into problems
 * Then close the Zookeeper connection and terminate the entire system
 */
class RemoteGuardian extends Actor with ActorLogging {

  val zkCluster= ZkCluster(context.system)
  import zkCluster._

  val suicideThreshold = Try {
    context.system.settings.config.getInt("zkCluster.suicide-threshold")
  } getOrElse 3

  override def preStart() = context.system.eventStream.subscribe(self, classOf[QuarantinedEvent])

  private[this] var quarantinedRemotes = Set.empty[Address]

  override def receive: Receive = {
    case QuarantinedEvent(remote, uid) => // first QuarantinedEvent arrived
      log.error("[RemoteGuardian] get Quarantined event for remote {} uid {}", remote, uid)
      quarantinedRemotes += remote
      zkClusterActor ! ZkQueryMembership
      context become {
        case ZkMembership(members) => members -- quarantinedRemotes foreach {address =>
          context.actorSelection(self.path.toStringWithAddress(address)) ! Identify("ping")
        }
        case QuarantinedEvent(qRemote, _) => quarantinedRemotes += qRemote
          if (quarantinedRemotes.size >= suicideThreshold) {
            log.error("[RemoteGuardian] cannot reach {} any more. Performing a suicide ... ", quarantinedRemotes)
            zkCluster.addShutdownListener(context.system.shutdown)
            zkClusterActor ! PoisonPill
          }
        case other => // don't care
      }
  }
}
