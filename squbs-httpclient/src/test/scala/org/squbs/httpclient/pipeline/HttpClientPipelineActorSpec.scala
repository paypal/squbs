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

package org.squbs.httpclient.pipeline

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import org.squbs.httpclient.HttpClientTestKit
import org.squbs.httpclient.dummy.DummyService
import org.squbs.httpclient.endpoint.Endpoint
import org.squbs.pipeline.{SimplePipelineConfig, SimplePipelineResolver}
import org.squbs.testkit.Timeouts._
import spray.http._

import scala.language.postfixOps

class HttpClientPipelineActorSpec extends TestKit(ActorSystem("HttpClientPipelineActorSpec"))
  with ImplicitSender with FlatSpecLike with Matchers with HttpClientTestKit with DummyService with BeforeAndAfterAll {

  import system.dispatcher

  val endpoint = Endpoint(DummyService.dummyServiceEndpoint)
  val processor = SimplePipelineResolver.INSTANCE.create(SimplePipelineConfig.empty, None)
  val pipeline = spray.client.pipelining.sendReceive

  override def beforeAll() {
    startDummyService(system)
  }

  override def afterAll() {
    shutdownActorSystem()
  }

  "HttpClientPipelineActor" should "forward HttpRequest" in {
    val actor = system.actorOf(Props(classOf[HttpClientPipelineActor], "name1", endpoint, processor, pipeline))
    actor ! HttpRequest(uri = s"${DummyService.dummyServiceEndpoint}/view")
    expectMsgType[HttpResponse](awaitMax).status should be (StatusCodes.OK)
    system stop actor
  }

  "HttpClientPipelineActor" should "forward chunked request" in {
    val actor = system.actorOf(Props(classOf[HttpClientPipelineActor], "name2", endpoint, processor, pipeline))
    val request = HttpRequest(
      HttpMethods.POST,
      s"${DummyService.dummyServiceEndpoint}/add",
      entity = HttpEntity("{\"id\":1,\"firstName\":\"John\",\"lastName\":\"Doe\",\"age\":20,\"male\":true}")
    )
    request.asPartStream(16) foreach {actor ! _}
    expectMsgType[HttpResponse](awaitMax).status should be (StatusCodes.InternalServerError)
    system stop actor
  }
}
