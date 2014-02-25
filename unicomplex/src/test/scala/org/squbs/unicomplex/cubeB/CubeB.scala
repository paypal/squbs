package org.squbs.unicomplex.cubeB

import org.squbs.lifecycle.{GracefulStop, GracefulStopHelper}
import scala.Some
import scala.util.Try
import org.squbs.unicomplex.Initialized
import akka.actor.{Actor, ActorLogging}
import org.squbs.unicomplex.Unicomplex.InitReport


/**
 * Created by zhuwang on 2/25/14.
 */
class InitCubeActorB extends Actor with ActorLogging with GracefulStopHelper  {

  // do initialization
  def init: InitReport = {
    log.info("initializing")
    Try {
      // do some tasks
      Some("InitCubeActorB")
    }
  }

  context.parent ! (Initialized(init))

  def receive = {
    case GracefulStop => defaultLeafActorStop

    case other => sender ! other
  }

}
