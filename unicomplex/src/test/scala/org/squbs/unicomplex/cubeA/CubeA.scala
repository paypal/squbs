package org.squbs.unicomplex.cubeA

import org.squbs.lifecycle.{GracefulStop, GracefulStopHelper}
import scala.util.Try
import org.squbs.unicomplex.Initialized
import org.squbs.unicomplex.Unicomplex.InitReport
import akka.actor.{Actor, ActorLogging}

/**
 * Created by zhuwang on 2/25/14.
 */
class InitCubeActorA1 extends Actor with ActorLogging with GracefulStopHelper{

  // do initialization
  def init: InitReport = {
    log.info("initializing")
    Try {
      // do some tasks
      Some("InitCubeActorA1")
    }
  }

  context.parent ! Initialized(init)

  def receive = {
    case GracefulStop => defaultLeafActorStop

    case other => sender ! other
  }

}

class InitCubeActorA2 extends Actor with ActorLogging with GracefulStopHelper{

  // do initialization
  def init: InitReport = {
    log.info("initializing")
    Try {
      // do some tasks
      Some("InitCubeActorA2")
    }
  }

  context.parent ! Initialized(init)

  def receive = {
    case GracefulStop => defaultLeafActorStop

    case other => sender ! other
  }

}