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

import akka.actor.{Props, ActorContext}
import akka.util.Timeout
import com.typesafe.config.Config
import akka.pattern.ask
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

class PipeLineProcessor(reqPipe: PipeLineConfig, respPipe: PipeLineConfig) extends ServiceProxyProcessor {
	//inbound processing
	def inbound(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext): Future[RequestContext] = {
		reqPipe.handlers.foldLeft(Future.successful(reqCtx)) { (ctx, handler) => ctx flatMap (handler.process(_))}
	}

	//outbound processing
	def outbound(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext): Future[RequestContext] = {
		respPipe.handlers.foldLeft(Future.successful(reqCtx)) { (ctx, handler) => ctx.flatMap(handler.process(_))}
	}
}

object PipeLineProcessor {
	def empty: PipeLineProcessor = new PipeLineProcessor(PipeLineConfig.empty, PipeLineConfig.empty)
}

class PipeLineProcessorFactory extends ServiceProxyProcessorFactory {
	private implicit val timeout = Timeout(1 seconds)

	def create(settings: Option[Config])(implicit context: ActorContext): ServiceProxyProcessor = {
		settings match {
			case Some(conf) =>
				val loader = context.actorOf(Props(classOf[PipeConfigLoader]))
				val result = Await.result((loader ? conf).mapTo[PipeConfigInfo], 1 seconds)
				new PipeLineProcessor(result.reqConf, result.respConf)
			case None => PipeLineProcessor.empty
		}
	}
}
