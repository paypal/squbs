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

package org.squbs.unicomplex.dummysvc

import org.apache.pekko.actor.{Actor, ActorLogging, ActorRef, Props}
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.pattern.ask
import org.squbs.unicomplex.Timeouts._
import org.squbs.unicomplex._

class DummySvc extends RouteDefinition with WebContext {
  def route: Route =
    get {
      path("msg" / Segment) { param =>
        onSuccess((context.actorOf(Props(classOf[DummyClient])) ? EchoMsg(param)).mapTo[String]) {
          value => complete(value)
        }
      } ~
      path("who") {
        extractClientIP { ip =>
          complete(ip.toString)
        }
      }
    }
}

class Dummy2VersionedSvc extends RouteDefinition with WebContext {
  def route: Route = path("msg" / Segment) {param =>
    get {
      onSuccess((context.actorOf(Props(classOf[DummyClient])) ? EchoMsg(param)).mapTo[String]) {
        case value => complete(value)
      }
    }
  }
}

class Dummy2Svc extends RouteDefinition with WebContext {
  def route: Route = path("msg" / Segment) {param =>
    get {
      onSuccess((context.actorOf(Props(classOf[DummyClient])) ? EchoMsg(param.reverse)).mapTo[String]) {
        case value => complete(value)
      }
    }
  }
}

private class DummyClient extends Actor with ActorLogging {

  private def receiveMsg(responder: ActorRef): Receive = {

    case AppendedMsg(appendedMsg) => context.actorSelection("/user/DummyCube/Prepender") ! EchoMsg(appendedMsg)

    case PrependedMsg(prependedMsg) =>
      responder ! prependedMsg
      context.stop(self)
  }

  def receive: Receive = {
    case msg: EchoMsg =>
      context.actorSelection("/user/DummyCube/Appender") ! msg
      context.become(receiveMsg(sender()))
  }
}
