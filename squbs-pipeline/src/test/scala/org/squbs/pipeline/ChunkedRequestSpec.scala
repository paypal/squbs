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
import akka.io.IO
import akka.pattern._
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import spray.can.Http
import spray.can.Http.RegisterChunkHandler
import spray.client.HttpDialog
import spray.client.pipelining._
import spray.http.HttpMethods._
import spray.http.StatusCodes._
import spray.http._
import spray.util.Utils._
import spray.util._

import scala.concurrent.duration._
import scala.language.postfixOps


class ChunkedRequestSpec extends TestKit(ActorSystem("testSystem", ConfigFactory.parseString(
  """
    |
    | spray.can.server {
    |  request-timeout = infinite
    |  timeout-timeout = infinite
    |  request-chunk-aggregation-limit = 0
    |  parsing {
    |      max-content-length = 5g
    |      incoming-auto-chunking-threshold-size = 1k
    |  }
    |}
    |
    |
  """.stripMargin))) with FlatSpecLike
with Matchers with ImplicitSender with BeforeAndAfterAll with MockHttpServerSupport {

  import system.dispatcher
  implicit val connectTimeout: Timeout = 5 seconds

  val (_, port) = temporaryServerHostnameAndPort()

  val hostConnector = Http.HostConnectorSetup("127.0.0.1", port)
  val Http.HostConnectorInfo(connector, _) = IO(Http).ask(hostConnector).await


  protected override def beforeAll(): Unit = {
    super.beforeAll()
    startMockServer(port = port, handler = Option(system.actorOf(Props {
      new Actor {
        val pipeHandler = context.actorOf(Props(classOf[ChunkActor]))
        val processor = new SimpleProcessor(SimplePipelineConfig.empty) {
          //sync method to process request chunk
          override def processRequestChunk(ctx: RequestContext, chunk: MessageChunk)(implicit context: ActorRefFactory):
          MessageChunk = {
            println("processRequestChunk...")
            MessageChunk(chunk.data.asString(HttpCharsets.`UTF-8`).replace('0', 'A'), chunk.extension)
          }

          //sync method to process request chunk end
          override def processRequestChunkEnd(ctx: RequestContext, chunkEnd: ChunkedMessageEnd)
                                             (implicit context: ActorRefFactory): ChunkedMessageEnd = {
            chunkEnd.copy(extension = "XYZ")
          }
        }

        override def receive: Actor.Receive = {
          case start@ChunkedRequestStart(req@HttpRequest(POST, Uri.Path("/nopipe"), _, _, _)) =>
            pipeHandler forward start

          case start@ChunkedRequestStart(req@HttpRequest(POST, Uri.Path("/pipe"), _, _, _)) =>
            context.actorOf(Props(classOf[PipelineProcessorActor], PipelineProcessorActor.toTarget(pipeHandler), processor)) forward RequestContext(start.request, isChunkRequest = true)
        }
      }
    }, "facade")))
  }

  override protected def afterAll(): Unit = {
    stopMockServer
    super.afterAll()
  }

  "upload chunks without pipeline" should "work" in {

    val data = "1234567890" * 10000
    val response = HttpDialog(connector)
      .send(Post(uri = "/nopipe", content = data))
      .end
      .await(5 second)

    response.entity.data.asString(HttpCharsets.`UTF-8`) should be(data)

  }

  "upload chunks with pipeline" should "work" in {

    val data = "1234567890" * 10000
    val result = "123456789A" * 10000 + "XYZ"
    val response = HttpDialog(connector)
      .send(Post(uri = "/pipe", content = data))
      .end
      .await(5 second)

    response.entity.data.asString(HttpCharsets.`UTF-8`) should be(result)

  }
}

class ChunkActor extends Actor {

  override def receive: Actor.Receive = {
    case start@ChunkedRequestStart(req@HttpRequest(POST, _, _, _, _)) =>
      val handler = context.actorOf(Props(new ChunkedRequestHandler(context.sender(), start)))
      context.sender() ! RegisterChunkHandler(handler)
  }

  class ChunkedRequestHandler(client: ActorRef, start: ChunkedRequestStart) extends Actor with ActorLogging {

    import start.request._

    val content = new collection.mutable.ArrayBuffer[Byte]

    receivedChunk(entity.data)

    def receivedChunk(data: HttpData) {
      if (data.length > 0) {
        val byteArray = data.toByteArray
        println(data.asString(HttpCharsets.`UTF-8`))
        content ++= byteArray
      }
    }

    def receive = {
      case chunk: MessageChunk => receivedChunk(chunk.data)

      case chunkEnd: ChunkedMessageEnd =>
        content ++= chunkEnd.extension.getBytes
        sender() ! HttpResponse(OK, content.toArray)
        context.stop(self)
      case _ =>
        println("unknown message")
    }

  }

}



