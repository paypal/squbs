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

package org.squbs.unicomplex.dummycubesvc

import org.apache.pekko.actor.{Actor, ActorLogging, ActorRef, Props}
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.pattern.ask
import org.squbs.lifecycle.{GracefulStop, GracefulStopHelper}
import org.squbs.unicomplex.Timeouts._
import org.squbs.unicomplex.{Ping, Pong, RouteDefinition}

class PingPongSvc extends RouteDefinition{

  def route: Route = path("ping") {
    get {
      onSuccess((context.actorOf(Props(classOf[PingPongClient])) ? "ping").mapTo[String]) {
        case value => complete(value)
      }
    }
  } ~
  path("pong") {
    get {
      onSuccess((context.actorOf(Props(classOf[PingPongClient])) ? "pong").mapTo[String]) {
        case value => complete(value)
      }
    }
  }

}

private class PingPongClient extends Actor with ActorLogging {

  private val pingPongActor = context.actorSelection("/user/DummyCubeSvc/PingPongPlayer")

  def ping(responder: ActorRef): Receive = {
    case Pong => responder ! Pong.toString
  }

  def pong(responder: ActorRef): Receive = {
    case Ping => responder ! Ping.toString
  }

  def receive: Receive = {
    case "ping" => pingPongActor ! Ping
      context.become(ping(sender()))

    case "pong" => pingPongActor ! Pong
      context.become(pong(sender()))
  }

}

class PingPongActor extends Actor with ActorLogging with GracefulStopHelper{

  def receive = {
    case GracefulStop => defaultLeafActorStop

    case Ping => sender() ! Pong

    case Pong => sender() ! Ping
  }
}
