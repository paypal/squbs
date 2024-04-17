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

package org.squbs.unicomplex.streamSvc

import org.apache.pekko.http.scaladsl.server._
import org.squbs.unicomplex.RouteDefinition

class StreamSvc extends RouteDefinition {

  def route: Route =
    extractRequestContext { ctx =>
      implicit val materializer = ctx.materializer
      implicit val ec = ctx.executionContext

      path("file-upload") {

        var chunkCount = 0L
        var byteCount = 0L

        val future = ctx.request.entity.dataBytes.runForeach { b =>
          chunkCount += 1
          byteCount += b.length
        }

        onSuccess(future) { _ => complete(s"Chunk Count: $chunkCount ByteCount: $byteCount") }
      }
    }
}

