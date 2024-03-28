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

package org.squbs.actormonitor

import org.apache.pekko.actor._
import com.typesafe.scalalogging.LazyLogging
import org.squbs.lifecycle.ExtensionLifecycle


private class ActorMonitorInit extends ExtensionLifecycle with LazyLogging {

  override def postInit(): Unit = {
    logger.info(s"postInit ${this.getClass}")

    import boot._
    implicit val system = actorSystem

    val monitorConfig = config.getConfig("squbs-actormonitor")

    import monitorConfig._
    system.actorOf(Props(classOf[ActorMonitor],
      ActorMonitorConfig(getInt("maxActorCount"), getInt("maxChildrenDisplay"))), "squbs-actormonitor")
  }
}


