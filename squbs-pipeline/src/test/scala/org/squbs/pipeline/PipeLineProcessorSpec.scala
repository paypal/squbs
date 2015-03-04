package org.squbs.pipeline

import akka.actor.{Props, Actor, ActorContext, ActorSystem}
import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
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

		val processorActor = system.actorOf(Props(classOf[PipeLineProcessorActor], target, self, DummyProcessor))
		processorActor ! ctx

		val resp = expectMsgType[HttpResponse]
		println(resp)
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
		ctx +> ("postoutbound", "go")
	}
}
