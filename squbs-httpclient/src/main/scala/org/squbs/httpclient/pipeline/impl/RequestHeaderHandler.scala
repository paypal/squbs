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

import scala.collection.mutable
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
  override def process(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext):
  Future[RequestContext] = {
		val origHdrKV = reqCtx.request.headers map { h => h.lowercaseName -> h }
		val newHdrKV = newHeaders map { h => h.lowercaseName -> h }
		val finalHeaders = (mutable.LinkedHashMap(origHdrKV: _*) ++ newHdrKV).values.toList
    Future.successful(reqCtx.copy(request = reqCtx.request.copy(headers = finalHeaders)))
  }
}

