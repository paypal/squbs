package org.squbs.cluster

import akka.actor._
import akka.remote.QuarantinedEvent

/**
 * Created by zhuwang on 2/8/15.
 */

/**
 * The RemoteGuardian subscribe to QuarantinedEvent
 * If a QuarantinedEvent arrives, it will close the connection to Zookeeper and exit the JVM using code 99
 * External monitor tool like JSW can be configured to restart the app according to the exist code
 */
class RemoteGuardian extends Actor with ActorLogging {
  
  val zkCluster= ZkCluster(context.system)
  
  override def preStart = context.system.eventStream.subscribe(self, classOf[QuarantinedEvent])

  override def receive: Receive = {
    case QuarantinedEvent(remote, uid) => // first QuarantinedEvent arrived
      log.error("[RemoteGuardian] get Quarantined event for remote {} uid {}. Performing a suicide ...", remote, uid)
      zkCluster.addShutdownListener(() => System.exit(99))
      zkCluster.addShutdownListener(() => context.system.shutdown)
      zkCluster.zkClusterActor ! PoisonPill
  }
}