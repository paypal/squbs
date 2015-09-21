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

package org.squbs.pipeline

import akka.actor._
import org.squbs.pipeline.PipelineProcessorActor.PipelineTarget
import spray.can.Http.RegisterChunkHandler
import spray.http._

import scala.util.{Failure, Success}

object PipelineProcessorActor {
  type PipelineTarget = (HttpRequestPart, ActorRef) => Unit

  implicit def toTarget(recevier: ActorRef): PipelineTarget = (req, sender) => {
    recevier.tell(req, sender)
  }
}

//TODO use FSM ??
class PipelineProcessorActor(target: PipelineTarget, processor: Processor) extends Actor with ActorLogging with Stash {

  import context.dispatcher
  import processor._

  //common receiver for all cases
  def onPostProcess(client: ActorRef): Receive = {
    case PostProcess(ctx) => postProcess(ctx, client)
    case other => client forward other // unknown message
  }

  override def receive = {
    //Only accept RequestContext
    case ctx: RequestContext =>
      val client = sender()
      context.become(onPostProcess(client)) // ready to handle exception or postProcess
      inboundForRequest(ctx, client)

    case other => sender ! HttpResponse(status = StatusCodes.InternalServerError, entity = "Pipeline Error!")
  }

  private def inboundForRequest(ctx: RequestContext, client: ActorRef) = {
    var newCtx = ctx
    try {
      newCtx = preInbound(ctx)
      newCtx.response match {
        case nr: NormalResponse => postProcess(newCtx, client) //TODO need go thru outbound??
        case er: ExceptionalResponse => postProcess(newCtx, client)
        case _ =>
          inbound(newCtx) onComplete {
            case Success(result) =>
              try {
                if (result.responseReady) self ! PostProcess(result)
                else {
                  val postResult = postInbound(result)
                  context.become(onResponse(postResult, client) orElse onPostProcess(client))
                  target(postResult.payload, self)
                }
              } catch {
                case t: Throwable =>
                  t.printStackTrace()
                  log.error(t, "Error in postInbound processing")
                  self ! PostProcess(onRequestError(result, t))
              }
            case Failure(t) =>
              log.error(t, "Error in inbound processing")
              self ! PostProcess(onRequestError(newCtx, t))
          }
      }
    } catch {
      case t: Throwable =>
        log.error(t, "Error in processing request")
        self ! PostProcess(onRequestError(newCtx, t))
    } finally {
      inboundFinalize(newCtx)
    }
  }

  private def outboundForResponse(reqCtx: RequestContext, msgFunc: RequestContext => Any) = {
    var newCtx = reqCtx
    try {
      newCtx = preOutbound(newCtx)
      outbound(newCtx) onComplete {
        case Success(result) =>
          self ! msgFunc(result)
        case Failure(t) =>
          log.error(t, "Error in processing outbound")
          self ! PostProcess(onResponseError(newCtx, t)) // chunks will be dead letters?
      }
    } catch {
      case t: Throwable =>
        log.error(t, "Error in processing response")
        self ! PostProcess(onResponseError(newCtx, t))
    } finally {
      outboundFinalize(newCtx)
    }
  }

  // ready to serve response from proxied actor/route
  def onResponse(reqCtx: RequestContext, client: ActorRef): Receive = {

    case resp: HttpResponse =>
      outboundForResponse(reqCtx.copy(response = NormalResponse(resp)), ctx => PostProcess(ctx))

    case ReadyToChunk(ctx) =>
      val newCtx = postProcess(ctx, client)
      unstashAll()
      context.become(onChunk(newCtx, client) orElse onPostProcess(client))

    case respStart: ChunkedResponseStart =>
      outboundForResponse(reqCtx.copy(response = NormalResponse(respStart)), ctx => ReadyToChunk(ctx))

    case data@Confirmed(ChunkedResponseStart(resp), ack) =>
      outboundForResponse(reqCtx.copy(response = NormalResponse(data, sender())), ctx => ReadyToChunk(ctx))

    case chunk: MessageChunk => stash()

    case chunkEnd: ChunkedMessageEnd => stash()

    case Confirmed(data, ack) => stash()

    case rch@RegisterChunkHandler(handler) =>
      val chunkHandler = context.actorOf(Props(classOf[ChunkHandler], handler, self, processor, reqCtx))
      client ! RegisterChunkHandler(chunkHandler)

    case Status.Failure(t) =>
      log.error(t, "Receive Status.Failure")
      outboundForResponse(onResponseError(reqCtx, t), ctx => PostProcess(ctx)) // make sure preOutbound gets invoked to pair with postInbound

    case t: Throwable =>
      log.error(t, "Receive Throwable")
      outboundForResponse(onResponseError(reqCtx, t), ctx => PostProcess(ctx))

  }

  //usually chunks will not go to postProcess but go to finalOutput directly.
  def onChunk(reqCtx: RequestContext, client: ActorRef): Receive = {

    case chunk: MessageChunk =>
      processChunk(reqCtx) {
        finalOutput(reqCtx.copy(response = NormalResponse(processResponseChunk(reqCtx, chunk))), client)
      }

    case chunkEnd: ChunkedMessageEnd =>
      processChunk(reqCtx) {
        finalOutput(reqCtx.copy(response = NormalResponse(processResponseChunkEnd(reqCtx, chunkEnd))), client)
      }

    case data@Confirmed(mc@(_: MessageChunk), ack) =>
      processChunk(reqCtx) {
        val newChunk = processResponseChunk(reqCtx, mc)
        finalOutput(reqCtx.copy(response = NormalResponse(Confirmed(newChunk, ack), sender())), client)
      }
    case AckInfo(rawAck, receiver) =>
      processChunk(reqCtx) {
        receiver tell(rawAck, self)
      }
  }

  private def postProcess(ctx: RequestContext, client: ActorRef): RequestContext = {
    val newCtx: RequestContext = try {
      postOutbound(ctx)
    } catch {
      case t: Throwable =>
        log.error(t, "Error in processing postProcess")
        onResponseError(ctx, t)
    }
    finalOutput(newCtx, client)
    newCtx
  }

  private def finalOutput(ctx: RequestContext, client: ActorRef) = {

    ctx.response match {
      case r: NormalResponse =>
        val response = r.responseMessage
        client ! response
        response match {
          case r@(_: HttpResponse | _: ChunkedMessageEnd) => context stop self
          case other =>
        }
      case r: ExceptionalResponse =>
        //TODO needs to check if chunk already start
        client ! r.response
        context stop self

      case other =>
        log.error("Unexpected response: " + other)
        client ! ExceptionalResponse.defaultErrorResponse
        context stop self
    }
  }
}

case class ReadyToChunk(ctx: RequestContext)

case class PostProcess(ctx: RequestContext)

private class ChunkHandler(realHandler: ActorRef, caller: ActorRef, processor: Processor, reqCtx: RequestContext) extends Actor {

  import processor._

  def receive: Actor.Receive = {
    case chunk: MessageChunk => realHandler tell(processRequestChunk(reqCtx, chunk), caller)

    case chunkEnd: ChunkedMessageEnd => realHandler tell(processRequestChunkEnd(reqCtx, chunkEnd), caller)

    case other => realHandler tell(other, caller)

  }
}