package org.squbs.unicomplex.initfail

import org.squbs.lifecycle.{GracefulStop, GracefulStopHelper}
import org.squbs.unicomplex.Unicomplex._
import scala.util.Try
import org.squbs.unicomplex.Initialized
import akka.actor.{Actor, ActorLogging}

/**
 * Created by zhuwang on 2/25/14.
 */
class InitFailActor extends Actor with ActorLogging with GracefulStopHelper {

  // do initialization
  def init: InitReport = {
    log.info("initializing")
    Try {
      // do some tasks
      throw new Exception("Init failed")
    }
  }

  context.parent ! Initialized(init)

  def receive = {
    case GracefulStop => defaultLeafActorStop

    case other => sender ! other
  }

}
