/*
 * Copyright 2015 PayPal
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

package org.squbs.unicomplex.streaming.pipeline

import akka.actor.{Actor, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpEntity.{LastChunk, Chunk}
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source, FileIO}
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, FlatSpecLike}
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.Timeouts._
import org.squbs.unicomplex.{Unicomplex, UnicomplexBoot, JMX}
import org.squbs.unicomplex.streaming._

import scala.concurrent.Await

object PipelineChunkingSpec {

  val classPaths = Array(getClass.getClassLoader.getResource("classpaths/streaming/pipeline/PipelineChunkingSpec").getPath)

  val (_, _, port) = temporaryServerHostnameAndPort()

  val config = ConfigFactory.parseString(
    s"""
       |default-listener.bind-port = $port
       |squbs {
       |  actorsystem-name = streaming-pipelineChunkingSpec
       |  ${JMX.prefixConfig} = true
       |  experimental-mode-on = true
       |}
       |
       |dummyFlow {
       |  type = squbs.pipelineflow
       |  factory = org.squbs.unicomplex.streaming.pipeline.DummyFlow
       |}
       |
       |preFlow {
       |  type = squbs.pipelineflow
       |  factory = org.squbs.unicomplex.streaming.pipeline.PreFlow
       |}
       |
       |postFlow {
       |  type = squbs.pipelineflow
       |  factory = org.squbs.unicomplex.streaming.pipeline.PostFlow
       |}
       |
       |squbs.pipeline.streaming.defaults {
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

class PipelineChunkingSpec extends TestKit(PipelineChunkingSpec.boot.actorSystem) with FlatSpecLike
  with Matchers with ImplicitSender with BeforeAndAfterAll {

  implicit val am = ActorMaterializer()
  import system.dispatcher

  val port = system.settings.config getInt "default-listener.bind-port"

  override def afterAll() {
    Unicomplex(system).uniActor ! GracefulStop
  }

  it should "run pipeline flow for chunked requests" in {
    val filePath =
      PipelineChunkingSpec.getClass.getResource("/classpaths/streaming/StreamSvc/dummy.txt").getPath
    val file = new java.io.File(filePath)
    require(file.exists() && file.canRead)

    val chunkSize = 8192
    val responseF = Http().singleRequest(HttpRequest(HttpMethods.POST,
      uri = s"http://127.0.0.1:$port/fileuploadroute/file-upload",
      entity = HttpEntity(MediaTypes.`application/octet-stream`,
        FileIO.fromFile(file, chunkSize))))

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

    response.entity.dataBytes.map(b => Chunk(b)).runWith(Sink.actorRef(self, "Done"))
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
