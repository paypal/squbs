/*
 * Copyright 2017 PayPal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.squbs.unicomplex.cubeA

import org.apache.pekko.actor.{Actor, ActorLogging, Props}
import org.squbs.lifecycle.{GracefulStop, GracefulStopHelper}
import org.squbs.unicomplex.Unicomplex.InitReport
import org.squbs.unicomplex.{Initialized, StopTimeout}

import scala.util.Try

class InitCubeActorA1 extends Actor with ActorLogging with GracefulStopHelper {

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

    case other => sender() ! other
  }

}

class InitCubeActorA2 extends Actor with ActorLogging with GracefulStopHelper {

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

    case other => sender() ! other
  }

}

class InitCubeActorA3 extends Actor with ActorLogging with GracefulStopHelper {

  import scala.concurrent.duration._

  // do initialization
  def init: InitReport = {
    log.info("initializing")
    Try {
      // do some tasks
      Some("InitCubeActorA3")
    }
  }

  val dummySender = context.actorOf(Props(new Actor {
    def receive: Actor.Receive = {
      case _ =>
    }
  }))


  override def preStart(): Unit = {
    context.parent tell(Initialized(init), dummySender)

    context.parent tell(GracefulStop, dummySender)

    context.parent tell(StopTimeout(3001.millisecond), dummySender)

    self ! "Boom"
  }

  override def postRestart(reason: Throwable): Unit = {

  }

  def receive = {
    case GracefulStop => defaultLeafActorStop

    case "Boom" => throw new RuntimeException("Boom")

    case other => sender() ! other
  }

}
