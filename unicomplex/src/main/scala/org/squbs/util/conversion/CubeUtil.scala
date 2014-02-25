package org.squbs.util.conversion

import akka.actor.{ActorRef, ActorSelection, ActorSystem}
import scala.concurrent.Future
import akka.util.Timeout
import scala.concurrent.duration._

/**
 * Created by zhuwang on 2/20/14.
 */

case class CubeSupervisorNotFound(selection: ActorSelection) extends RuntimeException("Cubesupervisor not found for: " + selection)

object CubeUtil {
  /**
   * convert a CubeName to ActorSelection
   */
  implicit class CubeNameConversion(val cubeAlias: String) {
    implicit val timeout: Timeout = 10 millis

    def cubeSupervisor()(implicit system: ActorSystem): Future[ActorRef] = {
      if (cubeAlias == null || cubeAlias.length == 0) {
        Future failed CubeSupervisorNotFound(system.actorSelection(system / cubeAlias))
      }else {
        system.actorSelection(system / cubeAlias).resolveOne()
      }
    }
  }

}
