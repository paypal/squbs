/*
 * Copyright 2017 PayPal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.squbs.unicomplex.dummyflowsvc

import org.apache.pekko.http.scaladsl.model.HttpEntity.{ChunkStreamPart, Chunked}
import org.apache.pekko.http.scaladsl.model.Uri.Path
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.stream.scaladsl.{Flow, Source}
import org.squbs.unicomplex.{FlowDefinition, WebContext}

class DummyFlowSvc extends FlowDefinition with WebContext {

  val prefix = if (webContext.isEmpty) "" else s"/$webContext"
  val pingPath = s"$prefix/ping"
  val chunkPath = s"$prefix/chunks"
  val exceptionPath = s"$prefix/throwit"

  def flow = Flow[HttpRequest].map {
    case HttpRequest(_, Uri(_, _, Path(`pingPath`), _, _), _, _, _) =>
      HttpResponse(StatusCodes.OK, entity = "pong")

    case req @ HttpRequest(_, Uri(_, _, Path(`chunkPath`), _, _), _, _, _) =>

      val responseChunks = req.entity.dataBytes.filter(_.nonEmpty)
        .map { b => (1, b.length) }
        .reduce { (a, b) => (a._1 + b._1, a._2 + b._2) }
        .map { case (chunkCount, byteCount) =>
          ChunkStreamPart(s"Received $chunkCount chunks and $byteCount bytes.\r\n")
        }
        .concat(Source.single(ChunkStreamPart("This is the last chunk!")))

      HttpResponse(StatusCodes.OK, entity = Chunked(ContentTypes.`text/plain(UTF-8)`, responseChunks))

    case HttpRequest(_, Uri(_, _, Path(`exceptionPath`), _, _), _, _, _) =>
      throw new IllegalArgumentException("This path is supposed to throw this exception!")

    case _ => HttpResponse(StatusCodes.NotFound, entity = "Path not found!")
  }
}
