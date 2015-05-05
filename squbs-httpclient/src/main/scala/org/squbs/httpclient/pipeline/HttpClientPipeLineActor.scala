package org.squbs.httpclient.pipeline

import akka.actor._
import akka.pattern._
import org.squbs.httpclient.endpoint.Endpoint
import org.squbs.httpclient.pipeline.HttpClientContextUtils._
import org.squbs.pipeline.{PipelineProcessorActor, RequestContext}
import org.squbs.proxy.{PipelineResolverRegistry, SimplePipelineConfig, SimpleProcessor}
import spray.client.pipelining.SendReceive
import spray.http._

/**
 * Created by jiamzhang on 2015/3/6.
 */
class HttpClientPipelineActor(clientName: String, pipelineConf: SimplePipelineConfig, target: SendReceive) extends Actor with ActorLogging {

  val pipeplineResolverRegistry = PipelineResolverRegistry(context.system)

	override def receive = {
		case request: HttpRequest =>
      val responder = sender()
      val targetAgent = context.actorOf(Props(classOf[HttpClientPipelineTargetActor], target))
      pipeplineResolverRegistry.default.resolve(pipelineConf) match {
        case None => targetAgent tell(request, responder)
        case Some(proc) =>
          val pipeproxy = context.actorOf(Props(classOf[PipelineProcessorActor], targetAgent, responder, proc))
          context.watch(pipeproxy)
          pipeproxy ! RequestContext(request) +> ("HttpClient.name" -> clientName)
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
