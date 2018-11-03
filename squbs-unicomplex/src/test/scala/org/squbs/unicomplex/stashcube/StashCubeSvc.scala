package org.squbs.unicomplex.stashcube

import akka.actor.{Actor, Stash}
import spray.http.HttpMethods._
import spray.http.{HttpRequest, HttpResponse, HttpEntity}

import scala.collection.mutable.ListBuffer

/**
 * Created by jiamzhang on 2015/1/4.
 */
class StashCubeSvc extends Actor with Stash {

	private val msgList = new ListBuffer[String]()

	override def receive = {
		case req: HttpRequest if req.method == POST =>
			stash()
		case req: HttpRequest if req.method == PUT =>
			context.become(report)
			unstashAll()
		case req: HttpRequest if req.method == GET =>
			val resp = HttpResponse(entity = msgList.toSeq.toString())
			sender() ! resp
	}

	def report: Receive = {
		case req: HttpRequest if req.method == POST =>
			msgList.append(req.entity.asString)
		case req: HttpRequest if req.method == GET =>
			val resp = HttpResponse(entity = msgList.toSeq.toString())
			sender() ! resp
	}
}
