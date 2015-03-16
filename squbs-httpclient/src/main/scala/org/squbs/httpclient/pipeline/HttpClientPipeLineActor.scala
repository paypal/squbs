package org.squbs.httpclient.pipeline

import akka.actor._
import akka.pattern._
import org.squbs.httpclient.endpoint.Endpoint
import spray.client.pipelining.SendReceive
import spray.http.{ChunkedRequestStart, HttpRequest, HttpResponse}
import HttpClientContextUtils._
import org.squbs.proxy.{SimplePipelineConfig, SimpleProcessor}
import org.squbs.pipeline.{PipelineMgr, RequestContext}

/**
 * Created by jiamzhang on 2015/3/6.
 */
class HttpClientPipeLineActor(endpoint: Endpoint, pipelineConf: SimplePipelineConfig, target: SendReceive) extends Actor with ActorLogging {

	override def receive = {
		case request: HttpRequest =>
			val responder = sender()
			val targetAgent = context.actorOf(Props(classOf[HttpClientPipeLineTargetActor], target))
			val pipeproxy = PipelineMgr(context.system).getPipeLine(new SimpleProcessor(pipelineConf), targetAgent, responder)
			context.watch(pipeproxy)
			pipeproxy ! RequestContext(request) +> endpoint

		case request: ChunkedRequestStart =>
			val responder = sender()
			val targetAgent = context.actorOf(Props(classOf[HttpClientPipeLineTargetActor], target))
			val pipeproxy = PipelineMgr(context.system).getPipeLine(new SimpleProcessor(pipelineConf), targetAgent, responder)
			context.watch(pipeproxy)
			pipeproxy ! RequestContext(request.request, true) +> endpoint

		case Terminated(actor) =>
			log.debug("The actor " + actor + " is terminated, stop myself")
			context.stop(self)
	}
}

class HttpClientPipeLineTargetActor(target: SendReceive) extends Actor with ActorLogging {
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
