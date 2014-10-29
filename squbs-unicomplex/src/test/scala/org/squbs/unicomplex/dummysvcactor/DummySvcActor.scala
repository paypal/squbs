package org.squbs.unicomplex.dummysvcactor

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import spray.can.Http.RegisterChunkHandler
import spray.http.StatusCodes._
import spray.http._

case object RegisterTimeoutHandler

class DummySvcActor extends Actor with ActorLogging {

  var timeoutListeners = Seq.empty[ActorRef]

  def receive = {
    case req: HttpRequest if req.uri.path.toString == "/dummysvcactor/ping" =>
      log.debug("Received request " + req.uri)
      sender() ! HttpResponse(OK, "pong")

    case ChunkedRequestStart(req) if req.uri.path.toString == "/dummysvcactor/chunks" =>
      val handler = context.actorOf(Props[ChunkedRequestHandler])
      sender() ! RegisterChunkHandler(handler)
      handler forward req

    case req: HttpRequest if req.uri.path.toString == "/dummysvcactor/timeout" =>

    case ChunkedRequestStart(req) if req.uri.path.toString == "/dummysvcactor/chunktimeout" =>
      val handler = context.actorOf(Props[ChunkedRequestDropHandler])
      sender() ! RegisterChunkHandler(handler)
      handler forward req

    case t: Timedout =>
      timeoutListeners foreach { _ ! t }

    case RegisterTimeoutHandler =>
      timeoutListeners = timeoutListeners :+ sender()
  }
}

class ChunkedRequestHandler extends Actor with ActorLogging {

  var chunkCount = 0L
  var byteCount = 0L

  def receivedChunk(data: HttpData) {
    if (data.length > 0) {
      chunkCount += 1
      byteCount += data.length
    }
  }

  def receive = {
    case req: HttpRequest => receivedChunk(req.entity.data)

    case chunk: MessageChunk => receivedChunk(chunk.data)

    case chunkEnd: ChunkedMessageEnd =>
      sender() ! HttpResponse(OK, s"Received $chunkCount chunks and $byteCount bytes.")
      context.stop(self)
  }
}

class ChunkedRequestDropHandler extends Actor with ActorLogging {

  def receive = {
    case req: HttpRequest => // Don't handle. Drop everything

    case chunk: MessageChunk => // Don't handle. Drop.

    case chunkEnd: ChunkedMessageEnd => // Just stop this actor. Let the request timeout.
      context.stop(self)
  }
}
