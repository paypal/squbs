package org.squbs.httpclient.pipeline.impl

import akka.actor.Actor
import org.squbs.pipeline.{RequestContext, Handler}
import akka.pattern.pipe

/**
 * Created by jiamzhang on 2015/3/6.
 */
class HandlerAgentActor(handler: Handler) extends Actor {
	import context.dispatcher

	override def receive = {
		case ctx: RequestContext =>
			val client = sender()
			handler.process(ctx) pipeTo client
	}
}

