package org.squbs.proxy.serviceproxyroute

import org.squbs.unicomplex._
import spray.routing.Directives._
import spray.http.HttpEntity
import spray.http.MediaTypes._
import akka.actor.ActorRef
import com.typesafe.config.Config
import scala.concurrent.{Promise, Future}
import spray.http.HttpResponse
import spray.http.HttpHeaders.RawHeader
import scala.Some
import org.squbs.proxy.{NormalProxyResponse, SimpleServiceProxy, RequestContext}

/**
 * Created by lma on 14-10-13.
 */
class ServiceProxyRoute extends RouteDefinition with WebContext {
  def route = path("msg" / Segment) {
    param =>
      get {
        ctx =>
          val customHeader = ctx.request.headers.find(h => h.name.equals("dummyReqHeader"))
          val output = customHeader match {
            case None => "No custom header found"
            case Some(header) => header.value
          }
          ctx.responder ! HttpResponse(entity = HttpEntity(`text/plain`, param + output))
      }
  }
}

class DummyServiceProxyForRoute(settings: Option[Config], hostActor: ActorRef) extends SimpleServiceProxy(settings, hostActor) {


  def processRequest(reqCtx: RequestContext): Future[RequestContext] = {
    val newreq = reqCtx.request.copy(headers = RawHeader("dummyReqHeader", "eBay") :: reqCtx.request.headers)
    val promise = Promise[RequestContext]()
    promise.success(RequestContext(request = newreq, attributes = Map("key1" -> "CCOE")))
    promise.future
  }

  //outbound processing
  def processResponse(reqCtx: RequestContext): Future[RequestContext] = {
    val newCtx = reqCtx.response match {
      case npr@NormalProxyResponse(_, _, _, rrr@(_: HttpResponse)) =>
        reqCtx.copy(response = npr.copy(data = rrr.copy(headers = RawHeader("dummyRespHeader", reqCtx.getAttribute[String]("key1").getOrElse("Unknown")) :: rrr.headers)))
      case other => reqCtx
    }
    val promise = Promise[RequestContext]()
    promise.success(newCtx)
    promise.future
  }
}


