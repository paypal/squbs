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

package org.squbs.httpclient.pipeline.impl

import akka.actor.ActorContext
import org.squbs.pipeline.{Handler, RequestContext}
import spray.http.HttpHeader
import scala.collection.parallel.mutable
import scala.concurrent.{ExecutionContext, Future}

class RequestAddHeaderHandler(httpHeader: HttpHeader) extends Handler {
	override def process(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext):
	    Future[RequestContext] = {
		val originalHeaders = reqCtx.request.headers
		Future {
			reqCtx.copy(request = reqCtx.request.copy(headers = originalHeaders :+ httpHeader))
		}
	}
}

class RequestRemoveHeaderHandler(httpHeader: HttpHeader) extends Handler {
	override def process(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext):
      Future[RequestContext] = {
		val originalHeaders = reqCtx.request.headers.groupBy[Boolean](_.name == httpHeader.name)
		Future {
			reqCtx.copy(request = reqCtx.request.copy(headers = originalHeaders.getOrElse(false, List.empty[HttpHeader])))
		}
	}
}

class RequestUpdateHeaderHandler(httpHeader: HttpHeader) extends Handler {
	override def process(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext):
      Future[RequestContext] = {
		val originalHeaders = reqCtx.request.headers.groupBy[Boolean](_.name == httpHeader.name)
		Future {
			reqCtx.copy(request =
        reqCtx.request.copy(headers = originalHeaders.getOrElse(false, List.empty[HttpHeader]) :+ httpHeader))
		}
	}
}

class RequestUpdateHeadersHandler(newHeaders: List[HttpHeader]) extends Handler {
  override def process(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext): Future[RequestContext] = {
    val originalHeaders = reqCtx.request.headers
    val newHeaderMap = scala.collection.mutable.Map.empty[String, HttpHeader]
    newHeaders.foreach {
      h => newHeaderMap.put(h.lowercaseName, h)
    }

    var finalHeaders: List[HttpHeader] = originalHeaders.map {
      h => newHeaderMap.remove(h.lowercaseName) match {
        case None => h
        case Some(newHeader) => newHeader
      }
    }

    if (newHeaderMap.size > 0) finalHeaders ++= newHeaderMap.values

    Future.successful(reqCtx.copy(request = reqCtx.request.copy(headers = finalHeaders)))
  }
}

