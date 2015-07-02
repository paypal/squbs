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

package org.squbs.proxy

import akka.actor.ActorContext
import org.scalatest.concurrent.AsyncAssertions.Waiter
import org.scalatest.{FlatSpecLike, Matchers}
import org.squbs.pipeline.{Handler, NormalResponse, RequestContext}
import spray.http.{HttpRequest, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

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
