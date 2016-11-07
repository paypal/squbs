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

package org.squbs.httpclient.pipeline.impl

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import org.squbs.pipeline.RequestContext
import spray.http.HttpHeaders.RawHeader
import spray.http.{HttpHeader, HttpRequest}

class RequestHeaderHandlerSpec extends TestKit(ActorSystem("RequestHeaderHandlerSpecSys"))
with ImplicitSender with FlatSpecLike with Matchers with BeforeAndAfterAll {

	override def afterAll() {
		system.shutdown()
	}

  "RequestAddHeaderHandler" should "support to add HttpHeader in HttpRequest" in {
    val httpRequest = HttpRequest()
    val httpHeader = RawHeader("test-name", "test-value")
    val handler = new RequestAddHeaderHandler(httpHeader)
		val agentActor = system.actorOf(Props(classOf[HandlerAgentActor], handler))

		agentActor ! RequestContext(httpRequest)

		val updateHttpRequest = expectMsgType[RequestContext].request
		updateHttpRequest.headers should have size 1
    updateHttpRequest.headers.head shouldBe httpHeader
  }

  "RequestRemoveHeaderHandler" should "support to remove HttpHeader in HttpRequest" in {
    val httpHeader1 = RawHeader("name1", "value1")
    val httpHeader2 = RawHeader("name2", "value2")
    val httpRequest = HttpRequest(headers = List[HttpHeader](httpHeader1, httpHeader2))
    val handler = new RequestRemoveHeaderHandler(httpHeader1)

		val agentActor = system.actorOf(Props(classOf[HandlerAgentActor], handler))

		agentActor ! RequestContext(httpRequest)
		val updateHttpRequest = expectMsgType[RequestContext].request
    updateHttpRequest.headers should have size 1
    updateHttpRequest.headers.head shouldBe httpHeader2
  }

  "RequestUpdateHeaderHandler" should "support to update HttpHeader in HttpRequest" in {
    val httpHeader1 = RawHeader("name1", "value1")
    val httpHeader2 = RawHeader("name1", "value2")
    val httpRequest = HttpRequest(headers = List[HttpHeader](httpHeader1))
    val handler = new RequestUpdateHeaderHandler(httpHeader2)

		val agentActor = system.actorOf(Props(classOf[HandlerAgentActor], handler))

		agentActor ! RequestContext(httpRequest)
		val updateHttpRequest = expectMsgType[RequestContext].request
    updateHttpRequest.headers should have size 1
    updateHttpRequest.headers.head shouldBe httpHeader2
  }

  "RequestUpdateHeadersHandler" should "support to update HttpHeader in HttpRequest" in {
    val httpHeader1 = RawHeader("name1", "value1")
    val httpHeader2 = RawHeader("name2", "value2")
    val httpHeader1New = RawHeader("name1", "value11")
    val httpHeader3 = RawHeader("name3", "value3")
    val httpRequest = HttpRequest(headers = List[HttpHeader](httpHeader1, httpHeader2))
    val handler = new RequestUpdateHeadersHandler(List[HttpHeader](httpHeader1New, httpHeader3))

    val agentActor = system.actorOf(Props(classOf[HandlerAgentActor], handler))

    agentActor ! RequestContext(httpRequest)
    val updateHttpRequest = expectMsgType[RequestContext].request
    updateHttpRequest.headers should have size 3
    updateHttpRequest.headers.find(_.name == "name1").get.value should be("value11")
    updateHttpRequest.headers.find(_.name == "name2").get.value should be("value2")
    updateHttpRequest.headers.find(_.name == "name3").get.value should be("value3")
  }
}
