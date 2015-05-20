/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the AUTHORS file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.squbs.httpclient.pipeline

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import org.squbs.httpclient.HttpClientTestKit
import org.squbs.httpclient.dummy.DummyService
import org.squbs.httpclient.endpoint.Endpoint
import org.squbs.proxy.SimplePipelineConfig
import spray.http._

import scala.concurrent.duration._

class HttpClientPipelineActorSpec extends TestKit(ActorSystem("HttpClientPipelineActorSpec"))
  with ImplicitSender with FlatSpecLike with Matchers with HttpClientTestKit with DummyService with BeforeAndAfterAll {

  import system.dispatcher
  implicit val timeout: Timeout = 10 seconds

  val endpoint = Endpoint(DummyService.dummyServiceEndpoint)
  val config = SimplePipelineConfig.empty
  val pipeline = spray.client.pipelining.sendReceive

  override def beforeAll {
    startDummyService(system)
  }

  override def afterAll {
    shutdownActorSystem
  }

  "HttpClientPipelineActor" should "forward HttpRequest" in {
    val actor = system.actorOf(Props(classOf[HttpClientPipelineActor], "name1", endpoint, config, pipeline))
    actor ! HttpRequest(uri = s"${DummyService.dummyServiceEndpoint}/view")
    expectMsgType[HttpResponse](timeout.duration).status should be (StatusCodes.OK)
    system stop actor
  }

  "HttpClientPipelineActor" should "forward chunked request" in {
    val actor = system.actorOf(Props(classOf[HttpClientPipelineActor], "name2", endpoint,config, pipeline))
    val request = HttpRequest(
      HttpMethods.POST,
      s"${DummyService.dummyServiceEndpoint}/add",
      entity = HttpEntity("{\"id\":1,\"firstName\":\"Zhuchen\",\"lastName\":\"Wang\",\"age\":20,\"male\":true}")
    )
    request.asPartStream(16) foreach {actor ! _}
    expectMsgType[HttpResponse](timeout.duration).status should be (StatusCodes.InternalServerError)
    system stop actor
  }
}
