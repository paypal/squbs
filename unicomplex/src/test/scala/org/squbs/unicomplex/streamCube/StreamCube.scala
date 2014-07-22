package org.squbs.unicomplex.streamCube


import akka.actor._
import spray.http.MediaTypes._
import spray.http._
import spray.httpx.unmarshalling._
import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.util.control.NonFatal
import spray.io.CommandWrapper
import spray.http.ChunkedRequestStart
import scala.Some
import spray.http.HttpResponse
import spray.http.SetRequestTimeout


/**
 * Handles uploading of multiple blobs formatted as chunked multi-part content
 * @param client
 * @param start
 * @author Yuri Finkelstein
 */
class StreamCube(client: ActorRef, start: ChunkedRequestStart) extends Actor with ActorLogging {

  import start.request._ //gives us uri and method
  client ! CommandWrapper(SetRequestTimeout(Duration.Inf)) // cancel timeout
  val Some(HttpHeaders.`Content-Type`(ContentType(multipart: MultipartMediaType, xx))) = header[HttpHeaders.`Content-Type`]
  require (multipart.matches(`multipart/form-data`), "at the moment this code supports only multipart/form-data only")
  val boundary = multipart.parameters("boundary")
  var bytesReceived = 0L
  var currentPartData:HttpData = HttpData.Empty
  var parser:Parser = new RootParser (new BoundaryMatcher (boundary), true)
  log.debug(s"Got start of multipart POST request $method $uri with multipart boundary '$boundary'")
  var parts = ArrayBuffer[String]()
  var request: Option[Create] = None
  var currentSegment: Option[String] = None
  var isBadRequest = false
  val partsMap: mutable.Map[String,String] = mutable.Map.empty



  val patternSegment = ("^" + PARTNAME_SEGMENT + "(.+)$").r
    val patternRequest = ("^" + PARTNAME_REQUEST + "$").r
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
      log.debug(s"Got ${c.data.length} bytes of chunked request $method $uri}")

      try handleParsingResult (parser(c.data.toByteString))
      catch {
        case e: ExceptionWithErrorInfo ⇒ handleError (StatusCodes.BadRequest, e.info)
        case NonFatal(e)               ⇒ handleError (StatusCodes.BadRequest, ErrorInfo("Illegal request", e.getMessage))
      }

    case e: ChunkedMessageEnd =>
      log.debug(s"Got end of multipart request $method $uri")
  }

}
