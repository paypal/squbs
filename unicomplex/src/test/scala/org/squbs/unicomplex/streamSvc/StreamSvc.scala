package org.squbs.unicomplex.streamSvc


import akka.actor._
import spray.http._
import spray.http.HttpMethods._
import org.squbs.unicomplex.RouteDefinition
import spray.routing._
import scala.concurrent.duration._
import akka.util.Timeout
import spray.http.HttpRequest
import spray.http.HttpResponse
import spray.http.ChunkedRequestStart
import spray.http.Timedout
import org.squbs.unicomplex.streamCube.StreamCube
import spray.can.Http.RegisterChunkHandler
import spray.http.StatusCodes._

/**
 * Created by junjshi on 14-7-18.
 */
/*class StreamSvc  extends RouteDefinition{// with MstoreService {
  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  implicit val timeout: Timeout = 1.second // for the actor 'asks'


  // this actor only runs our route, but you could add
  // other things here, like request stream processing,
  // timeout handling or alternative handler registration
  def receive = receiveCanRules
  def route = receive


  def receiveCanRules : Actor.Receive = {
     case rc@RequestContext(HttpRequest(POST, Uri.Path("/streamsvc/file-upload"), headers, entity: HttpEntity.NonEmpty, protocol),_,_) =>
        // emulate chunked behavior for POST requests to this path
       val r = rc.request
       val parts = r.asPartStream()
        val client = context.sender
        val handler = context.actorOf(Props(new StreamCube(client,  parts.head.asInstanceOf[ChunkedRequestStart])))
        parts.tail.foreach(handler !)

      case Timedout(HttpRequest(method, uri, _, _, _)) =>
        context.sender ! HttpResponse(
          status = StatusCodes.InternalServerError,
          entity = "The " + method + " request to '" + uri + "' has timed out..."
        )
  }

}*/

class StreamSvc extends Actor with ActorLogging {

  def receive = {
    case req: HttpRequest if req.uri.path.toString() == "/streamsvc/ping" =>
      log.debug("Received request " + req.uri)
      sender() ! HttpResponse(OK, "pong")
    /*
    case ChunkedRequestStart(req) if req.uri.path.toString() == "/streamsvc/file-upload" =>
      val handler = context.actorOf(Props[ChunkedRequestHandler])
      sender() ! RegisterChunkHandler(handler)
      handler forward req
    */
    case req1@HttpRequest(POST, Uri.Path("/streamsvc/file-upload"), _, _, _)=>
      val parts = req1.asPartStream()
      val client = context.sender
      //val handler1 = context.actorOf(Props[ChunkedRequestHandler])
      val handler1 = context.actorOf(Props(new StreamCube(client,  parts.head.asInstanceOf[ChunkedRequestStart])))
      sender() ! RegisterChunkHandler(handler1)
      parts.tail.foreach(handler1 forward)
      //handler1 forward req1
  }
}

object ChunkedRequestHandler{
  var chunkCount = 0L
  var byteCount = 0L
}

class ChunkedRequestHandler extends Actor with ActorLogging {



  def receivedChunk(data: HttpData) {
    if (data.length > 0) {
      println("Received "+ChunkedRequestHandler.chunkCount +":"+ ChunkedRequestHandler.byteCount)
      ChunkedRequestHandler.chunkCount += 1
      ChunkedRequestHandler.byteCount += data.length
    }
  }

  def receive = {
    case req: HttpRequest =>
      receivedChunk(req.entity.data)



    case chunk: MessageChunk => receivedChunk(chunk.data)

    case chunkEnd: ChunkedMessageEnd =>
      sender() ! HttpResponse(OK, s"Received $ChunkedRequestHandler.chunkCount chunks and $ChunkedRequestHandler.byteCount bytes.")
      context.stop(self)
    case _ =>
      println("unknown message")
  }
}

