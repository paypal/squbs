/*
 * Copyright 2017 PayPal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.squbs.unicomplex.stashcube

import org.apache.pekko.actor.{Actor, Stash}
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.util.ByteString

import scala.collection.mutable.ListBuffer

class StashCubeSvc extends Actor with Stash {

	private val msgList = new ListBuffer[String]()
  import context.{dispatcher, system}

	override def receive: Receive = {
		case req@HttpRequest(HttpMethods.POST, _, _, _, _) =>
			stash()
			val resp = HttpResponse(status = StatusCodes.Accepted, entity = HttpEntity("Stashed away!"))
      sender() ! resp
		case req@HttpRequest(HttpMethods.PUT, _, _, _, _) =>
			context.become(report)
      val resp = HttpResponse(status = StatusCodes.Created, entity = HttpEntity("Un-stashed"))
      sender() ! resp
      unstashAll()
		case req@HttpRequest(HttpMethods.GET, _, _, _, _) =>
			val resp = HttpResponse(entity = msgList.toSeq.toString())
			sender() ! resp
	}

	def report: Receive = {
		case req@HttpRequest(HttpMethods.POST, _, _, _, _) =>
			req.entity.dataBytes.runFold(ByteString(""))(_ ++ _) map(_.utf8String) foreach(msgList.append(_))
		case req@HttpRequest(HttpMethods.GET, _, _, _, _) =>
			val resp = HttpResponse(entity = msgList.toSeq.toString())
			sender() ! resp
	}
}
