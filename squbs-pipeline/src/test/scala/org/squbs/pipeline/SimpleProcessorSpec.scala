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

import akka.actor.{Actor, ActorRefFactory, ActorSystem}
import akka.testkit.{TestActorRef, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.AsyncAssertions.Waiter
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import org.squbs.pipeline.Timeouts._
import spray.http.{HttpRequest, HttpResponse}

import scala.collection.JavaConversions._
import scala.concurrent.Future


class SimpleProcessorSpec extends TestKit(ActorSystem("SimpleProcessorSpec")) with FlatSpecLike
    with Matchers with BeforeAndAfterAll {

  val nullContext = TestActorRef[NullActor].underlyingActor.context

  override def afterAll(): Unit = system.shutdown()

  implicit val execCtx = scala.concurrent.ExecutionContext.Implicits.global

  "Simple processor related factory" should "work" in {
    SimpleProcessor.empty should be(SimpleProcessor(SimplePipelineConfig.empty))

    val factory = new SimpleProcessorFactory
    factory.create(Some(ConfigFactory.empty())) should be(Some(SimpleProcessor(SimplePipelineConfig.empty)))

    SimplePipelineResolver.create(SimplePipelineConfig.empty, None) should be(None)
    SimplePipelineResolver.create(SimplePipelineConfig(Seq(new Inbound1, new Inbound2), Seq.empty), None) should not be(None)
  }

  "Simple processor by pass handler" should "work" in {

    val w = new Waiter
    val ctx = RequestContext(HttpRequest())

    val p1 = SimpleProcessor(SimplePipelineConfig(Seq(new Inbound2, new Inbound1), Seq.empty))
    p1.inbound(ctx)(system).onComplete {
      result =>
        w {
          assert(result.isSuccess)
          result.get.attribute[String]("attr1") should be(Some("v1"))
          result.get.response shouldBe a[NormalResponse]
        }
        w.dismiss()
    }
    w.await(waitMax)

    val p2 = SimpleProcessor(SimplePipelineConfig(Seq(new Inbound1, new Inbound2), Seq.empty))
    p2.inbound(ctx)(system).onComplete {
      result =>
        w {
          assert(result.isSuccess)
          result.get.attribute[String]("attr1") should be(None)
          result.get.response shouldBe a[NormalResponse]
        }
        w.dismiss()
    }
    w.await(waitMax)
  }

  "SimplePipelineConfig" should "work" in {
    val config = SimplePipelineConfig.create(ConfigFactory.empty(), system)
    config.reqPipe.size should be(0)
    config.respPipe.size should be(0)

    val config2 = SimplePipelineConfig.create(Seq(new Inbound1, new Inbound2), Seq.empty[Handler])
    config2.reqPipe.size should be(2)

  }

}


class Inbound1 extends Handler {
  override def process(reqCtx: RequestContext)(implicit context: ActorRefFactory) =
    Future.successful(reqCtx.copy(response = NormalResponse(HttpResponse())))
}

class Inbound2 extends Handler {
  override def process(reqCtx: RequestContext)(implicit context: ActorRefFactory) =
    Future.successful(reqCtx +> ("attr1" -> "v1"))
}

class NullActor extends Actor {
  def receive = {
    case _ =>
  }
}
