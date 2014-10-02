/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the AUTHORS file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.squbs.actormonitor

import javax.management.ObjectName
import akka.actor._
import org.squbs.unicomplex.Initialized
import org.squbs.unicomplex.JMX._
import scala.util.Success
import collection.JavaConversions._
import ActorMonitorBean._

private[actormonitor] case class StartActorMonitor(cubeNameList: List[String], monitorConfig: ActorMonitorConfig)
private[actormonitor] case class ActorMonitorConfig(timeout: Int, maxActorCount: Int, maxChildrenDisplay: Int)


private[actormonitor] class ActorMonitor extends Actor  {

  implicit var count = 0

  val configBean =  "org.squbs.unicomplex:type=ActorMonitor"

  override def postStop() {
    unregister(prefix + configBean)
    totalBeans.foreach {unregister(_)}
  }

  def receive = {
    case StartActorMonitor(topLevelActorList, monitorConfig) =>
      register(new ActorMonitorConfigBean(monitorConfig, context), prefix + configBean )

      count = topLevelActorList.size
      topLevelActorList.foreach { name =>
        context.actorSelection(s"/user/$name") ! Identify(monitorConfig)
      }

    case  ActorIdentity(monitorConfig: ActorMonitorConfig , Some(actor))=>
      implicit val config = monitorConfig
      process(actor)
      count -= 1
      if (count <= 0)
        context.parent ! Initialized(Success(None))

    case Terminated(actor) =>
      unregisterBean(actor)
  }

  def process(actor: ActorRef) (implicit monitorConfig: ActorMonitorConfig , context: ActorContext) : Unit= {
      context.watch(actor)
      registerBean(actor)
      getDescendant(actor).foreach(process(_))
  }

}







