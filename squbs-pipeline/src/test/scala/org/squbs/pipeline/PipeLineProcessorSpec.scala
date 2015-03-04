package org.squbs.pipeline

import akka.actor._
import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import com.typesafe.config.{ConfigFactory, Config}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import spray.http.HttpHeaders.RawHeader
import spray.http._

import scala.concurrent.{Future, ExecutionContext}

/**
 * Created by jiamzhang on 2015/3/4.
 */
class PipeLineProcessorSpec extends TestKit(ActorSystem("PipeLineProcessorSpecSys"))
																		with FlatSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll {

	override def afterAll {
		system.shutdown()
	}

	"PipeLineProcessor" should "processs the piepline correctly" in {
		val request = HttpRequest(HttpMethods.GET, Uri("http://localhost:9900/hello"))
		val ctx = RequestContext(request)
		val target = TestActorRef[DummyTarget]

		PipeLineMgr(system).registerProcessor("getprocessor", "org.squbs.pipeline.DummyProcessorFactory", Some(ConfigFactory.empty))
		val processorActor = PipeLineMgr(system).getPipeLine("getprocessor", target, self)
		processorActor ! ctx

		val resp = expectMsgType[HttpResponse]
		val hs = resp.headers
		val pre = hs.find(_.name == "preinbound")
		pre should not be None
		pre.get.value shouldBe "go"

		val post = hs.find(_.name == "postoutbound")
		post should not be None
		post.get.value shouldBe "go"
	}

	"PipeLineProcessorActor" should "handle the chunk response correctly" in {
		val request = HttpRequest(HttpMethods.POST, Uri("http://localhost:9900/hello"))
		val ctx = RequestContext(request)
		val target = TestActorRef[DummyTarget]

		PipeLineMgr(system).registerProcessor("myprocessor", "org.squbs.pipeline.DummyProcessorFactory", Some(ConfigFactory.empty))

		val processorActor = PipeLineMgr(system).getPipeLine("myprocessor", target, self)
		processorActor ! ctx

		val resp = expectMsgType[ChunkedResponseStart]
		expectMsgType[MessageChunk]
		expectMsgType[MessageChunk]
		expectMsgType[MessageChunk]
		expectMsgType[MessageChunk]
		expectMsgType[MessageChunk]
		expectMsgType[ChunkedMessageEnd]

		val hs = resp.response.headers
		val pre = hs.find(_.name == "preinbound")
		pre should not be None
		pre.get.value shouldBe "go"

		val post = hs.find(_.name == "postoutbound")
		post should not be None
		post.get.value shouldBe "go"
	}
}

class DummyTarget extends Actor {
	override def receive = {
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
	}
}

class DummyProcessorFactory extends ProcessorFactory {
	override def create(settings: Option[Config])(implicit actorRefFactory: ActorRefFactory): Processor = {
		DummyProcessor
	}
}

object DummyProcessor extends Processor {
	implicit class attr2method(ctx: RequestContext) {
		def +> (name: String, value: String): RequestContext = {
			ctx.copy(attributes = ctx.attributes + (name -> value))
		}
	}
	override def inbound(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext): Future[RequestContext] =
		Future {
			reqCtx +> ("inbound", "go")
		}

	//outbound processing
	override def outbound(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext): Future[RequestContext] =
		Future {
			reqCtx +> ("outbound", "go")
		}

	//first chance to handle input request before processing request
	override def preInbound(ctx: RequestContext)(implicit context: ActorContext): RequestContext = {
		ctx +> ("preinbound", "go")
	}

	override def postInbound(ctx: RequestContext)(implicit context: ActorContext): RequestContext = {
		ctx +> ("postinbound", "go")
	}

	override def preOutbound(ctx: RequestContext)(implicit context: ActorContext): RequestContext = {
		ctx +> ("preoutbound", "go")
	}

	//last chance to handle output
	override def postOutbound(ctx: RequestContext)(implicit context: ActorContext): RequestContext = {
		val newctx = ctx +> ("postoutbound", "go")
		val hs: List[HttpHeader] = newctx.attributes.flatMap { entry =>
			Some(HttpHeaders.RawHeader(entry._1, entry._2.toString))
		}.toList

		newctx.response match {
			case n@NormalResponse(resp) =>
				newctx.copy(response = n.update(resp.copy(headers = hs ++ resp.headers)))
			case e: ExceptionalResponse =>
				newctx.copy(response = e.copy(response = e.response.copy(headers = hs ++ e.response.headers)))
			case _ => newctx
		}
	}
}
