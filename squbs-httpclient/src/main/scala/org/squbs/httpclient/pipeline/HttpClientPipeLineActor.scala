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
package org.squbs.httpclient.pipeline

import akka.actor._
import akka.pattern.pipe
import org.squbs.httpclient.endpoint.Endpoint
import org.squbs.pipeline.{PipelineProcessorActor, RequestContext}
import org.squbs.proxy.{PipelineResolverRegistry, SimplePipelineConfig}
import spray.client.pipelining.SendReceive
import spray.http._

class HttpClientPipelineActor(clientName: String, endpoint: Endpoint, pipelineConf: SimplePipelineConfig, target: SendReceive) extends Actor with ActorLogging {

  val pipeplineResolverRegistry = PipelineResolverRegistry(context.system)

  override def receive = {
    case request: HttpRequest =>
      val responder = sender()
      val targetAgent = context.actorOf(Props(classOf[HttpClientPipelineTargetActor], target))
      pipeplineResolverRegistry.getResolver(clientName).getOrElse(pipeplineResolverRegistry.default).resolve(pipelineConf) match {
        case None => targetAgent tell(request, responder)
        case Some(proc) =>
          val pipeproxy = context.actorOf(Props(classOf[PipelineProcessorActor], targetAgent, responder, proc))
          context.watch(pipeproxy)
          pipeproxy ! RequestContext(request) +>(("HttpClient.name" -> clientName), ("HttpClient.Endpoint" -> endpoint))
      }


    case request: ChunkedRequestStart =>
      // not supported yet
      sender ! HttpResponse(StatusCodes.InternalServerError, HttpEntity("Chunked request is not supported yet"))
    //			val responder = sender()
    //			val targetAgent = context.actorOf(Props(classOf[HttpClientPipelineTargetActor], target))
    //			val pipeproxy = context.actorOf(Props(classOf[PipelineProcessorActor], targetAgent, responder, SimpleProcessor(pipelineConf)))
    //			context.watch(pipeproxy)
    //			pipeproxy ! RequestContext(request.request, true) +> endpoint

    case Terminated(actor) =>
      log.debug("The actor " + actor + " is terminated, stop myself")
      context.stop(self)
  }
}

class HttpClientPipelineTargetActor(target: SendReceive) extends Actor with ActorLogging {
  import context._
  private var client: ActorRef = ActorRef.noSender

  override def receive = {
    case request: HttpRequest =>
      client = sender()
      target(request) pipeTo self

    case response: HttpResponse =>
      client ! response
  }
}
