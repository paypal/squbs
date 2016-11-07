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

import akka.actor.{ActorRefFactory, ActorContext}
import org.squbs.pipeline.PipelineExecutor.Target
import spray.http.{HttpRequest, HttpResponse}

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

object PipelineExecutor {
  type Target = HttpRequest => Future[HttpResponse]
}

class PipelineExecutor(target: Target, processor: Processor) {

  import processor._

  def execute(ctx: RequestContext)(implicit refFactory: ActorRefFactory): Future[HttpResponse] = {
    val p = Promise[HttpResponse]
    import refFactory.dispatcher

    def afterResponse(rawResponse: Future[HttpResponse], currentCtx: RequestContext): Unit = {
      rawResponse onComplete {
        // 2nd hop
        case Success(resp) =>
          outboundForResponse(currentCtx.copy(response = NormalResponse(resp)))
        case Failure(t) =>
          outboundForResponse(onResponseError(currentCtx, t))
      }
    }

    def outboundForResponse(reqCtx: RequestContext) = {
      var outboundCtx = reqCtx
      try {
        outboundCtx = preOutbound(outboundCtx)
        outbound(outboundCtx) onComplete {
          case Success(ctxAfterOutbound) =>
            p complete postProcess(ctxAfterOutbound)
          case Failure(t) =>
            p complete postProcess(onResponseError(outboundCtx, t))
        }
      } catch {
        case t: Throwable =>
          p complete postProcess(onResponseError(outboundCtx, t)) // from preOutbound
      } finally {
        outboundFinalize(outboundCtx)
      }
    }

    def postProcess(ctx: RequestContext): Try[HttpResponse] = {
      val newCtx = try {
        postOutbound(ctx)
      } catch {
        case t: Throwable => onResponseError(ctx, t)
      }
      finalOutput(newCtx)
    }

    var newCtx = ctx
    try {
      newCtx = preInbound(ctx)
      if (newCtx.responseReady) {
        p complete postProcess(newCtx)
      } else {
        inbound(newCtx) onComplete {
          // 1st hop
          case Success(ctxAfterInbound) =>
            try {
              if (ctxAfterInbound.responseReady) p complete postProcess(ctxAfterInbound)
              else {
                val ctxAfterPostInbound = postInbound(ctxAfterInbound)
                afterResponse(target(ctxAfterPostInbound.request), ctxAfterPostInbound)
              }
            } catch {
              case t: Throwable =>
                p complete postProcess(onRequestError(ctxAfterInbound, t))
            }
          case Failure(t) =>
            p complete postProcess(onRequestError(newCtx, t)) // from inbound
        }
      }
    } catch {
      case t: Throwable =>
        p complete postProcess(onRequestError(newCtx, t)) // from preInbound
    } finally {
      inboundFinalize(newCtx)
    }
    p.future
  }

  def execute(request: HttpRequest)(implicit context: ActorContext): Future[HttpResponse] = {
    execute(RequestContext(request))
  }




  private def finalOutput(ctx: RequestContext): Try[HttpResponse] = {
    ctx.response match {
      case r: NormalResponse =>
        r.responseMessage match {
          case r: HttpResponse => Success(r)
          case other => Failure(new IllegalArgumentException("Unsupported response: " + other))
        }
      case r: ExceptionalResponse =>
        r.cause match {
          case Some(t) => Failure(t)
          case None => Success(r.response)
        }
      case other =>
        Failure(new IllegalArgumentException("Unsupported response: " + other))
    }
  }


}
