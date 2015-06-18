/*
 *  Copyright 2015 PayPal
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

package org.squbs.unicomplex.dummysvc

import org.squbs.unicomplex._
import akka.actor.{Props, ActorRef, Actor, ActorLogging}
import spray.routing._
import Directives._
import spray.http.HttpEntity
import spray.http.MediaTypes._
import spray.http.HttpResponse

class DummySvc extends RouteDefinition with WebContext {
  def route = path("msg" / Segment) {param =>
    get {ctx =>
      context.actorOf(Props[DummyClient]).tell(EchoMsg(param), ctx.responder)
    }
  }
}

class Dummy2VersionedSvc extends RouteDefinition with WebContext {
  def route = path("msg" / Segment) {param =>
    get {ctx =>
      context.actorOf(Props[DummyClient]).tell(EchoMsg(param), ctx.responder)
    }
  }
}

class Dummy2Svc extends RouteDefinition with WebContext {
  def route = path("msg" / Segment) {param =>
    get {ctx =>
      context.actorOf(Props[DummyClient]).tell(EchoMsg(param.reverse), ctx.responder)
    }
  }
}

private class DummyClient extends Actor with ActorLogging {

  private def receiveMsg(responder: ActorRef): Receive = {

    case AppendedMsg(appendedMsg) => context.actorSelection("/user/DummyCube/Prepender") ! EchoMsg(appendedMsg)

    case PrependedMsg(prependedMsg) => responder ! HttpResponse(entity = HttpEntity(`text/plain`, prependedMsg))
      context.stop(self)
  }

  def receive = {
    case msg: EchoMsg => context.actorSelection("/user/DummyCube/Appender") ! msg
      context.become(receiveMsg(sender()))
  }
}