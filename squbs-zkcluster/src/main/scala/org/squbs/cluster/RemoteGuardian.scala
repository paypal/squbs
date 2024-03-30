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
import org.apache.pekko.remote.QuarantinedEvent

/**
 * Created by zhuwang on 2/8/15.
 */

/**
 * The RemoteGuardian subscribe to QuarantinedEvent
 * If a QuarantinedEvent arrives, it will close the connection to Zookeeper and exit the JVM using code 99
 * External monitor tool like JSW can be configured to restart the app according to the exist code
 */
@deprecated("zkcluster is deprecated in lieu of maturity of Pekko cluster and more modern cluster coordinators",
  since = "0.15.0")
class RemoteGuardian extends Actor with ActorLogging {

  val zkCluster= ZkCluster(context.system)

  override def preStart(): Unit = context.system.eventStream.subscribe(self, classOf[QuarantinedEvent])

  val EXIT_CODE = 99

  override def receive: Receive = {
    case QuarantinedEvent(remote, uid) => // first QuarantinedEvent arrived
      log.error("[RemoteGuardian] get Quarantined event for remote {} uid {}. Performing a suicide ...", remote, uid)
      zkCluster.addShutdownListener((_) => context.system.terminate())
      zkCluster.addShutdownListener((_) => System.exit(EXIT_CODE))
      zkCluster.zkClusterActor ! PoisonPill
  }
}
