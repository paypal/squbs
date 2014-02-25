package org.squbs.unicomplex.dummysvc

import org.squbs.unicomplex.RouteDefinition
import akka.actor.{Props, ActorRef, Actor, ActorLogging}
import spray.routing._
import Directives._
import spray.http.HttpEntity
import spray.http.MediaTypes._
import spray.http.HttpResponse
import org.squbs.unicomplex.AppendedMsg
import org.squbs.unicomplex.PrependedMsg
import org.squbs.unicomplex.EchoMsg

/**
 * Created by zhuwang on 2/21/14.
 */
class DummySvc extends RouteDefinition{
  val webContext = "dummysvc"
  def route = path("msg" / Segment) {param =>
    get {ctx =>
      context.actorOf(Props[DummyClient]).tell(EchoMsg(param), ctx.responder)
    }
  }
}

private class DummyClient extends Actor with ActorLogging {

  private def receiveMsg(sender: ActorRef): Receive = {

    case AppendedMsg(appendedMsg) => context.actorSelection("/user/DummyCube/Prepender") ! EchoMsg(appendedMsg)

    case PrependedMsg(prependedMsg) => sender ! HttpResponse(entity = HttpEntity(`text/plain`, prependedMsg))
      context.stop(self)
  }

  def receive = {
    case msg: EchoMsg => context.actorSelection("/user/DummyCube/Appender") ! msg
      context.become(receiveMsg(sender))

    case other => log.warning(s"received $other")
  }
}