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
import com.typesafe.config.Config
import org.squbs.pipeline._
import spray.http._
import spray.can.Http.RegisterChunkHandler
import spray.http.Confirmed
import scala.concurrent.Future
import scala.util.{Failure, Success}
import spray.http.ChunkedResponseStart
import spray.http.HttpResponse

class CubeProxyActor(processorName: String, target: ActorRef) extends Actor with ActorLogging {
	import context.dispatcher

	def receive = {
		case req: HttpRequest =>
			val client = sender()
			Future {
				handleRequest(RequestContext(req), client)
			}

		case crs: ChunkedRequestStart =>
			val client = sender()
			Future {
				handleRequest(RequestContext(crs.request, true), client)
			}

		//let underlying actor handle it
		case other =>
			target forward other
	}

  def handleRequest(requestCtx: RequestContext, responder: ActorRef) {
		try {
			val facade = PipelineMgr(context.system).getPipeline(processorName, target, responder)
			facade ! requestCtx
		} catch {
			case t: Throwable =>
				log.error(s"Fail to get the pipeline with processor $processorName.", t)
				responder ! HttpResponse(StatusCodes.InternalServerError, entity = "Internal Error Occurred")
		}
  }
}


