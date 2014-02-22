package org.squbs.unicomplex.dummycubesvc

import org.squbs.unicomplex.{Ping, Pong, RouteDefinition}
import spray.routing.Directives._
import akka.actor.{ActorRef, Actor, ActorLogging, Props}
import org.squbs.lifecycle.{GracefulStop, GracefulStopHelper}
import spray.http.{HttpEntity, HttpResponse}
import spray.http.MediaTypes._

/**
 * Created by zhuwang on 2/21/14.
 */
class PingPongSvc extends RouteDefinition{

  val webContext = "pingpongsvc"

  def route = path("ping") {
    get {ctx =>
      context.actorOf(Props[PingPongClient]).tell("ping", ctx.responder)
    }
  } ~
  path("pong") {
    get {ctx =>
      context.actorOf(Props[PingPongClient]).tell("pong", ctx.responder)
    }
  }

}

private class PingPongClient extends Actor with ActorLogging {

  private val pingPongActor = context.actorSelection("/user/DummyCubeSvc/PingPongPlayer")

  def ping(sender: ActorRef): Receive = {
    case Pong => sender ! HttpResponse(entity = HttpEntity(`text/plain`, Pong.toString))

    case other => log.warning(s"received $other")
  }

  def pong(sender: ActorRef): Receive = {
    case Ping => sender ! HttpResponse(entity = HttpEntity(`text/plain`, Ping.toString))

    case other => log.warning(s"received $other")
  }

  def receive = {
    case "ping" => pingPongActor ! Ping
      context.become(ping(sender))

    case "pong" => pingPongActor ! Pong
      context.become(pong(sender))

    case other => log.warning(s"received $other")
  }

}

class PingPongActor extends Actor with ActorLogging with GracefulStopHelper{

  def receive = {
    case GracefulStop => defaultLeafActorStop

    case Ping => sender ! Pong

    case Pong => sender ! Ping

    case other => log.warning(s"received $other")
  }
}