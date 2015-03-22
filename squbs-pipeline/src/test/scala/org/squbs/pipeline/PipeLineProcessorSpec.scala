package org.squbs.pipeline

import akka.actor._
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import spray.http._

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

/**
 * Created by jiamzhang on 2015/3/4.
 */
class PipeineProcessorSpec extends TestKit(ActorSystem("PipelineProcessorSpecSys"))
																		with FlatSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll {

	override def afterAll {
		system.shutdown()
	}

	"PipelineProcessor" should "processs the piepline correctly" in {
		val request = HttpRequest(HttpMethods.GET, Uri("http://localhost:9900/hello"))
		val ctx = RequestContext(request)
		val target = TestActorRef[DummyTarget]

		PipelineMgr(system).registerProcessor("getprocessor", "org.squbs.pipeline.DummyProcessorFactory", Some(ConfigFactory.empty))
		val processorActor = PipelineMgr(system).getPipeline("getprocessor", target, self)
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

	"PipelineProcessorActor" should "handle the chunk response correctly" in {
		val request = HttpRequest(HttpMethods.POST, Uri("http://localhost:9900/hello"))
		val ctx = RequestContext(request)
		val target = TestActorRef[DummyTarget]

		PipelineMgr(system).registerProcessor("myprocessor", "org.squbs.pipeline.DummyProcessorFactory", Some(ConfigFactory.empty))

		val processorActor = PipelineMgr(system).getPipeline("myprocessor", target, self)
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

		PipelineMgr(system).registerProcessor("chunkprocessor", "org.squbs.pipeline.DummyProcessorFactory", Some(ConfigFactory.empty))

		val processorActor = PipelineMgr(system).getPipeline("chunkprocessor", target, self)
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
}

case object DummyACK

class DummyTarget extends Actor {
	private val msgCache = new ListBuffer[MessageChunk]()

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
		case r: HttpRequest if r.method == HttpMethods.PUT =>
			msgCache += (MessageChunk("Chunk1"), MessageChunk("Chunk2"), MessageChunk("Chunk3"), MessageChunk("Chunk4"), MessageChunk("Chunk5"))
			sender() ! Confirmed(ChunkedResponseStart(HttpResponse(StatusCodes.OK, entity = "DummyConfirmedStart")), DummyACK)

		case DummyACK =>
			if (msgCache.size > 0) {
				sender() ! Confirmed(msgCache.remove(0), DummyACK)
			} else {
				sender() ! ChunkedMessageEnd()
			}
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
