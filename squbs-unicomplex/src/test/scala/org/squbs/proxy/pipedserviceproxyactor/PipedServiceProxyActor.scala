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
import spray.http.HttpHeaders.RawHeader
import spray.http.StatusCodes._
import spray.http.{HttpRequest, HttpResponse}

import scala.concurrent.Future

class PipedServiceProxyActor extends Actor with WebContext with ActorLogging {

  def receive = {
    case req: HttpRequest =>
      val customHeader1 = req.headers.find(h => h.name.equals("dummyReqHeader1"))
      val customHeader2 = req.headers.find(h => h.name.equals("dummyReqHeader2"))
      val response = (customHeader1, customHeader2) match {
        case (Some(h1), Some(h2)) => HttpResponse(OK, h1.value + h2.value, List(h1, h2))
				case (Some(h1), None) => HttpResponse(OK, h1.value, List(h1))
				case (None, Some(h2)) => HttpResponse(OK, h2.value, List(h2))
        case other => HttpResponse(OK, "No custom header found")
      }

      sender() ! response
  }
}


class DummyPipedProcessorFactoryForActor extends ProcessorFactory {

  def create(settings: Option[Config])(implicit actorRefFactory: ActorRefFactory): Option[Processor] = {
     Some(SimpleProcessor(SimplePipelineConfig(Seq(RequestHandler1,RequestHandler2), Seq(ResponseHandler1, ResponseHandler2))))
  }

  object RequestHandler1 extends Handler {
    def process(reqCtx: RequestContext)(implicit context: ActorRefFactory): Future[RequestContext] = {
      import context.dispatcher
      val newreq = reqCtx.request.copy(headers = RawHeader("dummyReqHeader1", "PayPal") :: reqCtx.request.headers)
      Future {
	      reqCtx.copy(request = newreq, attributes = reqCtx.attributes + ("key1" -> "CDC"))
      }
    }
  }

  object RequestHandler2 extends Handler {
    def process(reqCtx: RequestContext)(implicit context: ActorRefFactory): Future[RequestContext] = {
      val newreq = reqCtx.request.copy(headers = RawHeader("dummyReqHeader2", "eBay") :: reqCtx.request.headers)
      import context.dispatcher
      Future {
	      reqCtx.copy(request = newreq, attributes = reqCtx.attributes + ("key2" -> "CCOE"))
      }
    }
  }

  object ResponseHandler1 extends Handler {
    def process(reqCtx: RequestContext)(implicit context: ActorRefFactory): Future[RequestContext] = {
      import context.dispatcher
      val newCtx = reqCtx.response match {
        case nr@NormalResponse(r) =>
          reqCtx.copy(response = nr.update(r.copy(headers = RawHeader("dummyRespHeader1", reqCtx.attribute[String]("key1").getOrElse("Unknown")) :: r.headers)))

        case other => reqCtx
      }
      Future {
	      newCtx
      }
    }
  }

  object ResponseHandler2 extends Handler {
    def process(reqCtx: RequestContext)(implicit context: ActorRefFactory): Future[RequestContext] = {
      val newCtx = reqCtx.response match {
        case nr@NormalResponse(r) =>
          reqCtx.copy(response = nr.update(r.copy(headers = RawHeader("dummyRespHeader2", reqCtx.attribute[String]("key2").getOrElse("Unknown")) :: r.headers)))

        case other => reqCtx
      }
      import context.dispatcher
      Future { newCtx }
    }
  }
}
