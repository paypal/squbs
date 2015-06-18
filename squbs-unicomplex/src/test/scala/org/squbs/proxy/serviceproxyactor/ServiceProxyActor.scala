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

package org.squbs.proxy.serviceproxyactor

import java.io.ByteArrayInputStream

import akka.actor._
import com.typesafe.config.Config
import org.apache.commons.io.IOUtils
import org.jvnet.mimepull.MIMEMessage
import org.squbs.pipeline.{NormalResponse, Processor, ProcessorFactory, RequestContext}
import org.squbs.unicomplex.WebContext
import spray.can.Http.RegisterChunkHandler
import spray.http.HttpHeaders.RawHeader
import spray.http.HttpMethods._
import spray.http.StatusCodes._
import spray.http.{ChunkedRequestStart, ChunkedResponseStart, Confirmed, HttpRequest, HttpResponse, _}

import scala.concurrent.{ExecutionContext, Future, Promise}

class ServiceProxyActor extends Actor with WebContext with ActorLogging {

  def receive = {


    case req@HttpRequest(GET, Uri.Path("/serviceproxyactor/msg/hello"), _, _, _) =>
      val customHeader = req.headers.find(h => h.name.equals("dummyReqHeader"))
      val output = customHeader match {
        case None => "No custom header found"
        case Some(header) => header.value
      }
      sender() ! HttpResponse(OK, output)

    case req@HttpRequest(GET, Uri.Path("/serviceproxyactor/msg/hello-chunk"), _, _, _) =>
      val customHeader = req.headers.find(h => h.name.equals("dummyReqHeader"))
      val output = customHeader match {
        case None => "No custom header found"
        case Some(header) => header.value
      }
      sender() ! ChunkedResponseStart(HttpResponse(OK, output))
      sender() ! MessageChunk("1")
      sender() ! MessageChunk("2")
      sender() ! MessageChunk("3")
      sender() ! ChunkedMessageEnd("abc")

    case req@HttpRequest(GET, Uri.Path("/serviceproxyactor/msg/hello-chunk-confirm"), _, _, _) =>
      val customHeader = req.headers.find(h => h.name.equals("dummyReqHeader"))
      val output = customHeader match {
        case None => "No custom header found"
        case Some(header) => header.value
      }
      sender() ! Confirmed(ChunkedResponseStart(HttpResponse(OK, output)), Pack(0))


    case Pack(i) =>
      if (i > 3) sender() ! ChunkedMessageEnd("123")
      else sender() ! Confirmed(MessageChunk(String.valueOf(i)), Pack(i + 1))

    case start@ChunkedRequestStart(req@HttpRequest(POST, Uri.Path("/serviceproxyactor/file-upload"), _, _, _)) =>
      val handler = context.actorOf(Props(new ChunkHandler(sender, start)))
      sender() ! RegisterChunkHandler(handler)


  }

}

case class Pack(count: Int)

class ChunkHandler(client: ActorRef, start: ChunkedRequestStart) extends Actor with ActorLogging {

  import start.request._

  // client ! CommandWrapper(SetRequestTimeout(Duration.Inf))
  // cancel timeout
  val Some(HttpHeaders.`Content-Type`(ContentType(multipart: MultipartMediaType, _))) = header[HttpHeaders.`Content-Type`]
  val boundary = multipart.parameters("boundary")

  val content = new collection.mutable.ArrayBuffer[Byte]

  val modifiedHeader = headers.find(h => h.name.equals("dummyReqHeader")).getOrElse(RawHeader("dummyReqHeader", "Unknown")).value

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
      import scala.collection.JavaConverters._

      val output = new collection.mutable.ArrayBuffer[Byte]
      new MIMEMessage(new ByteArrayInputStream(content.toArray), boundary).getAttachments.asScala.foreach(part => {
        // set the size for verification
        output ++= IOUtils.toByteArray(part.readOnce())

      })

      sender() ! HttpResponse(OK, HttpEntity(output.toArray), List(RawHeader("dummyReqHeader", modifiedHeader)))
      //      client ! CommandWrapper(SetRequestTimeout(2.seconds)) // reset timeout to original value
      context.stop(self)
    case _ =>
      println("unknown message")
  }


}


class DummyProcessorForActor extends Processor with ProcessorFactory {

  def create(settings: Option[Config])(implicit actorRefFactory: ActorRefFactory): Option[Processor] = Some(this)

  override def processResponseChunk(ctx : RequestContext, chunk: MessageChunk)(implicit context: ActorContext): MessageChunk = {
    val raw = new String(chunk.data.toByteArray)
    MessageChunk(raw + "a")
  }

  def inbound(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext): Future[RequestContext] = {
    reqCtx.request match {
      case HttpRequest(GET, Uri.Path("/serviceproxyactor/msg/processingRequestError"), _, _, _) =>
        val promise = Promise[RequestContext]()
        promise.failure(new RuntimeException("BadMan"))
        promise.future
      case other =>
        val newreq = reqCtx.request.copy(headers = RawHeader("dummyReqHeader", "PayPal") :: reqCtx.request.headers)
        Promise.successful(reqCtx.copy(request = newreq, attributes = reqCtx.attributes + ("key1" -> "CDC"))).future
    }

  }

  //outbound processing
  def outbound(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext): Future[RequestContext] = {
    val newCtx = reqCtx.response match {
      case nr@NormalResponse(r) =>
        reqCtx.copy(response = nr.update(r.copy(headers = RawHeader("dummyRespHeader", reqCtx.attribute[String]("key1").getOrElse("Unknown")) :: r.headers)))

      case other => reqCtx
    }
    val promise = Promise[RequestContext]()
    promise.success(newCtx)
    promise.future
  }


}




