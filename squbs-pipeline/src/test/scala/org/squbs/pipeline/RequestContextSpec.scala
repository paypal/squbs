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

package org.squbs.pipeline

import akka.actor.{Actor, ActorSystem}
import akka.testkit.{TestActorRef, TestKit}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import spray.http.HttpHeaders.RawHeader
import spray.http._

class RequestContextSpec extends TestKit(ActorSystem("RequestContextSpecSys")) with FlatSpecLike with Matchers with BeforeAndAfterAll {

	override def afterAll() {
		system.shutdown()
	}

  "RequestContext" should "handle attributes correctly" in {
    val req = HttpRequest()
    val ctx = RequestContext(req)

    ctx.attributes.size should be(0)

    val ctx1 = ctx +> ("key1" -> "value1")
    ctx1.attribute("key1") should be(Some("value1"))

    val ctx2 = ctx1 +> ("key2" -> 1) +> ("key3"-> new Exception("BadMan"))
    ctx2.attribute("key2") should be(Some(1))
    ctx2.attribute[Exception]("key3").get.getMessage should be("BadMan")
    ctx2.attribute("key1") should be(Some("value1"))

    val ctx3 = ctx2 -> ("key1", "key5")
    ctx3.attributes.size should be(2)
    ctx3.attribute("key2") should be(Some(1))
    ctx3.attribute[Exception]("key3").get.getMessage should be("BadMan")

  }

  "RequestContext" should "handle headers correctly" in {
    val ctx = RequestContext(HttpRequest())

    val ctx1 = ctx.addRequestHeader(RawHeader("h11","v11"))
    ctx1.request.headers.contains(RawHeader("h11","v11")) should be(true)

    val ctx2 = ctx.addRequestHeaders(RawHeader("h11","v11"), RawHeader("h22","v22"))
    ctx2.request.headers.contains(RawHeader("h11","v11")) should be(true)
    ctx2.request.headers.contains(RawHeader("h22","v22")) should be(true)

    val ctx3 = ctx.copy(response = NormalResponse(HttpResponse())).addResponseHeader(RawHeader("h33","v33"))
    NormalResponse.unapply(ctx3.response.asInstanceOf[NormalResponse]).get.headers.contains(RawHeader("h33","v33")) should be (true)

  }

	"RequestContext" should "parse and wrap the request correctly" in {
		val req = HttpRequest()
		val ctx = RequestContext(req, isChunkRequest = false, attributes = Map("aaa" -> "dummy", "ccc" -> null))
		ctx.attribute[String]("aaa") shouldBe Some("dummy")
		ctx.attribute[Int]("bbb") shouldBe None
		ctx.attribute[String]("ccc") shouldBe None

		ctx.payload shouldBe req

		val ctx1 = RequestContext(req, isChunkRequest = true)
		ctx1.payload shouldBe ChunkedRequestStart(req)
	}

	"ExceptionalResponse" should "correctly apply and parse the config" in {
		val exp1 = ExceptionalResponse()
		exp1.response shouldBe HttpResponse(status = StatusCodes.InternalServerError, entity = "Service Error!")
		exp1.cause shouldBe None
		exp1.original shouldBe None

		val dummyerr = new RuntimeException("dummy error")
		val exp2 = ExceptionalResponse(dummyerr)
		exp2.response shouldBe HttpResponse(status = StatusCodes.InternalServerError, entity = "dummy error")
		exp2.cause shouldBe Some(dummyerr)
		exp2.original shouldBe None

		val origin = NormalResponse(HttpResponse(StatusCodes.OK, entity = "hello world"))
		val exp3 = ExceptionalResponse(dummyerr, Some(origin))
		exp3.response shouldBe HttpResponse(status = StatusCodes.InternalServerError, entity = "dummy error")
		exp3.cause shouldBe Some(dummyerr)
		exp3.original shouldBe Some(origin)
	}

	"NormalResponse" should "correctly apply and parse the config" in {
		val resp = HttpResponse(StatusCodes.OK)
		val resp1 = HttpResponse(StatusCodes.NotFound)
		val n1 = NormalResponse(resp)
		n1.data shouldBe resp
		n1.responseMessage shouldBe resp

		n1.update(resp1).data shouldBe resp1
		NormalResponse.unapply(n1) shouldBe Some(resp)

		val n2 = NormalResponse(ChunkedResponseStart(resp))
		n2.data shouldBe ChunkedResponseStart(resp)
		n2.update(resp1).data shouldBe ChunkedResponseStart(resp1)

		n2.update(ChunkedResponseStart(resp1)).data shouldBe ChunkedResponseStart(resp1)
		NormalResponse.unapply(n2) shouldBe Some(resp)

		val n3 = NormalResponse(MessageChunk("hellochunk"))
		n3.data shouldBe MessageChunk("hellochunk")

		a [IllegalArgumentException] should be thrownBy n3.update(resp1)
		NormalResponse.unapply(n3) shouldBe None

		val n4 = NormalResponse(ChunkedMessageEnd("helloend"))
		n4.data shouldBe ChunkedMessageEnd("helloend")

		val actor = TestActorRef[ContextDummyActor]
		val n5 = NormalResponse(Confirmed(resp, "OK"), actor)
		n5.data shouldBe resp
		n5.responseMessage shouldBe Confirmed(resp, AckInfo("OK", actor))
		n5.update(resp1).data shouldBe resp1
	}
}

class ContextDummyActor extends Actor {
	override def receive = {
		case any =>
	}
}
