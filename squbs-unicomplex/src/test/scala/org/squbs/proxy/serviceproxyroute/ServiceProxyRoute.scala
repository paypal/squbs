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
        reqCtx.copy(response = npr.copy(data = rrr.copy(headers = RawHeader("dummyRespHeader", reqCtx.attribute[String]("key1").getOrElse("Unknown")) :: rrr.headers)))
      case other => reqCtx
    }
    val promise = Promise[RequestContext]()
    promise.success(newCtx)
    promise.future
  }
}


