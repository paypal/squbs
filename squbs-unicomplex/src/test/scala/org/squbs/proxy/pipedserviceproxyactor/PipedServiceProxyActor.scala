package org.squbs.proxy.pipedserviceproxyactor

import org.squbs.unicomplex.WebContext
import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import spray.http.StatusCodes._
import spray.http._
import spray.http.HttpMethods._
import com.typesafe.config.Config
import org.squbs.proxy._
import scala.concurrent.{Promise, Future}
import spray.http.HttpRequest
import spray.http.HttpResponse
import org.squbs.proxy.RequestContext
import spray.http.HttpHeaders.RawHeader
import org.squbs.proxy.PipeLineConfig
import scala.Some

/**
 * Created by lma on 14-10-13.
 */
class PipedServiceProxyActor extends Actor with WebContext with ActorLogging {

  def receive = {


    case req@HttpRequest(GET, Uri.Path("/pipedserviceproxyactor/msg/hello"), _, _, _) =>
      val customHeader1 = req.headers.find(h => h.name.equals("dummyReqHeader1"))
      val customHeader2 = req.headers.find(h => h.name.equals("dummyReqHeader2"))
      val output = (customHeader1, customHeader2) match {
        case (Some(h1), Some(h2)) => h1.value + h2.value
        case other => "No custom header found"
      }
      sender() ! HttpResponse(OK, output)


  }

}


class DummyPipedServiceProxyForActor(settings: Option[Config], hostActor: ActorRef) extends PipedServiceProxy(settings, hostActor) {

  def createPipeConfig(): PipeLineConfig = {
    PipeLineConfig(Seq(RequestHandler1, RequestHandler2), Seq(ResponseHandler1, ResponseHandler2))
  }

  object RequestHandler1 extends PipeHandler {
    def process(reqCtx: RequestContext): Future[RequestContext] = {
      val newreq = reqCtx.request.copy(headers = RawHeader("dummyReqHeader1", "PayPal") :: reqCtx.request.headers)
      Promise.successful(reqCtx.copy(request = newreq, attributes = reqCtx.attributes + (("key1" -> "CDC")))).future
    }
  }

  object RequestHandler2 extends PipeHandler {
    def process(reqCtx: RequestContext): Future[RequestContext] = {
      val newreq = reqCtx.request.copy(headers = RawHeader("dummyReqHeader2", "eBay") :: reqCtx.request.headers)
      Promise.successful(reqCtx.copy(request = newreq, attributes = reqCtx.attributes + (("key2" -> "CCOE")))).future
    }
  }

  object ResponseHandler1 extends PipeHandler {
    def process(reqCtx: RequestContext): Future[RequestContext] = {
      val newCtx = reqCtx.response match {
        case npr@NormalProxyResponse(_, _, _, rrr@(_: HttpResponse)) =>
          reqCtx.copy(response = npr.copy(data = rrr.copy(headers = RawHeader("dummyRespHeader1", reqCtx.getAttribute[String]("key1").getOrElse("Unknown")) :: rrr.headers)))
        case other => reqCtx
      }
      Promise.successful(newCtx).future
    }
  }

  object ResponseHandler2 extends PipeHandler {
    def process(reqCtx: RequestContext): Future[RequestContext] = {
      val newCtx = reqCtx.response match {
        case npr@NormalProxyResponse(_, _, _, rrr@(_: HttpResponse)) =>
          reqCtx.copy(response = npr.copy(data = rrr.copy(headers = RawHeader("dummyRespHeader2", reqCtx.getAttribute[String]("key2").getOrElse("Unknown")) :: rrr.headers)))
        case other => reqCtx
      }
      Promise.successful(newCtx).future
    }
  }

}




