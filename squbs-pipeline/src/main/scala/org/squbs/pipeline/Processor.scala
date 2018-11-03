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
package org.squbs.pipeline

import akka.actor.{ActorContext, ActorRefFactory}
import com.typesafe.config.Config
import spray.http.{ChunkedMessageEnd, MessageChunk}

import scala.concurrent.{ExecutionContext, Future}

trait Handler {
  def process(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext): Future[RequestContext]
}

trait HandlerFactory {
  def create(config: Option[Config])(implicit actorRefFactory: ActorRefFactory): Option[Handler]
}

//Must be stateless
trait Processor {

  //inbound processing
  def inbound(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext): Future[RequestContext]

  //outbound processing
  def outbound(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext): Future[RequestContext]

  //first chance to handle input request before processing request
  def preInbound(ctx: RequestContext)(implicit context: ActorContext): RequestContext = {
    ctx
  }

  //last chance to handle input request before sending request to underlying service
  def postInbound(ctx: RequestContext)(implicit context: ActorContext): RequestContext = {
    ctx
  }

  //place holder for special use case like clean up thread local, usually don't need it.
  def inboundFinalize(ctx: RequestContext): Unit = {
  }

  //first chance to handle response before executing outbound
  def preOutbound(ctx: RequestContext)(implicit context: ActorContext): RequestContext = {
    ctx
  }

  //last chance to handle output after executing outbound
  def postOutbound(ctx: RequestContext)(implicit context: ActorContext): RequestContext = {
    ctx
  }

  //place holder for special use case like clean up thread local, usually don't need to override
  def outboundFinalize(ctx: RequestContext): Unit = {
  }

  //hook method for special use case like install/cleanup thread local, usually don't need to override
  def processChunk(ctx: RequestContext)(func: => Unit): Unit = func

  //sync method to process response chunk
  def processResponseChunk(ctx: RequestContext, chunk: MessageChunk)(implicit context: ActorContext): MessageChunk = {
    chunk
  }

  //sync method to process response chunk end
  def processResponseChunkEnd(ctx: RequestContext, chunkEnd: ChunkedMessageEnd)(implicit context: ActorContext): ChunkedMessageEnd = {
    chunkEnd
  }

  //sync method to process request chunk
  def processRequestChunk(ctx: RequestContext, chunk: MessageChunk)(implicit context: ActorContext): MessageChunk = {
    chunk
  }

  //sync method to process request chunk end
  def processRequestChunkEnd(ctx: RequestContext, chunkEnd: ChunkedMessageEnd)(implicit context: ActorContext): ChunkedMessageEnd = {
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

}

trait ProcessorFactory {
  def create(settings: Option[Config])(implicit actorRefFactory: ActorRefFactory): Option[Processor]
}
