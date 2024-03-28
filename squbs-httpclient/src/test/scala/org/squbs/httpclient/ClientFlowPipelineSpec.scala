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

package org.squbs.httpclient

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import org.apache.pekko.stream.BidiShape
import org.apache.pekko.stream.scaladsl._
import org.apache.pekko.util.ByteString
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.squbs.pipeline.{Context, PipelineFlow, PipelineFlowFactory, RequestContext}
import org.squbs.resolver.ResolverRegistry
import org.squbs.testkit.Timeouts._

import scala.concurrent.{Await, Future}
import scala.util.{Success, Try}

object ClientFlowPipelineSpec {

  val config = ConfigFactory.parseString(
    s"""
       |dummyFlow {
       |  type = squbs.pipelineflow
       |  factory = org.squbs.httpclient.DummyFlow
       |}
       |
       |preFlow {
       |  type = squbs.pipelineflow
       |  factory = org.squbs.httpclient.PreFlow
       |}
       |
       |postFlow {
       |  type = squbs.pipelineflow
       |  factory = org.squbs.httpclient.PostFlow
       |}
       |
       |squbs.pipeline.client.default {
       |  pre-flow =  preFlow
       |  post-flow = postFlow
       |}
       |
       |clientWithCustomPipelineWithDefaults {
       |  type = squbs.httpclient
       |  pipeline = dummyFlow
       |}
       |
       |clientWithOnlyDefaults {
       |  type = squbs.httpclient
       |}
       |
       |clientWithCustomPipelineWithoutDefaults {
       |  type = squbs.httpclient
       |  pipeline = dummyFlow
       |  defaultPipeline = off
       |}
       |
       |clientWithNoPipeline {
       |  type = squbs.httpclient
       |  defaultPipeline = off
       |}
    """.stripMargin
  )

  implicit val system: ActorSystem = ActorSystem("ClientFlowPipelineSpec", config)
  import org.apache.pekko.http.scaladsl.server.Directives._

  val route =
    path("hello") {
      extract(_.request.headers) { headers =>
        // Filter any non-test headers
        complete(headers.filter(_.name.startsWith("key")).sortBy(_.name).mkString(","))
      }
    }

  val serverBinding = Await.result(Http().newServerAt("localhost", 0).bind(route), awaitMax)
  val port = serverBinding.localAddress.getPort
}

class ClientFlowPipelineSpec extends AsyncFlatSpec with Matchers with BeforeAndAfterAll {

  import ClientFlowPipelineSpec._

  override def afterAll(): Unit = {
    serverBinding.unbind() map {_ => system.terminate()}
  }

  ResolverRegistry(system).register[HttpEndpoint]("LocalhostEndpointResolver")
    { (_, _) => Some(HttpEndpoint(s"http://localhost:$port")) }

  it should "build the flow with defaults" in {
    val expectedResponseHeaders = Seq(
      RawHeader("keyD", "valD"),
      RawHeader("keyPreOutbound", "valPreOutbound"),
      RawHeader("keyPostOutbound", "valPostOutbound"))

    val expectedEntity = Seq(
      RawHeader("keyA", "valA"),
      RawHeader("keyB", "valB"),
      RawHeader("keyC", "valC"),
      RawHeader("keyPreInbound", "valPreInbound"),
      RawHeader("keyPostInbound", "valPostInbound")).sortBy(_.name).mkString(",")

    assertPipeline("clientWithCustomPipelineWithDefaults", expectedResponseHeaders, expectedEntity)
  }

  it should "build the flow only with defaults" in {
    val expectedResponseHeaders = Seq(
      RawHeader("keyPreOutbound", "valPreOutbound"),
      RawHeader("keyPostOutbound", "valPostOutbound"))

    val expectedEntity =  Seq(
      RawHeader("keyPreInbound", "valPreInbound"),
      RawHeader("keyPostInbound", "valPostInbound")).sortBy(_.name).mkString(",")

    assertPipeline("clientWithOnlyDefaults", expectedResponseHeaders, expectedEntity)
  }

  it should "build the flow without defaults" in {
    val expectedResponseHeaders = Seq(RawHeader("keyD", "valD"))

    val expectedEntity =  Seq(
      RawHeader("keyA", "valA"),
      RawHeader("keyB", "valB"),
      RawHeader("keyC", "valC")).sortBy(_.name).mkString(",")

    assertPipeline("clientWithCustomPipelineWithoutDefaults", expectedResponseHeaders, expectedEntity)
  }

  it should "not build a pipeline" in {
    assertPipeline("clientWithNoPipeline", Seq.empty[RawHeader], "")
  }

  // TODO Add tests to make sure do not change the type of userContext
  // it should "keep the user context"

  private def assertPipeline(clientName: String, expectedResponseHeaders: Seq[RawHeader], expectedEntity: String) = {
    val clientFlow = ClientFlow[Int](clientName)
    val responseFuture: Future[(Try[HttpResponse], Int)] =
      Source.single(HttpRequest(uri = "/hello") -> 42)
        .via(clientFlow)
        .runWith(Sink.head)

    val (Success(response), userContext) = Await.result(responseFuture, awaitMax)
    userContext shouldBe 42 // Make sure we keep user context

    response.status should be (StatusCodes.OK)
    response.headers.filter(_.name.startsWith("key")) should contain theSameElementsAs expectedResponseHeaders
    val entity = response.entity.dataBytes.runFold(ByteString(""))(_ ++ _) map(_.utf8String)
    entity map { e => e shouldEqual expectedEntity }
  }
}

class DummyFlow extends PipelineFlowFactory {

  override def create(context: Context)(implicit system: ActorSystem): PipelineFlow = {

    BidiFlow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val stageA = b.add(Flow[RequestContext].map { rc => rc.withRequestHeader(RawHeader("keyA", "valA")) })
      val stageB = b.add(Flow[RequestContext].map { rc => rc.withRequestHeader(RawHeader("keyB", "valB")) })
      val stageC = b.add(dummyBidi)
      val stageD = b.add(Flow[RequestContext].map { rc => rc.withResponseHeader(RawHeader("keyD", "valD")) })

      stageA ~> stageB ~> stageC.in1
      stageD <~           stageC.out2

      BidiShape(stageA.in, stageC.out1, stageC.in2, stageD.out)
    })
  }

  val requestFlow = Flow[RequestContext].map { rc => rc.withRequestHeader(RawHeader("keyC", "valC")) }
  val dummyBidi =  BidiFlow.fromFlows(requestFlow, Flow[RequestContext])
}

class PreFlow extends PipelineFlowFactory {

  override def create(context: Context)(implicit system: ActorSystem): PipelineFlow = {
    val inbound = Flow[RequestContext].map { rc => rc.withRequestHeader(RawHeader("keyPreInbound", "valPreInbound")) }
    val outbound = Flow[RequestContext].map { rc => rc.withResponseHeader(RawHeader("keyPreOutbound", "valPreOutbound")) }
    BidiFlow.fromFlows(inbound, outbound)
  }
}

class PostFlow extends PipelineFlowFactory {

  override def create(context: Context)(implicit system: ActorSystem): PipelineFlow = {
    val inbound = Flow[RequestContext].map { rc => rc.withRequestHeader(RawHeader("keyPostInbound", "valPostInbound")) }
    val outbound = Flow[RequestContext].map { rc => rc.withResponseHeader(RawHeader("keyPostOutbound", "valPostOutbound")) }
    BidiFlow.fromFlows(inbound, outbound)
  }
}
