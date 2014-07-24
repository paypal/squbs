package org.squbs.unicomplex.streamSvc


import akka.actor._
import spray.http._
import spray.http.HttpMethods._
import scala.concurrent.duration._
import org.squbs.unicomplex.streamCube._
import spray.http.StatusCodes._
import spray.http.MediaTypes._
import scala.annotation.tailrec
import spray.http.HttpRequest
import spray.can.Http.RegisterChunkHandler
import spray.io.CommandWrapper
import spray.http.ChunkedRequestStart
import org.squbs.unicomplex.streamCube.CreateResult
import scala.Some
import spray.http.HttpResponse
import spray.http.SetRequestTimeout
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import spray.httpx.unmarshalling._
import java.io.{IOException, FileNotFoundException, FileOutputStream, File}


/**
 * Created by junjshi on 14-7-18.
 */

class StreamSvc extends Actor with ActorLogging {

  var handler1:ActorRef = null
  def receive = {
    case req: HttpRequest if req.uri.path.toString() == "/streamsvc/ping" =>
      log.debug("Received request " + req.uri)
      sender() ! HttpResponse(OK, "pong")

    case ChunkedRequestStart(req@HttpRequest(POST, Uri.Path("/streamsvc/file-upload"), _, _, _)) =>
      val parts = req.asPartStream()
      val client = context.sender
      handler1 = context.actorOf(Props(new ChunkedRequestHandler(client,parts.head.asInstanceOf[ChunkedRequestStart])))
      sender() ! RegisterChunkHandler(handler1)
      parts.tail.foreach(handler1 forward)
  }
}

object ChunkedRequestHandler{
  var chunkCount = 0L
  var byteCount = 0L
}

class ChunkedRequestHandler(client: ActorRef, start: ChunkedRequestStart) extends Actor with ActorLogging {
  import start.request._
  val Some(HttpHeaders.`Content-Type`(ContentType(multipart: MultipartMediaType, xx))) = header[HttpHeaders.`Content-Type`]
  val boundary = multipart.parameters("boundary")
  var parser:Parser = new RootParser (new BoundaryMatcher (boundary), true)
  val patternSegment = ("^" + PARTNAME_SEGMENT + "(.+)$").r
  val patternRequest = ("^" + PARTNAME_REQUEST + "$").r
  var request: Option[Create] = None
  var currentSegment: Option[String] = None
  var parts = ArrayBuffer[String]()
  var isBadRequest = false
  val partsMap: mutable.Map[String,String] = mutable.Map.empty
  var currentPartData:HttpData = HttpData.Empty
  var datas:HttpData = HttpData.Empty
  var bytesReceived = 0L
  client ! CommandWrapper(SetRequestTimeout(Duration.Inf)) // cancel timeout




  def receivedChunk(data: HttpData) {
    if (data.length > 0) {
      println("Received "+ChunkedRequestHandler.chunkCount +":"+ data.length)
      ChunkedRequestHandler.chunkCount += 1
      ChunkedRequestHandler.byteCount += data.length
    }
  }

  def completeRequest () {
    client !  HttpResponse(entity = spray.http.HttpEntity(`application/json`, CreateResult(bytesReceived, partsMap.toList.sortBy(_._1.toInt)).toJson.prettyPrint))
    client ! CommandWrapper (SetRequestTimeout(2.seconds)) // reset timeout to original value
    //context.stop(self) //TODO somehow context is null here

  }

  def handleError(status: StatusCode, info: ErrorInfo): Unit = {
    if (!isBadRequest) {
      log.warning("Illegal request, responding with status '{}': {}", status, info.formatPretty)
      val msg = if (true /*settings.verboseErrorMessages*/ ) info.formatPretty else info.summary
      client ! HttpResponse(status = status, entity = msg)
      parser = Event.IgnoreAllFurtherInput
      isBadRequest = true
    }
  }


  def receive = {
    case c: MessageChunk =>
      handleParsingResult (parser(c.data.toByteString))
    case e: ChunkedMessageEnd =>
    case req: HttpRequest =>
      println("unknown message")
  }

  @tailrec private def handleParsingResult(result: Event): Unit =
    result match {

      case Event.PartStart (part, cont) ⇒
        log.debug (s"Seeing start of new part ${part.disp.parameters.get("name")} with file ${part.disp.parameters.get("filename")} content-type=${part.contentType}")

        part.disp.parameters.get("name") match {
          case Some(patternRequest()) if request.isEmpty && part.contentType.mediaType == ContentTypes.`application/json`.mediaType =>
            log.debug ("detected start of request part")

          case Some(patternSegment(seg)) if request.nonEmpty =>
            log.debug(s"detected start of a segment part $seg")
            currentSegment = Some(seg)
            parts += seg
            partsMap(currentSegment.get) = seg

          case x => handleError (StatusCodes.BadRequest, ErrorInfo (s"unexpected part name $x of type ${part.contentType.mediaType}"))
        }
        handleParsingResult(cont())

      case Event.PartContinue (data, part, next) ⇒
        log.debug (s"Seeing a chunk of size ${data.length} of part ${part.disp.parameters.get("name")} with file ${part.disp.parameters.get("filename")}: ${/*CharUtils.asciiString (data.toByteString, 0, 1000)*/}")
        currentPartData = currentPartData +: data
        if (request.nonEmpty) bytesReceived += data.length //we only count bytes toward parts other than the request itself (json)
        parser = next // wait for the next packet

      case Event.PartEnd (data, part, isLastPart, cont) ⇒
        log.debug (s"Seeing the last chunk of size ${data.length} of part ${part.disp.parameters.get("name")} with file ${part.disp.parameters.get("filename")}: ${/*CharUtils.asciiString (data.toByteString, 0, 1000)*/}")
        currentPartData = currentPartData +: data
        if (part.contentType.mediaType == ContentTypes.`application/json`.mediaType) {
          if (request.isEmpty) { // parse the request json
            HttpEntity(`application/json`, currentPartData).as[Create].fold(
              err => handleError(StatusCodes.BadRequest, ErrorInfo("DeserializationError", err.toString)),
              req => { request = Some(req); log.debug (s"decoded request: $req") }
            )

          } else {
            handleError (StatusCodes.BadRequest, ErrorInfo (s"only the first part should have content-type=${ContentTypes.`application/json`}"))
          }
        } else {
          bytesReceived += data.length
        }
        //println(currentPartData.toByteString)
        datas = datas +: currentPartData
        currentPartData = HttpData.Empty
        currentSegment = None
        if (!isLastPart) handleParsingResult (cont())
        else {
          log.debug ("Got the closing of the last part!")
          completeRequest
        }

      case Event.NeedMoreData(next) ⇒
        log.debug (s"Received NeedMoreData, currentPartData.length=${currentPartData.length}")
        parser = next // wait for the next packet

      case e @ Event.ParsingError(status, info) ⇒ handleError(status, info)

      case Event.IgnoreAllFurtherInput          ⇒
    }


}



