package org.squbs.unicomplex.initblock

import org.squbs.unicomplex.Unicomplex._
import scala.util.Try
import org.squbs.lifecycle.{GracefulStopHelper, GracefulStop}
import akka.actor.{Actor, ActorLogging}

/**
 * Created by zhuwang on 2/25/14.
 */
class InitBlockActor extends Actor with ActorLogging with GracefulStopHelper{

  // do initialization
  def init: InitReport = {
    log.info("initializing")
    Try {
      // do some tasks
      throw new Exception("Init blocked")
    }
  }

  // never send the the report
  //context.parent ! Initialized(init)

  def receive = {
    case GracefulStop => defaultLeafActorStop

    case other => sender ! other
  }

}
