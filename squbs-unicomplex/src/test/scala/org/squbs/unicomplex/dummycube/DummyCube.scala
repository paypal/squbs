/*
 * Copyright 2015 PayPal
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

package org.squbs.unicomplex.dummycube

import akka.actor.{Props, ActorLogging, Actor}
import org.squbs.unicomplex.{PrependedMsg, Constants, AppendedMsg, EchoMsg}
import org.squbs.lifecycle.{GracefulStop, GracefulStopHelper}

class AppendActor extends Actor with ActorLogging with GracefulStopHelper {

  def receive = {
    case EchoMsg(msg) => sender ! AppendedMsg(msg + Constants.SUFFIX)

    case GracefulStop => defaultLeafActorStop
  }
}

class DummyPrependActor extends Actor with ActorLogging with GracefulStopHelper {

  def receive = {

    case echoMsg @ EchoMsg(msg) => context.actorOf(Props[ActualPrependActor]) forward echoMsg

    case GracefulStop => defaultMidActorStop(context.children)
  }
}

private class ActualPrependActor extends Actor with ActorLogging with GracefulStopHelper {

  def receive = {

    case EchoMsg(msg) => sender ! PrependedMsg(Constants.PREFIX + msg)

    case GracefulStop => defaultLeafActorStop
  }
}
