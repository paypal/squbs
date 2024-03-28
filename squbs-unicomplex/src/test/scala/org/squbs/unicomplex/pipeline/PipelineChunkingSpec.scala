/*
 * Copyright 2017 PayPal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.squbs.unicomplex.pipeline

import org.apache.pekko.actor.{Actor, ActorSystem, Status}
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.HttpEntity.{Chunk, LastChunk}
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import org.apache.pekko.http.scaladsl.server._
import org.apache.pekko.pattern._
import org.apache.pekko.stream.scaladsl.{FileIO, Sink, Source}
import org.apache.pekko.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.Timeouts._
import org.squbs.unicomplex._

import scala.concurrent.Await

object PipelineChunkingSpec {

  val classPaths = Array(getClass.getClassLoader.getResource("classpaths/pipeline/PipelineChunkingSpec").getPath)

  val config = ConfigFactory.parseString(
    s"""
       |default-listener.bind-port = 0
       |squbs {
       |  actorsystem-name = PipelineChunkingSpec
       |  ${JMX.prefixConfig} = true
       |}
       |
       |dummyFlow {
       |  type = squbs.pipelineflow
       |  factory = org.squbs.unicomplex.pipeline.DummyFlow
       |}
       |
       |preFlow {
       |  type = squbs.pipelineflow
       |  factory = org.squbs.unicomplex.pipeline.PreFlow
       |}
       |
       |postFlow {
       |  type = squbs.pipelineflow
       |  factory = org.squbs.unicomplex.pipeline.PostFlow
       |}
       |
       |squbs.pipeline.server.default {
       |  pre-flow =  preFlow
       |  post-flow = postFlow
       |}
    """.stripMargin
  )

  val boot = UnicomplexBoot(config)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()
}

class PipelineChunkingSpec extends TestKit(PipelineChunkingSpec.boot.actorSystem) with AnyFlatSpecLike
  with Matchers with ImplicitSender with BeforeAndAfterAll {

  import system.dispatcher

  val portBindings = Await.result((Unicomplex(system).uniActor ? PortBindings).mapTo[Map[String, Int]], awaitMax)
  val port = portBindings("default-listener")

  override def afterAll(): Unit = {
    Unicomplex(system).uniActor ! GracefulStop
  }

  it should "run pipeline flow for chunked requests" in {
    val filePath =
      PipelineChunkingSpec.getClass.getResource("/classpaths/StreamSvc/dummy.txt").getPath
    val file = new java.io.File(filePath)
    require(file.exists() && file.canRead)

    val chunkSize = 8192
    val responseF = Http().singleRequest(HttpRequest(HttpMethods.POST,
      uri = s"http://127.0.0.1:$port/fileuploadroute/file-upload",
      entity = HttpEntity(MediaTypes.`application/octet-stream`,
        FileIO.fromPath(file.toPath, chunkSize))))

    val (actualResponseEntity, actualResponseHeaders) = Await.result(
      responseF flatMap { r => extractEntityAsString(r) map {
        (_, r.headers)
      }
      }, awaitMax)

    val expectedResponseHeaders = Seq(
      RawHeader("keyD", "valD"),
      RawHeader("keyPreOutbound", "valPreOutbound"),
      RawHeader("keyPostOutbound", "valPostOutbound")).sortBy(_.name)

    actualResponseHeaders.filter(_.name.startsWith("key")).sortBy(_.name) should equal(expectedResponseHeaders)

    val expectedNumberOfChunks = Math.ceil(file.length.toDouble / chunkSize).toInt

    val expectedRequestHeaders = Seq(
      RawHeader("keyA", "valA"),
      RawHeader("keyB", "valB"),
      RawHeader("keyC", "valC"),
      RawHeader("keyPreInbound", "valPreInbound"),
      RawHeader("keyPostInbound", "valPostInbound")).sortBy(_.name).mkString(",")

    val expectedResponseEntity = s"Chunk Count: $expectedNumberOfChunks ByteCount: ${file.length} $expectedRequestHeaders"
    actualResponseEntity should be(expectedResponseEntity)
  }

  it should "run pipeline for chunked responses" in {
    val response = Await.result(Http().singleRequest(HttpRequest(uri = s"http://127.0.0.1:$port/chunkingactor/")) , awaitMax)

    val expectedRequestHeaders = Seq(
      RawHeader("keyA", "valA"),
      RawHeader("keyB", "valB"),
      RawHeader("keyC", "valC"),
      RawHeader("keyPreInbound", "valPreInbound"),
      RawHeader("keyPostInbound", "valPostInbound")).sortBy(_.name).mkString(",")

    val expectedResponseHeaders = Seq(
      RawHeader("keyD", "valD"),
      RawHeader("keyPreOutbound", "valPreOutbound"),
      RawHeader("keyPostOutbound", "valPostOutbound")).sortBy(_.name)

    response.headers.filter(_.name.startsWith("key")).sortBy(_.name) should equal(expectedResponseHeaders)

    response.entity.dataBytes
      .map(b => Chunk(b))
      .runWith(Sink.actorRef(self, "Done", t => Status.Failure(t)))
    expectMsg(Chunk("1"))
    expectMsg(Chunk("2"))
    expectMsg(Chunk("3"))
    expectMsg(Chunk("4"))
    expectMsg(Chunk("5"))
    expectMsg(Chunk("6"))
    expectMsg(Chunk("7"))
    expectMsg(Chunk("8"))
    expectMsg(Chunk("9"))
    expectMsg(Chunk(expectedRequestHeaders)) // ChunkingActor sends request headers as the 10th chunk
    expectMsg("Done")
  }
}

class FileUploadRoute extends RouteDefinition {

  def route: Route =
    extractRequestContext { ctx =>
      implicit val materializer = ctx.materializer
      implicit val ec = ctx.executionContext

      path("file-upload") {
        extract(_.request.headers) { headers =>

          var chunkCount = 0L
          var byteCount = 0L

          val future = ctx.request.entity.dataBytes.runForeach { b =>
            chunkCount += 1
            byteCount += b.length
          }

          onSuccess(future) { _ =>
            complete {
              val requestHeaders = headers.filter(_.name.startsWith("key")).sortBy(_.name).mkString(",")
              s"Chunk Count: $chunkCount ByteCount: $byteCount $requestHeaders"
            }
          }
        }
      }
    }
}

class ChunkingActor extends Actor {

  override def receive: Receive = {
    case req: HttpRequest =>
      val headersAsString = req.headers.filter(_.name.startsWith("key")).sortBy(_.name).mkString(",")
      val source = Source(1 to 11).map(n => if (n == 10 ) {Chunk(headersAsString)}
                                            else if (n == 11) { LastChunk }
                                            else { Chunk(n.toString) })
      sender() ! HttpResponse(entity = HttpEntity.Chunked(ContentTypes.`text/plain(UTF-8)`, source))
  }
}
