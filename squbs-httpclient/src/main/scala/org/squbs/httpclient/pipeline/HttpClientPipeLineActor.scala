package org.squbs.httpclient.pipeline

import akka.actor._
import akka.pattern._
import org.squbs.httpclient.endpoint.Endpoint
import spray.client.pipelining.SendReceive
import spray.http.{ChunkedRequestStart, HttpRequest, HttpResponse}
import HttpClientContextUtils._
import org.squbs.proxy.{SimplePipelineConfig, SimpleProcessor}
import org.squbs.pipeline.{PipelineProcessorActor, PipelineMgr, RequestContext}

/**
 * Created by jiamzhang on 2015/3/6.
 */
class HttpClientPipelineActor(endpoint: Endpoint, pipelineConf: SimplePipelineConfig, target: SendReceive) extends Actor with ActorLogging {

	override def receive = {
		case request: HttpRequest =>
			val responder = sender()
			val targetAgent = context.actorOf(Props(classOf[HttpClientPipelineTargetActor], target))
			val pipeproxy = context.actorOf(Props(classOf[PipelineProcessorActor], targetAgent, responder, SimpleProcessor(pipelineConf)))
			context.watch(pipeproxy)
			pipeproxy ! RequestContext(request) +> endpoint

		case request: ChunkedRequestStart =>
			val responder = sender()
			val targetAgent = context.actorOf(Props(classOf[HttpClientPipelineTargetActor], target))
			val pipeproxy = context.actorOf(Props(classOf[PipelineProcessorActor], targetAgent, responder, SimpleProcessor(pipelineConf)))
			context.watch(pipeproxy)
			pipeproxy ! RequestContext(request.request, true) +> endpoint

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
