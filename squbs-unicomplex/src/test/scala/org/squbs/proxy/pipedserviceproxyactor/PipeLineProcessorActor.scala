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
				case other => HttpResponse(NotFound, "No custom header found")
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

class confhandler2 extends Handler {
	override def process(reqCtx: RequestContext)(implicit executor: ExecutionContext): Future[RequestContext] = {
		Future {
			val resp = reqCtx.response match {
				case NormalResponse(rp) =>
					val httpresp = rp.copy(headers = HttpHeaders.RawHeader("confhandler2", "PayPal") :: rp.headers)
					reqCtx.response.asInstanceOf[NormalResponse].update(httpresp)
				case e:ExceptionalResponse =>
					val httperesp = e.response.copy(headers = HttpHeaders.RawHeader("confhandler2", "PayPal") :: e.response.headers)
					reqCtx.response.asInstanceOf[ExceptionalResponse].copy(response = httperesp)
			}
			reqCtx.copy(response = resp)
		}
	}
}

