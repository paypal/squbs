/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the AUTHORS file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.squbs.proxy.pipedserviceproxyactor

import org.squbs.unicomplex.WebContext
import akka.actor.{ActorContext, ActorLogging, Actor}
import spray.http.StatusCodes._
import com.typesafe.config.Config
import org.squbs.proxy._
import scala.concurrent.{Promise, Future}
import spray.http.HttpRequest
import spray.http.HttpResponse
import org.squbs.proxy.RequestContext
import spray.http.HttpHeaders.RawHeader
import org.squbs.proxy.PipelineConfig
import scala.Some

class PipedServiceProxyActor extends Actor with WebContext with ActorLogging {

  def receive = {


    case req: HttpRequest =>
      val customHeader1 = req.headers.find(h => h.name.equals("dummyReqHeader1"))
      val customHeader2 = req.headers.find(h => h.name.equals("dummyReqHeader2"))
      val output = (customHeader1, customHeader2) match {
        case (Some(h1), Some(h2)) => h1.value + h2.value
        case other => "No custom header found"
      }
      sender() ! HttpResponse(OK, output)


  }

}


class DummyPipedServiceProxyProcessorFactoryForActor extends ServiceProxyProcessorFactory {

  def create(settings: Option[Config])(implicit context: ActorContext): ServiceProxyProcessor = {
    new PipedServiceProxyProcessor(PipelineConfig(Seq(RequestHandler1, RequestHandler2), Seq(ResponseHandler1, ResponseHandler2)))
  }

  object RequestHandler1 extends Handler {
    def process(reqCtx: RequestContext): Future[RequestContext] = {
      val newreq = reqCtx.request.copy(headers = RawHeader("dummyReqHeader1", "PayPal") :: reqCtx.request.headers)
      Promise.successful(reqCtx.copy(request = newreq, attributes = reqCtx.attributes + (("key1" -> "CDC")))).future
    }
  }

  object RequestHandler2 extends Handler {
    def process(reqCtx: RequestContext): Future[RequestContext] = {
      val newreq = reqCtx.request.copy(headers = RawHeader("dummyReqHeader2", "eBay") :: reqCtx.request.headers)
      Promise.successful(reqCtx.copy(request = newreq, attributes = reqCtx.attributes + (("key2" -> "CCOE")))).future
    }
  }

  object ResponseHandler1 extends Handler {
    def process(reqCtx: RequestContext): Future[RequestContext] = {

      val newCtx = reqCtx.response match {
        case nr@NormalResponse(r) =>
          reqCtx.copy(response = nr.update(r.copy(headers = RawHeader("dummyRespHeader1", reqCtx.attribute[String]("key1").getOrElse("Unknown")) :: r.headers)))

        case other => reqCtx
      }
      Promise.successful(newCtx).future
    }
  }

  object ResponseHandler2 extends Handler {
    def process(reqCtx: RequestContext): Future[RequestContext] = {
      val newCtx = reqCtx.response match {
        case nr@NormalResponse(r) =>
          reqCtx.copy(response = nr.update(r.copy(headers = RawHeader("dummyRespHeader2", reqCtx.attribute[String]("key2").getOrElse("Unknown")) :: r.headers)))

        case other => reqCtx
      }
      Promise.successful(newCtx).future
    }
  }


}




