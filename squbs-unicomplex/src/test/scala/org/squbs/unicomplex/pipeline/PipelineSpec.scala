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

import org.apache.pekko.actor.{Actor, ActorSystem}
import org.apache.pekko.http.scaladsl.model.Uri.Path
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import org.apache.pekko.http.scaladsl.server.{RejectionHandler, Route}
import org.apache.pekko.pattern._
import org.apache.pekko.stream.BidiShape
import org.apache.pekko.stream.scaladsl.{BidiFlow, Flow, GraphDSL}
import org.apache.pekko.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.lifecycle.GracefulStop
import org.squbs.pipeline.{Context, PipelineFlow, PipelineFlowFactory, RequestContext}
import org.squbs.unicomplex.Timeouts._
import org.squbs.unicomplex._

import scala.concurrent.Await

object PipelineSpec {

  val classPaths = Array(getClass.getClassLoader.getResource("classpaths/pipeline/PipelineSpec").getPath)

  val config = ConfigFactory.parseString(
    s"""
       |default-listener.bind-port = 0
       |squbs {
       |  actorsystem-name = PipelineSpec
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

  class PipelineSpec extends TestKit(
    PipelineSpec.boot.actorSystem) with AnyFlatSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll {


    val portBindings = Await.result((Unicomplex(system).uniActor ? PortBindings).mapTo[Map[String, Int]], awaitMax)
    val port = portBindings("default-listener")


    override def afterAll(): Unit = {
      Unicomplex(system).uniActor ! GracefulStop
    }

    it should "build the flow with defaults" in {
      val (actualEntity, actualHeaders) =
        Await.result(entityAsStringWithHeaders(s"http://127.0.0.1:$port/1/dummy"), awaitMax)

      val expectedHeaders = Seq(
        RawHeader("keyD", "valD"),
        RawHeader("keyPreOutbound", "valPreOutbound"),
        RawHeader("keyPostOutbound", "valPostOutbound")).sortBy(_.name)

      actualHeaders.filter(_.name.startsWith("key")).sortBy(_.name) should equal(expectedHeaders)

      actualEntity should equal(Seq(
      RawHeader("keyA", "valA"),
      RawHeader("keyB", "valB"),
      RawHeader("keyC", "valC"),
      RawHeader("keyPreInbound", "valPreInbound"),
      RawHeader("keyPostInbound", "valPostInbound")).sortBy(_.name).mkString(","))
  }

  it should "build the flow only with defaults" in {
    val (actualEntity, actualHeaders) = Await.result(entityAsStringWithHeaders(s"http://127.0.0.1:$port/2/dummy"), awaitMax)

    val expectedHeaders = Seq(
      RawHeader("keyPreOutbound", "valPreOutbound"),
      RawHeader("keyPostOutbound", "valPostOutbound")).sortBy(_.name)

    actualHeaders.filter(_.name.startsWith("key")).sortBy(_.name) should equal(expectedHeaders)

    actualEntity should equal(Seq(
      RawHeader("keyPreInbound", "valPreInbound"),
      RawHeader("keyPostInbound", "valPostInbound")).sortBy(_.name).mkString(","))
  }

  it should "build the flow without defaults" in {
    val (actualEntity, actualHeaders) = Await.result(entityAsStringWithHeaders(s"http://127.0.0.1:$port/3/dummy"), awaitMax)

    val expectedHeaders = Seq(RawHeader("keyD", "valD")).sortBy(_.name)

    actualHeaders.filter(_.name.startsWith("key")).sortBy(_.name) should equal(expectedHeaders)

    actualEntity should equal(Seq(
      RawHeader("keyA", "valA"),
      RawHeader("keyB", "valB"),
      RawHeader("keyC", "valC")).sortBy(_.name).mkString(","))
  }

  it should "not build a pipeline" in {
    val (actualEntity, actualHeaders) = Await.result(entityAsStringWithHeaders(s"http://127.0.0.1:$port/4/dummy"), awaitMax)

    actualHeaders.filter(_.name.startsWith("key")).sortBy(_.name) should equal(Seq.empty[HttpHeader])

    actualEntity should equal("")
  }

  it should "build the flow with defaults for non-route actor" in {
    val (actualEntity, actualHeaders) = Await.result(entityAsStringWithHeaders(s"http://127.0.0.1:$port/5/dummy"), awaitMax)

    val expectedHeaders = Seq(
      RawHeader("keyD", "valD"),
      RawHeader("keyPreOutbound", "valPreOutbound"),
      RawHeader("keyPostOutbound", "valPostOutbound")).sortBy(_.name)

    actualHeaders.filter(_.name.startsWith("key")).sortBy(_.name) should equal(expectedHeaders)

    actualEntity should equal(Seq(
      RawHeader("keyA", "valA"),
      RawHeader("keyB", "valB"),
      RawHeader("keyC", "valC"),
      RawHeader("keyPreInbound", "valPreInbound"),
      RawHeader("keyPostInbound", "valPostInbound")).sortBy(_.name).mkString(","))
  }

  it should "bypass the response flow when request times out" in {
    val (_, actualHeaders) = Await.result(entityAsStringWithHeaders(s"http://127.0.0.1:$port/5/timeout"), awaitMax)
    actualHeaders.filter(_.name.startsWith("key")).sortBy(_.name) should equal(Seq.empty[HttpHeader])
  }

  it should "run the pipeline when resource could not be found in route level" in {
    val (actualEntity, actualHeaders) = Await.result(entityAsStringWithHeaders(s"http://127.0.0.1:$port/1/notexists"), awaitMax)

    val expectedHeaders = Seq(
      RawHeader("keyD", "valD"),
      RawHeader("keyPreOutbound", "valPreOutbound"),
      RawHeader("keyPostOutbound", "valPostOutbound")).sortBy(_.name)

    actualHeaders.filter(_.name.startsWith("key")).sortBy(_.name) should equal(expectedHeaders)
    actualEntity should equal("Custom route level not found message.")
  }

  it should "not build a flow for the resource not found in webcontext level scenario" in {
    val (actualEntity, actualHeaders) = Await.result(entityAsStringWithHeaders(s"http://127.0.0.1:$port/notexists"), awaitMax)
    actualHeaders.filter(_.name.startsWith("key")).sortBy(_.name) should equal(Seq.empty[HttpHeader])
    actualEntity should equal(StatusCodes.NotFound.defaultMessage)
  }
}

class DummyRoute extends RouteDefinition {
  override def route: Route =
    path("dummy") {
      extract(_.request.headers) { headers =>
        // Filter any non-test headers
        complete(headers.filter(_.name.startsWith("key")).sortBy(_.name).mkString(","))
      }
    }

  override def rejectionHandler: Option[RejectionHandler] = Some(RejectionHandler.newBuilder().handleNotFound {
    complete(StatusCodes.NotFound, "Custom route level not found message.")
  }.result())
}

class DummyActor extends Actor {

  override def receive: Receive = {
    case req @ HttpRequest(_, Uri(_, _, Path("/5/timeout"), _, _), _, _, _) => // Do nothing

    case req: HttpRequest =>
      sender() ! HttpResponse(entity = req.headers.filter(_.name.startsWith("key")).sortBy(_.name).mkString(","))
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

  val dummyBidi = BidiFlow.fromGraph(GraphDSL.create() { implicit b =>
    val requestFlow = b.add(Flow[RequestContext].map { rc => rc.withRequestHeader(RawHeader("keyC", "valC")) } )
    val responseFlow = b.add(Flow[RequestContext])
    BidiShape.fromFlows(requestFlow, responseFlow)
  })
}

class PreFlow extends PipelineFlowFactory {

  override def create(context: Context)(implicit system: ActorSystem): PipelineFlow = {

    BidiFlow.fromGraph(GraphDSL.create() { implicit b =>
      val inbound = b.add(Flow[RequestContext].map { rc => rc.withRequestHeader(RawHeader("keyPreInbound", "valPreInbound")) })
      val outbound = b.add(Flow[RequestContext].map { rc => rc.withResponseHeader(RawHeader("keyPreOutbound", "valPreOutbound")) })
      BidiShape.fromFlows(inbound, outbound)
    })
  }
}

class PostFlow extends PipelineFlowFactory {

  override def create(context: Context)(implicit system: ActorSystem): PipelineFlow = {

    BidiFlow.fromGraph(GraphDSL.create() { implicit b =>
      val inbound = b.add(Flow[RequestContext].map { rc => rc.withRequestHeader(RawHeader("keyPostInbound", "valPostInbound")) })
      val outbound = b.add(Flow[RequestContext].map { rc => rc.withResponseHeader(RawHeader("keyPostOutbound", "valPostOutbound")) })
      BidiShape.fromFlows(inbound, outbound)
    })
  }
}
