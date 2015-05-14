package org.squbs.proxy

import akka.actor.ActorContext
import org.scalatest.concurrent.AsyncAssertions.Waiter
import org.scalatest.{FlatSpecLike, Matchers}
import org.squbs.pipeline.{Handler, NormalResponse, RequestContext}
import spray.http.{HttpRequest, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

/**
 * Created by lma on 2015/5/14.
 */
class SimpleProcessorSpec extends FlatSpecLike with Matchers {

  implicit val execCtx = scala.concurrent.ExecutionContext.Implicits.global
  "Simple processor by pass handler" should "work" in {

    val w = new Waiter
    val ctx = RequestContext(HttpRequest())

    val p1 = SimpleProcessor(SimplePipelineConfig(Seq(new Inbound2, new Inbound1), Seq.empty))
    p1.inbound(ctx)(execCtx, null).onComplete {
      result =>
        w {
          assert(result.isSuccess)
          result.get.attribute[String]("attr1") should be(Some("v1"))
          result.get.response shouldBe a[NormalResponse]
        }
        w.dismiss()
    }
    w.await()

    val p2 = SimpleProcessor(SimplePipelineConfig(Seq(new Inbound1, new Inbound2), Seq.empty))
    p2.inbound(ctx)(execCtx, null).onComplete {
      result =>
        w {
          assert(result.isSuccess)
          result.get.attribute[String]("attr1") should be(None)
          result.get.response shouldBe a[NormalResponse]
        }
        w.dismiss()
    }
    w.await()
  }
}

class Inbound1 extends Handler {
  override def process(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext): Future[RequestContext] = {
    Future.successful(reqCtx.copy(response = NormalResponse(HttpResponse())))
  }
}

class Inbound2 extends Handler {
  override def process(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext): Future[RequestContext] = {
    Future.successful(reqCtx +> ("attr1" -> "v1"))
  }
}
