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

package org.squbs.pipeline

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.apache.pekko.stream.scaladsl._
import org.apache.pekko.stream.BidiShape
import org.apache.pekko.testkit.TestKit
import org.apache.pekko.util.ByteString
import com.typesafe.config.ConfigFactory
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.pipeline.Timeouts.awaitMax

import scala.concurrent.Await
import scala.util.{Success, Try}

class PipelineExtensionSpec extends TestKit(ActorSystem("PipelineExtensionSpec", PipelineExtensionSpec.config))
  with AnyFlatSpecLike with Matchers {

  val pipelineExtension = PipelineExtension(system)
  val dummyEndpoint = Flow[RequestContext].map { r =>
    r.withResponse(Try(HttpResponse(entity = s"${r.request.headers.sortBy(_.name).mkString(",")}")))
  }

  it should "build the flow with defaults" in {
    val pipelineFlow = pipelineExtension.getFlow((Some("dummyFlow1"), Some(true)), Context("dummy", ServerPipeline))

    pipelineFlow should not be (None)
    val httpFlow = pipelineFlow.get.join(dummyEndpoint)
    val future = Source.single(RequestContext(HttpRequest(), 0)).runWith(httpFlow.toMat(Sink.head)(Keep.right))
    val rc = Await.result(future, awaitMax)

    rc.response should not be (None)
    val Some(Success(httpResponse)) = rc.response
    httpResponse.headers.sortBy(_.name) should equal(Seq(RawHeader("keyD", "valD"),
                                                         RawHeader("keyPreOutbound", "valPreOutbound"),
                                                         RawHeader("keyPostOutbound", "valPostOutbound")).sortBy(_.name))
    import system.dispatcher
    val actualEntity = Await.result(httpResponse.entity.dataBytes.runFold(ByteString(""))(_ ++ _) map(_.utf8String), awaitMax)
    val expectedEntity = Seq( RawHeader("keyA", "valA"),
                              RawHeader("keyB", "valB"),
                              RawHeader("keyC", "valC"),
                              RawHeader("keyPreInbound", "valPreInbound"),
                              RawHeader("keyPostInbound", "valPostInbound")).sortBy(_.name).mkString(",")
    actualEntity should equal(expectedEntity)
  }

  it should "build the flow without defaults when defaults are turned off" in {
    val pipelineFlow = pipelineExtension.getFlow((Some("dummyFlow1"), Some(false)), Context("dummy", ServerPipeline))

    pipelineFlow should not be (None)
    val httpFlow = pipelineFlow.get.join(dummyEndpoint)
    val future = Source.single(RequestContext(HttpRequest(), 0)).runWith(httpFlow.toMat(Sink.head)(Keep.right))
    val rc = Await.result(future, awaitMax)

    rc.response should not be (None)
    val Some(Success(httpResponse)) = rc.response
    httpResponse.headers should equal(Seq(RawHeader("keyD", "valD")))
    import system.dispatcher
    val actualEntity = Await.result(httpResponse.entity.dataBytes.runFold(ByteString(""))(_ ++ _) map(_.utf8String), awaitMax)
    val expectedEntity = Seq( RawHeader("keyA", "valA"),
                              RawHeader("keyB", "valB"),
                              RawHeader("keyC", "valC")).sortBy(_.name).mkString(",")
    actualEntity should equal(expectedEntity)
  }

  it should "throw IllegalArgumentException when getFlow is called with a bad flow name" in {
    intercept[IllegalArgumentException] {
      pipelineExtension.getFlow((Some("badPipelineName"), Some(true)), Context("dummy", ServerPipeline))
    }
  }

  it should "return None when no custom flow exists and defaults are off" in {
    pipelineExtension.getFlow((None, Some(false)), Context("dummy", ServerPipeline)) should be (None)
  }

  it should "build the flow with defaults when defaultsOn param is set to None" in {
    pipelineExtension.getFlow((None, None), Context("dummy", ServerPipeline)) should not be (None)
  }
}

object PipelineExtensionSpec {
  val config = ConfigFactory.parseString(
    s"""
       |dummyFlow1 {
       |  type = squbs.pipelineflow
       |  factory = org.squbs.pipeline.DummyFlow1
       |}
       |
       |preFlow {
       |  type = squbs.pipelineflow
       |  factory = org.squbs.pipeline.PreFlow
       |}
       |
       |postFlow {
       |  type = squbs.pipelineflow
       |  factory = org.squbs.pipeline.PostFlow
       |}
       |
       |squbs.pipeline.server.default {
       |  pre-flow =  preFlow
       |  post-flow = postFlow
       |}
    """.stripMargin
  )
}

class PipelineExtensionSpec2 extends TestKit(ActorSystem("PipelineExtensionSpec2", ConfigFactory.parseString(
  s"""
     |notExists {
     |  type = squbs.pipelineflow
     |  factory = org.squbs.pipeline.NotExists
     |}
   """.stripMargin))) with AnyFlatSpecLike with Matchers {

  it should "throw ClassNotFoundException when a squbs.pipelineflow factory class does not exist." in {
    intercept[ClassNotFoundException] {
      PipelineExtension(system)
    }
  }
}

class PipelineExtensionSpec3 extends TestKit(ActorSystem("PipelineExtensionSpec3", ConfigFactory.parseString(
  s"""
     |dummyFlow1 {
     |  type = squbs.pipelineflow
     |  factory = org.squbs.pipeline.DummyFlow1
     |}
   """.stripMargin))) with AnyFlatSpecLike with Matchers {

  val pipelineExtension = PipelineExtension(system)
  val dummyEndpoint = Flow[RequestContext].map { r =>
    r.withResponse(Try(HttpResponse(entity = s"${r.request.headers.sortBy(_.name).mkString(",")}")))
  }

  it should "return None when no custom flow exists and no defaults specified in config" in {
    pipelineExtension.getFlow((None, Some(true)), Context("dummy", ServerPipeline)) should be (None)
  }

  it should "be able to build the flow when no defaults are specified in config" in {
    val pipelineFlow = pipelineExtension.getFlow((Some("dummyFlow1"), Some(true)), Context("dummy", ServerPipeline))

    pipelineFlow should not be (None)
    val httpFlow = pipelineFlow.get.join(dummyEndpoint)
    val future = Source.single(RequestContext(HttpRequest(), 0)).runWith(httpFlow.toMat(Sink.head)(Keep.right))
    val rc = Await.result(future, awaitMax)

    rc.response should not be (None)
    val Some(Success(httpResponse)) = rc.response
    httpResponse.headers should equal(Seq(RawHeader("keyD", "valD")))
    import system.dispatcher
    val entity = Await.result(httpResponse.entity.dataBytes.runFold(ByteString(""))(_ ++ _) map(_.utf8String), awaitMax)
    entity should equal("keyA: valA,keyB: valB,keyC: valC")
  }
}

class DummyFlow1 extends PipelineFlowFactory {

  override def create(context: Context)(implicit system: ActorSystem): PipelineFlow = {

    BidiFlow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val stageA = b.add(Flow[RequestContext].map { rc => rc.withRequestHeaders(RawHeader("keyA", "valA")) })
      val stageB = b.add(Flow[RequestContext].map { rc => rc.withRequestHeaders(RawHeader("keyB", "valB")) })
      val stageC = b.add(dummyBidi)
      val stageD = b.add(Flow[RequestContext].map { rc => rc.withResponseHeaders(RawHeader("keyD", "valD")) })

      stageA ~> stageB ~> stageC.in1
      stageD <~           stageC.out2

      BidiShape(stageA.in, stageC.out1, stageC.in2, stageD.out)
    })
  }

  val dummyBidi = BidiFlow.fromGraph(GraphDSL.create() { implicit b =>
    val requestFlow = b.add(Flow[RequestContext].map { rc => rc.withRequestHeaders(RawHeader("keyC", "valC")) } )
    val responseFlow = b.add(Flow[RequestContext])
    BidiShape.fromFlows(requestFlow, responseFlow)
  })
}

class PreFlow extends PipelineFlowFactory {

  override def create(context: Context)(implicit system: ActorSystem): PipelineFlow = {

    BidiFlow.fromGraph(GraphDSL.create() { implicit b =>
      val inbound = b.add(Flow[RequestContext].map { rc => rc.withRequestHeaders(RawHeader("keyPreInbound", "valPreInbound")) })
      val outbound = b.add(Flow[RequestContext].map { rc => rc.withResponseHeaders(RawHeader("keyPreOutbound", "valPreOutbound")) })
      BidiShape.fromFlows(inbound, outbound)
    })
  }
}

class PostFlow extends PipelineFlowFactory {

  override def create(context: Context)(implicit system: ActorSystem): PipelineFlow = {

    BidiFlow.fromGraph(GraphDSL.create() { implicit b =>
      val inbound = b.add(Flow[RequestContext].map { rc => rc.withRequestHeaders(RawHeader("keyPostInbound", "valPostInbound")) })
      val outbound = b.add(Flow[RequestContext].map { rc => rc.withResponseHeaders(RawHeader("keyPostOutbound", "valPostOutbound")) })
      BidiShape.fromFlows(inbound, outbound)
    })
  }
}
