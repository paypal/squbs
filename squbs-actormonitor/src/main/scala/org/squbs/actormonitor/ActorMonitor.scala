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
import org.squbs.actormonitor.ActorMonitorBean._
import org.squbs.lifecycle.GracefulStopHelper
import org.squbs.unicomplex.JMX._

import scala.jdk.CollectionConverters._

private[actormonitor] case class ActorMonitorConfig(maxActorCount: Int, maxChildrenDisplay: Int)


private[actormonitor] class ActorMonitor(_monitorConfig: ActorMonitorConfig) extends Actor with GracefulStopHelper {

  val configBean =  "org.squbs.unicomplex:type=ActorMonitor"
  val monitorConfig = _monitorConfig

  register(new ActorMonitorConfigBean(monitorConfig, self, context), prefix + configBean )
  context.actorSelection("/*") ! Identify(monitorConfig)

  override def postStop(): Unit = {
    unregister(prefix + configBean)
    totalBeans.asScala.foreach(unregister)
  }

  def receive = {
    case "refresh" =>
      totalBeans.asScala.foreach(unregister)
      context.actorSelection("/*") ! Identify(monitorConfig)
    case ActorIdentity(monitorConfig: ActorMonitorConfig , Some(actor))=>
      implicit val config = monitorConfig
      process(actor)

    case Terminated(actor) =>
      unregisterBean(actor)
  }

  def process(actor: ActorRef) (implicit monitorConfig: ActorMonitorConfig , context: ActorContext) : Unit= {
      context.watch(actor)
      registerBean(actor)
      getDescendant(actor) foreach process
  }
}







