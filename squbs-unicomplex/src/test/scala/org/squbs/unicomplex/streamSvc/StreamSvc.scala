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

package org.squbs.unicomplex.streamSvc

import java.io.{ByteArrayInputStream, InputStream}

import akka.actor._
import org.jvnet.mimepull.MIMEMessage
import spray.can.Http.RegisterChunkHandler
import spray.http.HttpMethods._
import spray.http.StatusCodes._
import spray.http.{ChunkedRequestStart, HttpRequest, HttpResponse, _}
import spray.io.CommandWrapper

import scala.annotation.tailrec
import scala.concurrent.duration._

class StreamSvc extends Actor with ActorLogging {

  def receive = {
    case req: HttpRequest if req.uri.path.toString() == "/streamsvc/ping" =>
      log.debug("Received request " + req.uri)
      sender() ! HttpResponse(OK, "pong")

    case start@ChunkedRequestStart(req@HttpRequest(POST, Uri.Path("/streamsvc/file-upload"), _, _, _)) =>
      val handler = context.actorOf(Props(new ChunkedRequestHandler(sender, start)))
      sender() ! RegisterChunkHandler(handler)
  }
}

object ChunkedRequestHandler{
  var chunkCount = 0L
  var byteCount = 0L
}

class ChunkedRequestHandler(client: ActorRef, start: ChunkedRequestStart) extends Actor with ActorLogging {
  import start.request._
  client ! CommandWrapper(SetRequestTimeout(Duration.Inf)) // cancel timeout
  val Some(HttpHeaders.`Content-Type`(ContentType(multipart: MultipartMediaType, _))) = header[HttpHeaders.`Content-Type`]
  val boundary = multipart.parameters("boundary")
  val content = new collection.mutable.ArrayBuffer[Byte]

  receivedChunk(entity.data)

  def receivedChunk(data: HttpData) {
    if (data.length > 0) {
      val byteArray = data.toByteArray
      //println("Received "+ChunkedRequestHandler.chunkCount +":"+ byteArray.length)
      content ++= byteArray
    }
  }

  def receive = {
    case chunk: MessageChunk => receivedChunk(chunk.data)

    case chunkEnd: ChunkedMessageEnd =>
      import collection.JavaConverters._
      new MIMEMessage(new ByteArrayInputStream(content.toArray), boundary).getAttachments.asScala.foreach(part => {
        // set the size for verification
        ChunkedRequestHandler.chunkCount += 1
        val body = part.readOnce()
        val size = sizeOf(body)
        ChunkedRequestHandler.byteCount += size
        println("Received "+ChunkedRequestHandler.chunkCount +":"+ size)
      })
      sender() ! HttpResponse(OK, s"Received $ChunkedRequestHandler.chunkCount chunks and $ChunkedRequestHandler.byteCount bytes.")
      //      client ! CommandWrapper(SetRequestTimeout(2.seconds)) // reset timeout to original value
      context.stop(self)
    case _ =>
      println("unknown message")
  }

  /**
   * Calculate the size of input stream.
   * @param is The input stream
   * @return The size of the input stream
   */
  def sizeOf(is: InputStream): Long = {
    val buffer = new Array[Byte](65000)

    @tailrec def inner(cur: Long): Long = {
      val read = is.read(buffer)
      if (read > 0) inner(cur + read)
      else cur
    }

    inner(0)
  }
}

