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

package org.squbs.proxy.pipedserviceproxyactor

import akka.actor.{Actor, ActorLogging, ActorRefFactory}
import com.typesafe.config.Config
import org.squbs.pipeline._
import org.squbs.unicomplex.WebContext
import spray.http.StatusCodes._
import spray.http.{HttpHeaders, HttpRequest, HttpResponse}

import scala.concurrent.Future

class PipelineProcessorActor extends Actor with WebContext with ActorLogging {

	def receive = {
		case req: HttpRequest =>
			val customHeader1 = req.headers.find(h => h.name.equals("confhandler1"))
			val response = customHeader1 match {
				case Some(h) => HttpResponse(OK, "Found conf handler", HttpHeaders.RawHeader("found", "true") :: req.headers)
				case other => HttpResponse(OK, "No custom header found")
			}

			sender() ! response
	}
}

class confhandler1 extends Handler with HandlerFactory{
	override def process(reqCtx: RequestContext)(implicit context: ActorRefFactory): Future[RequestContext] = {
		import context.dispatcher
		Future {
			reqCtx.copy(request = reqCtx.request.copy(headers = HttpHeaders.RawHeader("confhandler1", "eBay") :: reqCtx.request.headers))
		}
	}

  override def create(config: Option[Config])(implicit actorRefFactory: ActorRefFactory): Option[Handler] = Some(this)
}

class confhandlerEmpty extends Handler with HandlerFactory{
	override def process(reqCtx: RequestContext)(implicit context: ActorRefFactory): Future[RequestContext] = {
		import context.dispatcher
		Future { reqCtx }
	}

  override def create(config: Option[Config])(implicit actorRefFactory: ActorRefFactory): Option[Handler] = Some(this)
}

class confhandler2 extends Handler with HandlerFactory{
	override def process(reqCtx: RequestContext)(implicit context: ActorRefFactory): Future[RequestContext] = {
		import context.dispatcher
		Future {
			reqCtx.copy(attributes = reqCtx.attributes + ("confhandler2" -> "PayPal"))
		}
	}

  override def create(config: Option[Config])(implicit actorRefFactory: ActorRefFactory): Option[Handler] = Some(this)
}

class confhandler3 extends Handler with HandlerFactory{
	override def process(reqCtx: RequestContext)(implicit context: ActorRefFactory): Future[RequestContext] = {
    import context.dispatcher
		Future {
			val resp = (reqCtx.response, reqCtx.attribute[String]("confhandler2")) match {
				case (NormalResponse(rp), Some(v)) =>
          val attrHeaders = reqCtx.attributes.map{
            entry =>  HttpHeaders.RawHeader(entry._1 , entry._2.toString)
          }
					val httpresp = rp.copy(headers = HttpHeaders.RawHeader("confhandler3", "dummy") :: HttpHeaders.RawHeader("confhandler2", v) :: rp.headers ++ attrHeaders)
					reqCtx.response.asInstanceOf[NormalResponse].update(httpresp)
				case (NormalResponse(rp), None) =>
					val httpresp = rp.copy(headers = HttpHeaders.RawHeader("confhandler3", "dummy") :: rp.headers)
					reqCtx.response.asInstanceOf[NormalResponse].update(httpresp)
				case (e:ExceptionalResponse, Some(v)) =>
					val httperesp = e.response.copy(headers = HttpHeaders.RawHeader("confhandler3", "dummy") :: HttpHeaders.RawHeader("confhandler2", v) :: e.response.headers)
					reqCtx.response.asInstanceOf[ExceptionalResponse].copy(response = httperesp)
				case (e:ExceptionalResponse, None) =>
					val httperesp = e.response.copy(headers = HttpHeaders.RawHeader("confhandler3", "dummy") :: e.response.headers)
					reqCtx.response.asInstanceOf[ExceptionalResponse].copy(response = httperesp)
			}
			reqCtx.copy(response = resp)
		}
	}

  override def create(config: Option[Config])(implicit actorRefFactory: ActorRefFactory): Option[Handler] = Some(this)
}

