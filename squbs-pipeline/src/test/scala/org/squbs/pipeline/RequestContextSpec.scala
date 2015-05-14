package org.squbs.pipeline

import akka.actor.{Actor, ActorSystem}
import akka.testkit.{TestActorRef, TestKit}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import spray.http._

/**
 * Created by jiamzhang on 2015/3/4.
 */
class RequestContextSpec extends TestKit(ActorSystem("RequestContextSpecSys")) with FlatSpecLike with Matchers with BeforeAndAfterAll {

	override def afterAll {
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

	"RequestContext" should "parse and wrap the request correctly" in {
		val req = HttpRequest()
		val ctx = RequestContext(req, false, attributes = Map("aaa" -> "dummy", "ccc" -> null))
		ctx.attribute[String]("aaa") shouldBe Some("dummy")
		ctx.attribute[Int]("bbb") shouldBe None
		ctx.attribute[String]("ccc") shouldBe None

		ctx.payload shouldBe req

		val ctx1 = RequestContext(req, true)
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
