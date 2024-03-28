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

package org.squbs.actorregistry


import org.apache.pekko.actor._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.squbs.lifecycle.ExtensionLifecycle
import org.squbs.util.ConfigUtil
import org.squbs.unicomplex.UnicomplexBoot.StartupType

import scala.concurrent.duration._


private class ActorRegistryInit extends ExtensionLifecycle with LazyLogging {

  override def postInit(): Unit = {
    logger.info(s"postInit ${this.getClass}")

    import ConfigUtil._
    import boot._

    val registryConfig = config.getConfig("squbs-actorregistry")

    val cubeActorList = cubes.filterNot(x=> x.info.name == "ActorRegistryCube" || x.info.name == "RemoteCube").flatMap {
      cube =>
        cube.components.getOrElse(StartupType.ACTORS, Seq.empty).map {
          config =>
            val className = config getString "class-name"
            val actorName = config.get[String]("name", className.substring(className.lastIndexOf('.') + 1))
            val messageTypeList = config.get[Seq[Config]]("message-class", Seq.empty[Config]).map { x =>
              CubeActorMessageType(x.getOption[String]("request"), x.getOption[String]("response"))
            }

            val path = s"/user/${cube.info.name}/$actorName"

            CubeActorInfo(path, messageTypeList)
        }
    }.toList

    val t = registryConfig.getInt("timeout")
    implicit val system = boot.actorSystem
    system.actorOf(Props(classOf[HelperActor],
              system.actorSelection(ActorRegistry.path),
              StartActorRegister(cubeActorList, t),
              FiniteDuration(t, MILLISECONDS)))
  }
}

private class HelperActor(sel: ActorSelection, msg: Any, duration: FiniteDuration) extends Actor {
  import context.dispatcher
  sel ! Identify("try")

  context.setReceiveTimeout(duration)
  def receive = {
    case ActorIdentity("try", Some(actor)) =>
      actor ! msg
      context.stop(self)
    case ReceiveTimeout =>
      context.setReceiveTimeout(Duration.Undefined)
      context.system.scheduler.scheduleOnce(duration) {
        sel ! Identify("try")
      }
  }
}
