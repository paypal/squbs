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

package org.squbs.unicomplex

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.HttpEntity.Chunked
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import org.apache.pekko.pattern._
import org.apache.pekko.stream.scaladsl.{Keep, Sink, Source}
import org.apache.pekko.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.Timeouts._

object JavaFlowSvcSpec {

  val classPaths = Array(getClass.getClassLoader.getResource("classpaths/JavaFlowSvc").getPath)

  val config = ConfigFactory.parseString(
    s"""
       |default-listener.bind-port = 0
       |squbs {
       |  actorsystem-name = JavaFlowSvcSpec
       |  ${JMX.prefixConfig} = true
       |}
    """.stripMargin
  )

  val boot = UnicomplexBoot(config)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()
}

class JavaFlowSvcSpec extends TestKit(
  JavaFlowSvcSpec.boot.actorSystem) with AsyncFlatSpecLike with BeforeAndAfterAll with Matchers {

  val portBindingsF = (Unicomplex(system).uniActor ? PortBindings).mapTo[Map[String, Int]]
  val portF = portBindingsF map { bindings => bindings("default-listener") }

  override def afterAll(): Unit = {
    Unicomplex(system).uniActor ! GracefulStop
  }

  it should "handle a normal request" in {
    for {
      port <- portF
      response <- entityAsString(s"http://127.0.0.1:$port/javaflowsvc/ping")
    } yield {
      response shouldBe "pong"
    }
  }

  it should "handle a chunked request and be able to provide a chunked response" in {
    val requestChunks = Source.single("Hi this is a test")
      .mapConcat { s => s.split(' ').toList }
      .map(HttpEntity.ChunkStreamPart(_))

    for {
      port <- portF
      response <- post(s"http://127.0.0.1:$port/javaflowsvc/chunks",
                       Chunked(ContentTypes.`text/plain(UTF-8)`, requestChunks))
      responseString <- response.entity.dataBytes.map(_.utf8String).toMat(Sink.fold("") { _ + _})(Keep.right).run()
    } yield {
      response.entity shouldBe 'chunked
      responseString should be("Received 5 chunks and 13 bytes.\r\nThis is the last chunk!")
    }
  }

  it should "get an InternalServerError with blank response if Flow collapses" in {
    for {
      port <- portF
      errResp <- get(s"http://127.0.0.1:$port/javaflowsvc/throwit")
      respEntity <- errResp.entity.toStrict(awaitMax)
    } yield {
      errResp.status shouldBe StatusCodes.InternalServerError
      respEntity.data.utf8String shouldBe empty
    }
  }
}
