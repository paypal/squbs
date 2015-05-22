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
package org.squbs.httpclient.pipeline.impl

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import org.squbs.pipeline.{NormalResponse, RequestContext}
import spray.http.HttpHeaders.RawHeader
import spray.http.{HttpHeader, HttpRequest, HttpResponse}

class ResponseHeaderHandlerSpec extends TestKit(ActorSystem("ResponseHeaderHandlerSpec")) with ImplicitSender with FlatSpecLike with Matchers with BeforeAndAfterAll {

	override def afterAll() {
		system.shutdown()
	}

   "ResponseAddHeaderHandler" should "support to add HttpHeader in HttpRequest" in {
     val httpResponse = HttpResponse()
     val httpHeader = RawHeader("test-name", "test-value")
     val handler = new ResponseAddHeaderHandler(httpHeader)

		 val agentActor = system.actorOf(Props(classOf[HandlerAgentActor], handler))
     agentActor ! RequestContext(HttpRequest(), isChunkRequest = false, NormalResponse(httpResponse))
		 val updateHttpResponse = expectMsgType[RequestContext].response match {
			 case n@NormalResponse(resp) => resp
			 case _ => httpResponse
		 }
     updateHttpResponse.headers should have size 1
     updateHttpResponse.headers.head shouldBe httpHeader
   }

   "ResponseRemoveHeaderHandler" should "support to remove HttpHeader in HttpRequest" in {
     val httpHeader1 = RawHeader("name1", "value1")
     val httpHeader2 = RawHeader("name2", "value2")
     val httpResponse = HttpResponse(headers = List[HttpHeader](httpHeader1, httpHeader2))
     val handler = new ResponseRemoveHeaderHandler(httpHeader1)
		 val agentActor = system.actorOf(Props(classOf[HandlerAgentActor], handler))
		 agentActor ! RequestContext(HttpRequest(), isChunkRequest = false, NormalResponse(httpResponse))
		 val updateHttpResponse = expectMsgType[RequestContext].response match {
			 case n@NormalResponse(resp) => resp
			 case _ => httpResponse
		 }
     updateHttpResponse.headers should have size 1
     updateHttpResponse.headers.head shouldBe httpHeader2
   }

   "ResponseUpdateHeaderHandler" should "support to update HttpHeader in HttpRequest" in {
     val httpHeader1 = RawHeader("name1", "value1")
     val httpHeader2 = RawHeader("name1", "value2")
     val httpResponse = HttpResponse(headers = List[HttpHeader](httpHeader1))
     val handler = new ResponseUpdateHeaderHandler(httpHeader2)
		 val agentActor = system.actorOf(Props(classOf[HandlerAgentActor], handler))
		 agentActor ! RequestContext(HttpRequest(), isChunkRequest = false, NormalResponse(httpResponse))
		 val updateHttpResponse = expectMsgType[RequestContext].response match {
			 case n@NormalResponse(resp) => resp
			 case _ => httpResponse
		 }
     updateHttpResponse.headers should have size 1
     updateHttpResponse.headers.head shouldBe httpHeader2
   }
 }
