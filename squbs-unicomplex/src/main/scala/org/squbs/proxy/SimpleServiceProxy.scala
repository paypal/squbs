/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the AUTHORS file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.squbs.proxy

import akka.actor._
import spray.http._
import scala.concurrent.Future
import com.typesafe.config.Config
import spray.can.Http.RegisterChunkHandler
import spray.http.Confirmed
import spray.http.ChunkedRequestStart
import scala.util.{Failure, Success}
import spray.http.ChunkedResponseStart
import spray.http.HttpResponse


abstract class SimpleServiceProxy(settings: Option[Config], hostActor: ActorRef) extends ServiceProxy(settings, hostActor) {

  def handleRequest(requestCtx: RequestContext, responder: ActorRef)(implicit actorContext: ActorContext): Unit = {
    //val actor = actorContext.actorOf(Props(classOf[InnerActor], responder))
    val actor = actorContext.actorOf(Props(new InnerActor(responder)))
    actor ! requestCtx
  }

  //inbound processing
  def processRequest(reqCtx: RequestContext): Future[RequestContext]

  //outbound processing
  def processResponse(reqCtx: RequestContext): Future[RequestContext]

  //first chance to handle input request before processing request
  def preInbound(ctx: RequestContext): RequestContext = {
    ctx
  }

  //last chance to handle output
  def postOutbound(ctx: RequestContext): RequestContext = {
    ctx
  }

  def processResponseChunk(chunk: MessageChunk): MessageChunk = {
    chunk
  }

  def processResponseChunkEnd(chunkEnd: ChunkedMessageEnd): ChunkedMessageEnd = {
    chunkEnd
  }

  def processRequestChunk(chunk: MessageChunk): MessageChunk = {
    chunk
  }

  def processRequestChunkEnd(chunkEnd: ChunkedMessageEnd): ChunkedMessageEnd = {
    chunkEnd
  }

  //user can override this to generate customized error when failed in processing request
  def onRequestError(reqCtx: RequestContext, t: Throwable): RequestContext = {
    reqCtx.copy(response = ExceptionalResponse(t))
  }

  //user can override this to generate customized error when failed in processing response
  def onResponseError(reqCtx: RequestContext, t: Throwable): RequestContext = {
    val rawResp = reqCtx.response match {
      case r: NormalResponse => Some(r)
      case other => None
    }
    reqCtx.copy(response = ExceptionalResponse(t, rawResp))
  }

  case class ReadyToChunk(ctx: RequestContext)

  case class PostProcess(ctx: RequestContext)

  private class InnerActor(responder: ActorRef) extends Actor with Stash {

    import context.dispatcher

    def onPostProcess: Actor.Receive = {
      case PostProcess(ctx) => postProcess(ctx)
      case other => responder forward other // unknown message
    }

    def receive: Actor.Receive = onRequest orElse onPostProcess

    def onRequest: Actor.Receive = {
      case ctx: RequestContext =>
        val newCtx = preInbound(ctx)
        processRequest(newCtx) onComplete {
          case Success(result) =>
            val payload = newCtx.isChunkRequest match {
              case true => ChunkedRequestStart(result.request)
              case false => result.request
            }
            hostActor ! payload
            context.become(onResponse(result) orElse onPostProcess)
          case Failure(t) =>
            log.error(t, "Error in processing request")
            self ! PostProcess(onRequestError(newCtx, t))
        }
    }

    // ready to serve response from proxied actor/route
    def onResponse(reqCtx: RequestContext): Actor.Receive = {

      case resp: HttpResponse =>
        val newCtx = reqCtx.copy(response = NormalResponse(resp))
        processResponse(newCtx) onComplete {
          case Success(result) =>
            self ! PostProcess(result)
          case Failure(t) =>
            log.error(t, "Error in processing response")
            self ! PostProcess(onResponseError(newCtx, t))
        }

      case ReadyToChunk(ctx) =>
        postProcess(ctx)
        unstashAll()
        context.become(onChunk(ctx) orElse onPostProcess)


      case respStart: ChunkedResponseStart =>
        val newCtx = reqCtx.copy(response = NormalResponse(respStart))
        processResponse(newCtx) onComplete {
          case Success(result) =>
            self ! ReadyToChunk(result)
          case Failure(t) =>
            log.error(t, "Error in processing ChunkedResponseStart")
            self ! PostProcess(onResponseError(newCtx, t))
        }

      case data@Confirmed(ChunkedResponseStart(resp), ack) =>
        val newCtx = reqCtx.copy(response = NormalResponse(data, sender()))
        processResponse(newCtx) onComplete {
          case Success(result) =>
            self ! ReadyToChunk(result)
          case Failure(t) =>
            log.error(t, "Error in processing confirmed ChunkedResponseStart")
            self ! PostProcess(onResponseError(newCtx, t))
        }


      case chunk: MessageChunk => stash()

      case chunkEnd: ChunkedMessageEnd => stash()

      case Confirmed(data, ack) => stash()

      case rch@RegisterChunkHandler(handler) =>
        //val chunkHandler = context.actorOf(Props(classOf[ChunkHandler], handler, self))
        val chunkHandler = context.actorOf(Props(new ChunkHandler(handler, self)))
        responder ! RegisterChunkHandler(chunkHandler)

    }

    def onChunk(reqCtx: RequestContext): Actor.Receive = {

      case chunk: MessageChunk =>
        postProcess(reqCtx.copy(response = NormalResponse(processResponseChunk(chunk))))

      case chunkEnd: ChunkedMessageEnd =>
        postProcess(reqCtx.copy(response = NormalResponse(processResponseChunkEnd(chunkEnd))))

      case data@Confirmed(mc@(_: MessageChunk), ack) =>
        val newChunk = processResponseChunk(mc)
        postProcess(reqCtx.copy(response = NormalResponse(Confirmed(newChunk, ack), sender())))

      case AckInfo(rawAck, receiver) =>
        receiver tell(rawAck, self)


    }


    private def postProcess(ctx: RequestContext): Unit = {
      finalOutput(postOutbound(ctx))
    }

    private def finalOutput(ctx: RequestContext): Unit = {

      ctx.response match {
        case r: NormalResponse =>
          val response = r.responseMessage
          responder ! response
          response match {
            case r@(_: HttpResponse | _: ChunkedMessageEnd) => context stop self
            case other =>
          }
        case r: ExceptionalResponse =>
          //TODO needs to check if chunk already start
          responder ! r.response
          context stop self

        case other =>
          log.error("Unexpected response: " + other)
          responder ! ExceptionalResponse.defaultErrorResponse
          context stop self
      }

    }


  }

  private class ChunkHandler(realHandler: ActorRef, caller: ActorRef) extends Actor {
    def receive: Actor.Receive = {
      case chunk: MessageChunk => realHandler tell(processRequestChunk(chunk), caller)

      case chunkEnd: ChunkedMessageEnd => realHandler tell(processRequestChunkEnd(chunkEnd), caller)

      case other => realHandler tell(other, caller)

    }
  }

}


