package org.squbs.proxy.pipedserviceproxyactor

import akka.actor.{ActorLogging, Actor}
import org.squbs.proxy.{ExceptionalResponse, NormalResponse, RequestContext, Handler}
import org.squbs.unicomplex.WebContext
import spray.http.StatusCodes._
import spray.http.{HttpHeaders, HttpResponse, HttpRequest}

import scala.concurrent.{Future, ExecutionContext}

/**
 * Created by jiamzhang on 15/2/16.
 */
class PipeLineProcessorActor extends Actor with WebContext with ActorLogging {

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

class confhandler1 extends Handler {
	override def process(reqCtx: RequestContext)(implicit executor: ExecutionContext): Future[RequestContext] = {
		Future {
			reqCtx.copy(request = reqCtx.request.copy(headers = HttpHeaders.RawHeader("confhandler1", "eBay") :: reqCtx.request.headers))
		}
	}
}

class confhandlerEmpty extends Handler {
	override def process(reqCtx: RequestContext)(implicit executor: ExecutionContext): Future[RequestContext] = {
		Future { reqCtx }
	}
}

class confhandler2 extends Handler {
	override def process(reqCtx: RequestContext)(implicit executor: ExecutionContext): Future[RequestContext] = {
		Future {
			reqCtx.copy(attributes = reqCtx.attributes + ("confhandler2" -> "PayPal"))
		}
	}
}

class confhandler3 extends Handler {
	override def process(reqCtx: RequestContext)(implicit executor: ExecutionContext): Future[RequestContext] = {
		Future {
			val resp = (reqCtx.response, reqCtx.attribute[String]("confhandler2")) match {
				case (NormalResponse(rp), Some(v)) =>
					val httpresp = rp.copy(headers = HttpHeaders.RawHeader("confhandler3", "dummy") :: HttpHeaders.RawHeader("confhandler2", v) :: rp.headers)
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
}

