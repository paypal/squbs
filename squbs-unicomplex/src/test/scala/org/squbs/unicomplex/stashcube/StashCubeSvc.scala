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

package org.squbs.unicomplex.stashcube

import akka.actor.{Actor, Stash}
import spray.http.HttpMethods._
import spray.http.{HttpRequest, HttpResponse}

import scala.collection.mutable.ListBuffer

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
