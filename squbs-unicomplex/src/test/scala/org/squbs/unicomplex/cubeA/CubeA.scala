/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the CONTRIBUTING file distributed with this work for
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
package org.squbs.unicomplex.cubeA

import org.squbs.lifecycle.{GracefulStop, GracefulStopHelper}
import scala.util.Try
import org.squbs.unicomplex.Initialized
import org.squbs.unicomplex.Unicomplex.InitReport
import akka.actor.{Actor, ActorLogging}

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