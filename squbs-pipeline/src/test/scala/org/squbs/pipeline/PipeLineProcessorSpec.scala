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

import akka.actor._
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.typesafe.config.Config
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import spray.http.HttpHeaders.RawHeader
import spray.http.Uri.Path
import spray.http._

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

class PipeLineProcessorSpec extends TestKit(ActorSystem("PipelineProcessorSpecSys"))
with FlatSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll {

  override def afterAll() {
    system.shutdown()
  }


  "PipelineProcessor" should "processs the piepline correctly" in {
    val request = HttpRequest(HttpMethods.GET, Uri("http://localhost:9900/hello"))
    val ctx = RequestContext(request)
    val target = TestActorRef[DummyTarget]


    val processor = DummyProcessorFactory.create(None).get
    val processorActor = system.actorOf(Props(classOf[PipelineProcessorActor], target, self, processor))
    processorActor ! ctx

    val resp = expectMsgType[HttpResponse]
    val hs = resp.headers
    val pre = hs.find(_.name == "inbound")
    pre should not be None
    pre.get.value shouldBe "go"

    val post = hs.find(_.name == "outbound")
    post should not be None
    post.get.value shouldBe "go"
  }


  "PipelineProcessor" should "handle the exception from remote correctly" in {
    val request = HttpRequest(HttpMethods.GET, Uri("http://localhost:9900/error"))
    val ctx = RequestContext(request)
    val target = TestActorRef[DummyTarget]

    val processor = DummyProcessorFactory.create(None).get
    val processorActor = system.actorOf(Props(classOf[PipelineProcessorActor], target, self, processor))
    processorActor ! ctx

    val resp = expectMsgType[HttpResponse]
    resp.status should be(StatusCodes.InternalServerError)
    resp.headers find (_.name == "inbound") should matchPattern { case Some(h: HttpHeader) if h.value == "go" => }
    resp.headers find (_.name == "outbound") should matchPattern { case Some(h: HttpHeader) if h.value == "go" => }
    resp.headers find (_.name == "postoutbound") should matchPattern { case Some(h: HttpHeader) if h.value == "go" => }
  }


  "PipelineProcessor" should "return error if response in ctx didn't set correctly" in {
    val request = HttpRequest(HttpMethods.GET, Uri("http://localhost:9900/error"))
    val ctx = RequestContext(request, isChunkRequest = false)
    val processor = DummyProcessorFactory.create(None).get
    val processorActor = system.actorOf(Props(classOf[PipelineProcessorActor], Actor.noSender, self, processor))
    processorActor ! ctx

    val resp = expectMsgType[HttpResponse]
    resp should be(ExceptionalResponse.defaultErrorResponse.copy(headers = RawHeader("inbound", "go") :: RawHeader("postoutbound", "go") :: Nil))
  }

  "PipelineProcessor" should "forward unknown msg to client" in {
    val processor = DummyProcessorFactory.create(None).get
    val processorActor = system.actorOf(Props(classOf[PipelineProcessorActor], Actor.noSender, self, processor))

    object Unknown {}
    processorActor ! Unknown

    expectMsg(Unknown)
  }

  "PipelineProcessorActor" should "handle the chunk response correctly" in {
    val request = HttpRequest(HttpMethods.POST, Uri("http://localhost:9900/hello"))
    val ctx = RequestContext(request)
    val target = TestActorRef[DummyTarget]

    val processor = DummyProcessorFactory.create(None).get
    val processorActor = system.actorOf(Props(classOf[PipelineProcessorActor], target, self, processor))
    processorActor ! ctx

    val resp = expectMsgType[ChunkedResponseStart]
    expectMsgType[MessageChunk]
    expectMsgType[MessageChunk]
    expectMsgType[MessageChunk]
    expectMsgType[MessageChunk]
    expectMsgType[MessageChunk]
    expectMsgType[ChunkedMessageEnd]

    val hs = resp.response.headers
    val pre = hs.find(_.name == "inbound")
    pre should not be None
    pre.get.value shouldBe "go"

    val post = hs.find(_.name == "outbound")
    post should not be None
    post.get.value shouldBe "go"
  }

  "PipelineProcessorActor" should "handle the confirmed chunk response correctly" in {
    val request = HttpRequest(HttpMethods.PUT, Uri("http://localhost:9900/hello"))
    val ctx = RequestContext(request)
    val target = TestActorRef[DummyTarget]

    val processor = DummyProcessorFactory.create(None).get
    val processorActor = system.actorOf(Props(classOf[PipelineProcessorActor], target, self, processor))
    processorActor ! ctx

    val resp = expectMsgType[Confirmed]
    target ! DummyACK
    expectMsgType[Confirmed]
    target ! DummyACK

    expectMsgType[Confirmed]
    target ! DummyACK

    expectMsgType[Confirmed]
    target ! DummyACK

    expectMsgType[Confirmed]
    target ! DummyACK

    expectMsgType[Confirmed]
    target ! DummyACK

    expectMsgType[ChunkedMessageEnd]

    val hs = resp.messagePart.asInstanceOf[ChunkedResponseStart].response.headers
    val pre = hs.find(_.name == "inbound")
    pre should not be None
    pre.get.value shouldBe "go"

    val post = hs.find(_.name == "outbound")
    post should not be None
    post.get.value shouldBe "go"
  }

  "PipelineProcessorActor" should "handle exception from Processor correctly" in {
    val request = HttpRequest(HttpMethods.GET, Uri("http://localhost:9900/hello"))
    val ctx = RequestContext(request)
    val target = TestActorRef[DummyTarget]

    val phases = List("preInbound", "inbound", "postInbound", "preOutbound", "outbound")
    phases.foreach(p => {
      print("check:" + p)
      val processor = new ExceptionProcessor(List(p))
      val processorActor = system.actorOf(Props(classOf[PipelineProcessorActor], target, self, processor))
      processorActor ! ctx

      val res = expectMsgType[HttpResponse]
      res.status should be(StatusCodes.InternalServerError)
    })

  }

  "PipelineProcessorActor" should "handle exception from Processor for chunk response correctly" in {
    val request = HttpRequest(HttpMethods.PUT, Uri("http://localhost:9900/hello"))
    val ctx = RequestContext(request)
    val target = TestActorRef[DummyTarget]

    val phases = List("preOutbound", "outbound")
    phases.foreach(p => {
      print("check:" + p)
      val processor = new ExceptionProcessor(List(p))
      val processorActor = system.actorOf(Props(classOf[PipelineProcessorActor], target, self, processor))
      processorActor ! ctx

      val res = expectMsgType[HttpResponse]
      res.status should be(StatusCodes.InternalServerError)
    })

  }

  "Processor" should "handle request exception correctly" in {
    val processor = DummyProcessorFactory.create(None).get
    val request = HttpRequest(HttpMethods.PUT, Uri("http://localhost:9900/hello"))
    val ctx = RequestContext(request)
    val ex = new RuntimeException("test")
    val result = processor.onRequestError(ctx, ex)
    result.response should matchPattern { case r: ExceptionalResponse if r.cause.contains(ex) => }
  }

  "Processor" should "handle response exception correctly" in {
    val processor = DummyProcessorFactory.create(None).get
    val request = HttpRequest(HttpMethods.PUT, Uri("http://localhost:9900/hello"))
    val ctx = RequestContext(request)
    val ex = new RuntimeException("test")
    val result = processor.onResponseError(ctx, ex)
    result.response should matchPattern { case r: ExceptionalResponse if r.cause.contains(ex) && r.original.isEmpty =>}
  }

  "Processor" should "handle response exception with normal response correctly" in {
    val processor = DummyProcessorFactory.create(None).get
    val request = HttpRequest(HttpMethods.PUT, Uri("http://localhost:9900/hello"))
    val response = DirectResponse(HttpResponse())
    val ctx = RequestContext(request, isChunkRequest = false, response)

    val ex = new RuntimeException("test")
    val result = processor.onResponseError(ctx, ex)
    result.response should matchPattern {
      case r: ExceptionalResponse if r.cause.contains(ex) && r.original.contains(response) =>
    }
  }
}

case object DummyACK

class DummyTarget extends Actor {
  private val msgCache = new ListBuffer[MessageChunk]()

  override def receive = {
    case r: HttpRequest if r.uri.path == Path("/error") =>
      sender ! new RuntimeException("error")
    case r: HttpRequest if r.method == HttpMethods.GET =>
      sender() ! HttpResponse(StatusCodes.OK, entity = "DummyResponse")
    case r: HttpRequest if r.method == HttpMethods.POST =>
      sender() ! ChunkedResponseStart(HttpResponse(StatusCodes.OK, entity = "DummyChunkStart"))
      sender() ! MessageChunk("Chunk1")
      sender() ! MessageChunk("Chunk2")
      sender() ! MessageChunk("Chunk3")
      sender() ! MessageChunk("Chunk4")
      sender() ! MessageChunk("Chunk5")
      sender() ! ChunkedMessageEnd()
    case r: HttpRequest if r.method == HttpMethods.PUT =>
      msgCache +=(MessageChunk("Chunk1"), MessageChunk("Chunk2"), MessageChunk("Chunk3"), MessageChunk("Chunk4"), MessageChunk("Chunk5"))
      sender() ! Confirmed(ChunkedResponseStart(HttpResponse(StatusCodes.OK, entity = "DummyConfirmedStart")), DummyACK)

    case DummyACK =>
      if (msgCache.nonEmpty) {
        sender() ! Confirmed(msgCache.remove(0), DummyACK)
      } else {
        sender() ! ChunkedMessageEnd()
      }
  }
}

object DummyProcessorFactory extends ProcessorFactory {
  def create(settings: Option[Config])(implicit actorRefFactory: ActorRefFactory): Option[Processor] = {
    Some(DummyProcessor)
  }
}

class ExceptionProcessor(errorAt: List[String]) extends Processor {
  def error(ctx: RequestContext, phase: String) = {
    if (errorAt.contains(phase)) throw new RuntimeException()

    ctx
  }

  //inbound processing
  override def inbound(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext): Future[RequestContext] = Future(error(reqCtx, "inbound"))

  //outbound processing
  override def outbound(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext): Future[RequestContext] = Future(error(reqCtx, "outbound"))

  //first chance to handle input request before processing request
  override def preInbound(ctx: RequestContext)(implicit context: ActorContext): RequestContext = error(ctx, "preInbound")

  override def postInbound(ctx: RequestContext)(implicit context: ActorContext): RequestContext = error(ctx, "postInbound")

  override def preOutbound(ctx: RequestContext)(implicit context: ActorContext): RequestContext = error(ctx, "preOutbound")

  //last chance to handle output
  override def postOutbound(ctx: RequestContext)(implicit context: ActorContext): RequestContext = error(ctx, "postOutbound")
}

object DummyProcessor extends Processor {

  implicit class attr2method(val ctx: RequestContext) extends AnyVal {
    def +>(name: String, value: String): RequestContext = {
      ctx.copy(attributes = ctx.attributes + (name -> value))
    }
  }

  override def inbound(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext): Future[RequestContext] =
    Future {
      reqCtx +>("inbound", "go")
    }

  //outbound processing
  override def outbound(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext): Future[RequestContext] =
    Future {
      reqCtx +>("outbound", "go")
    }

  //last chance to handle output
  override def postOutbound(ctx: RequestContext)(implicit context: ActorContext): RequestContext = {
    val newctx = ctx +>("postoutbound", "go")
    val hs: List[HttpHeader] = newctx.attributes.flatMap { entry =>
      Some(HttpHeaders.RawHeader(entry._1, entry._2.toString))
    }.toList

    newctx.addResponseHeaders(hs: _*)
  }
}
