package org.squbs.cluster

import akka.actor._
import akka.remote.QuarantinedEvent

import scala.util.Try

/**
 * Created by zhuwang on 2/8/15.
 */

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
  
  override def preStart = context.system.eventStream.subscribe(self, classOf[QuarantinedEvent])
  
  private[this] var quarantinedRemotes = Set.empty[Address]
  
  override def receive: Receive = {
    case QuarantinedEvent(remote, uid) => // first QuarantinedEvent arrived
      quarantinedRemotes += remote
      zkClusterActor ! ZkQueryMembership
      context become {
        case ZkMembership(members) => members -- quarantinedRemotes foreach {address =>
          context.actorSelection(self.path.toStringWithAddress(address)) ! Identify("ping")
        }
        case QuarantinedEvent(remote, uid) => quarantinedRemotes += remote
          if (quarantinedRemotes.size >= suicideThreshold) {
            log.error("[RemoteGuardian] cannot reach {} any more. Performing a suicide ... ", quarantinedRemotes)
            zkCluster.addShutdownListener(context.system.shutdown)
            zkClusterActor ! PoisonPill
          }
        case other => // don't care
      }
  }
}
