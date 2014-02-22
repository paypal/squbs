package org.squbs.util.conversion

import akka.actor.{ActorRef, ActorSelection, ActorSystem}

/**
 * Created by zhuwang on 2/20/14.
 */

case class CubeName(name: String)

object CubeUtil {

  /**
   * get the cube supervisor for a given ActorRef
   * @return Option[ActorSelection] of the CubeSupervisor
   */
  implicit class ActorRefConversion(val target: ActorRef) {

    def cubeSupervisor()(implicit system: ActorSystem): Option[ActorSelection] = {
      val targetPath = target.path
      val pathElements = targetPath.elements.toSeq

      if (pathElements.size <= 2) {
        None
      }else {
        val supervisorPath = targetPath.root / pathElements(0) / pathElements(1)
        Some(system.actorSelection(supervisorPath))
      }
    }
  }

  /**
   * convert a CubeName to ActorSelection
   */
  implicit class CubeNameConversion(val cubeName: CubeName) {

    def cubeSupervisor()(implicit system: ActorSystem): ActorSelection = {

      system.actorSelection(system / cubeName.name.substring(cubeName.name.lastIndexOf(".") + 1))
    }
  }

}
