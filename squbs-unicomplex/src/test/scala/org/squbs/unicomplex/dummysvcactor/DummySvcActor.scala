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
      context.stop(self)
  }
}
