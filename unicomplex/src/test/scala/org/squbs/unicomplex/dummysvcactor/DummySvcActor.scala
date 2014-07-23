package org.squbs.unicomplex.dummysvcactor

import akka.actor.{ActorLogging, Actor}
import spray.http.StatusCodes._
import spray.http.{HttpResponse, HttpRequest}

class DummySvcActor extends Actor with ActorLogging {

  def receive = {
    case req: HttpRequest if req.uri.path.toString == "/dummysvcactor/ping" =>
      log.debug("Received request " + req.uri)
      sender() ! HttpResponse(OK, "pong")
  }
}
