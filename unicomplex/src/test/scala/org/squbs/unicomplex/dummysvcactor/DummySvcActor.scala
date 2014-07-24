package org.squbs.unicomplex.dummysvcactor

import akka.actor.{Props, ActorLogging, Actor}
import spray.can.Http.RegisterChunkHandler
import spray.http.StatusCodes._
import spray.http._

class DummySvcActor extends Actor with ActorLogging {

  def receive = {
    case req: HttpRequest if req.uri.path.toString == "/dummysvcactor/ping" =>
      log.debug("Received request " + req.uri)
      sender() ! HttpResponse(OK, "pong")

    case ChunkedRequestStart(req) if req.uri.path.toString == "/dummysvcactor/chunks" =>
      val handler = context.actorOf(Props[ChunkedRequestHandler])
      sender() ! RegisterChunkHandler(handler)
      handler forward req
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
  }
}
