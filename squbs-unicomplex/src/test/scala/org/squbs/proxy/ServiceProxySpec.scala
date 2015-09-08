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

package org.squbs.proxy

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.io.IO
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest._
import org.scalatest.concurrent.AsyncAssertions
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.UnicomplexBoot.StartupType
import org.squbs.unicomplex.{JMX, Timeouts, Unicomplex, UnicomplexBoot}
import spray.can.Http
import spray.client.pipelining._
import spray.http.{HttpRequest, HttpResponse, _}
import spray.util._

import scala.util.Try

object ServiceProxySpec {

  val dummyJarsDir = getClass.getClassLoader.getResource("classpaths").getPath

  val classPaths = Array(
    "ServiceProxyRoute",
    "ServiceProxyActor",
    "PipedServiceProxyActor"
  ) map (dummyJarsDir + "/" + _)

  val (_, port) = Utils.temporaryServerHostnameAndPort()

  val config = ConfigFactory.parseString(
    s"""
       |squbs {
       |  actorsystem-name = ServiceProxySpec
       |  ${JMX.prefixConfig} = true
       |}
       |default-listener.bind-port = $port
     """.stripMargin
  ) withFallback ConfigFactory.parseString(
    """
      |  default-proxy {
      |    type = squbs.proxy
      |    processorFactory = org.squbs.proxy.pipedserviceproxyactor.DummyPipedProcessorFactoryForActor
      |  }
      |
      |  MyProxy1 {
      |    type = squbs.proxy
      |    processorFactory = org.squbs.proxy.serviceproxyactor.DummyProcessorForActor
      |    settings = {
      |
      |    }
      |  }
      |
      |  MyProxy2 {
      |    type = squbs.proxy
      |    processorFactory = org.squbs.proxy.serviceproxyroute.DummyProcessorForRoute
      |  }
			|
			|
      |
      |confproxy {
      |    type = squbs.proxy
      |    processorFactory = org.squbs.pipeline.SimpleProcessorFactory
      |    settings = {
      |      inbound = [confhandler1, javaReqHandler, confhandler2, confhandlerempty]
      |      outbound = [javaRespHandler, confhandler3]
      |    }
      |}
      |
      |confpipe {
      |    type = squbs.proxy
      |    factory = org.squbs.pipeline.SimpleProcessorFactory
      |    settings = {
      |      inbound = [confhandler1, javaReqHandler, confhandler2, confhandlerempty]
      |      outbound = [javaRespHandler, confhandler3]
      |    }
      |}
      |
      |javaReqHandler {
      |    type = pipeline.handler
      |    factory = org.squbs.proxy.japi.JavaRequestHandlerFactory
      |}
      |
      |javaRespHandler {
      |    type = pipeline.handler
      |    factory = org.squbs.proxy.japi.JavaResponseHandlerFactory
      |}
      |
      |confhandler1 {
      |    type = pipeline.handler
      |    factory = org.squbs.proxy.pipedserviceproxyactor.confhandler1
      |}
      |confhandler2 {
      |    type = pipeline.handler
      |    factory = org.squbs.proxy.pipedserviceproxyactor.confhandler2
      |}
      |confhandler3 {
      |    type = pipeline.handler
      |    factory = org.squbs.proxy.pipedserviceproxyactor.confhandler3
      |}
      |confhandlerempty {
      |    type = pipeline.handler
      |    factory = org.squbs.proxy.pipedserviceproxyactor.confhandlerEmpty
      |}
      |spray {
      |  can {
      |    client {
      |      response-chunk-aggregation-limit = 0
      |    }
      |  }
      |}
			|
			|akka {
			|  loglevel = DEBUG
			|}
    """.stripMargin
  )

  val boot = UnicomplexBoot(config)
    .createUsing {
    (name, config) => ActorSystem(name, config)
  }
    .scanComponents(classPaths)
    .initExtensions.start()
	Thread.sleep(2000L)
}

class ServiceProxySpec extends TestKit(ServiceProxySpec.boot.actorSystem) with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll
with AsyncAssertions {

  import org.squbs.proxy.ServiceProxySpec._

  implicit val timeout: akka.util.Timeout =
    Try(System.getProperty("test.timeout").toLong) map {
      millis =>
        akka.util.Timeout(millis, TimeUnit.MILLISECONDS)
    } getOrElse Timeouts.askTimeout

  val port = system.settings.config getInt "default-listener.bind-port"

  implicit val executionContext = system.dispatcher

  override def afterAll() {
    Unicomplex(system).uniActor ! GracefulStop
  }

  val interface = "127.0.0.1"
  //val connect = Http.Connect(interface, port)

  val hostConnector = Http.HostConnectorSetup(interface, port)
  val Http.HostConnectorInfo(connector, _) = IO(Http).ask(hostConnector).await

  "UnicomplexBoot" must {

    "start all cube actors" in {
      val w = new Waiter

      system.actorSelection("/user/ServiceProxyRoute").resolveOne().onComplete {
        result =>
          w {
            assert(result.isSuccess)
          }
          w.dismiss()
      }
      w.await()

      system.actorSelection("/user/ServiceProxyActor").resolveOne().onComplete {
        result =>
          w {
            assert(result.isSuccess)
          }
          w.dismiss()
      }
      w.await()

      system.actorSelection("/user/PipedServiceProxyActor").resolveOne().onComplete {
        result =>
          w {
            assert(result.isSuccess)
          }
          w.dismiss()
      }
      w.await()

      system.actorSelection("/user/ServiceProxyRoute/serviceproxyroute-ServiceProxyRoute-route").resolveOne().onComplete {
        result =>
          w {
            assert(result.isSuccess)
          }
          w.dismiss()
      }
      w.await()

      system.actorSelection("/user/ServiceProxyActor/serviceproxyactor-ServiceProxyActor-handler").resolveOne().onComplete {
        result =>
          w {
            assert(result.isSuccess)
          }
          w.dismiss()
      }
      w.await()

      system.actorSelection("/user/PipedServiceProxyActor/pipedserviceproxyactor-PipedServiceProxyActor-handler").resolveOne().onComplete {
        result =>
          w {
            assert(result.isSuccess)
          }
          w.dismiss()
      }
      w.await()

      system.actorSelection("/user/PipedServiceProxyActor/pipedserviceproxyactor1-PipedServiceProxyActor-handler").resolveOne().onComplete {
        result =>
          w {
            assert(result.isSuccess)
          }
          w.dismiss()
      }
      w.await()

			system.actorSelection("/user/PipedServiceProxyActor/pipedserviceproxyactor2-PipelineProcessorActor-handler").resolveOne().onComplete {
				result =>
					w {
						assert(result.isSuccess)
					}
					w.dismiss()
			}
			w.await()

      system.actorSelection("/user/PipedServiceProxyActor/pipedserviceproxyactor3-PipelineProcessorActor-handler").resolveOne().onComplete {
        result =>
          w {
            assert(result.isSuccess)
          }
          w.dismiss()
      }
      w.await()

    }


    "start all services" in {
      val services = boot.cubes flatMap {
        cube => cube.components.getOrElse(StartupType.SERVICES, Seq.empty)
      }
      assert(services.size == 6)

      IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/serviceproxyroute/msg/hello"))
      within(timeout.duration) {
        val response = expectMsgType[HttpResponse]
        response.status should be(StatusCodes.OK)
        response.entity.asString should be("helloeBay")
        response.headers.find(h => h.name.equals("dummyRespHeader")).get.value should be("CCOE")
      }

      IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/serviceproxyactor/msg/hello"))
      within(timeout.duration) {
        val response = expectMsgType[HttpResponse]
        response.status should be(StatusCodes.OK)
        response.entity.asString should be("PayPal")
        response.headers.find(h => h.name.equals("dummyRespHeader")).get.value should be("CDC")
      }

			val baseReq = HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/pipedserviceproxyactor/msg/hello"))

      IO(Http) ! baseReq
      within(timeout.duration) {
        val response = expectMsgType[HttpResponse]
        response.status shouldBe StatusCodes.OK
        response.entity.asString shouldBe "PayPaleBay"
        response.headers.find(h => h.name.equals("dummyRespHeader1")).get.value shouldBe "CDC"
        response.headers.find(h => h.name.equals("dummyRespHeader2")).get.value shouldBe "CCOE"
      }

      IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/pipedserviceproxyactor1/msg/hello"))
      within(timeout.duration) {
        val response = expectMsgType[HttpResponse]
        response.status should be(StatusCodes.OK)
        response.entity.asString should be("No custom header found")
        response.headers.find(h => h.name.equals("dummyRespHeader1")) should be(None)
        response.headers.find(h => h.name.equals("dummyRespHeader2")) should be(None)
      }

      val confReq = HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/pipedserviceproxyactor2/msg/hello"))

      IO(Http) ! confReq
      within(timeout.duration) {
        val response = expectMsgType[HttpResponse]
        response.status shouldBe StatusCodes.OK
        response.entity.asString shouldBe "Found conf handler"
        val header1 = response.headers.find(h => h.name == "found")
        header1 should not be None
        header1.get.value shouldBe "true"
        val header2 = response.headers.find(h => h.name == "confhandler2")
        header2 should not be None
        header2.get.value shouldBe "PayPal"
        val header3 = response.headers.find(h => h.name == "confhandler3")
        header3 should not be None
        header3.get.value shouldBe "dummy"
        val header4 = response.headers.find(h => h.name == "JavaRequestHandler")
        header4 should not be None
        header4.get.value shouldBe "JavaRequestHandler"
        val header5 = response.headers.find(h => h.name == "JavaResponseHandler")
        header5 should not be None
        header5.get.value shouldBe "JavaResponseHandler"
        val header6 = response.headers.find(h => h.name == "someAttr")
        header6 should not be None
        header6.get.value shouldBe "[AttrValue]"
      }

      val confReq2 = HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/pipedserviceproxyactor3/msg/hello"))

      IO(Http) ! confReq2
      within(timeout.duration) {
        val response = expectMsgType[HttpResponse]
        response.status shouldBe StatusCodes.OK
        response.entity.asString shouldBe "Found conf handler"
        val header1 = response.headers.find(h => h.name == "found")
        header1 should not be None
        header1.get.value shouldBe "true"
        val header2 = response.headers.find(h => h.name == "confhandler2")
        header2 should not be None
        header2.get.value shouldBe "PayPal"
        val header3 = response.headers.find(h => h.name == "confhandler3")
        header3 should not be None
        header3.get.value shouldBe "dummy"
        val header4 = response.headers.find(h => h.name == "JavaRequestHandler")
        header4 should not be None
        header4.get.value shouldBe "JavaRequestHandler"
        val header5 = response.headers.find(h => h.name == "JavaResponseHandler")
        header5 should not be None
        header5.get.value shouldBe "JavaResponseHandler"
        val header6 = response.headers.find(h => h.name == "someAttr")
        header6 should not be None
        header6.get.value shouldBe "[AttrValue]"
      }

      println("Success......")
    }

    "failed in processing request" in {
      IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/serviceproxyactor/msg/processingRequestError"))
      within(timeout.duration) {
        val response = expectMsgType[HttpResponse]
        response.status should be(StatusCodes.InternalServerError)
        response.entity.asString should be("BadMan")
      }
    }

    "chunk response" in {
      IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/serviceproxyactor/msg/hello-chunk"))
      within(timeout.duration) {
        val responseStart = expectMsgType[ChunkedResponseStart]
        val response = responseStart.response
        response.status should be(StatusCodes.OK)
        response.entity.asString should be("")
        response.headers.find(h => h.name.equals("dummyRespHeader")).get.value should be("CDC")

        expectMsg(MessageChunk("PayPal"))
        expectMsg(MessageChunk("1a"))
        expectMsg(MessageChunk("2a"))
        expectMsg(MessageChunk("3a"))
        expectMsg(ChunkedMessageEnd("abc"))
      }
    }

    "chunk response with confirm" in {
      IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/serviceproxyactor/msg/hello-chunk-confirm"))
      within(timeout.duration) {
        val responseStart = expectMsgType[ChunkedResponseStart]
        val response = responseStart.response
        response.status should be(StatusCodes.OK)
        response.entity.asString should be("")
        response.headers.find(h => h.name.equals("dummyRespHeader")).get.value should be("CDC")

        expectMsg(MessageChunk("PayPal"))
        expectMsg(MessageChunk("0a"))
        expectMsg(MessageChunk("1a"))
        expectMsg(MessageChunk("2a"))
        expectMsg(MessageChunk("3a"))
        expectMsg(ChunkedMessageEnd("123"))
      }
    }

    "chunk request with RegisterChunkHandler" in {
      val actor_jar_path = ServiceProxySpec.getClass.getResource("/classpaths/StreamSvc/akka-actor_2.10-2.3.2.jar1").getPath
      val actorFile = new java.io.File(actor_jar_path)
      println("stream file path:" + actor_jar_path)
      println("Exists:" + actorFile.exists())
      println("Can Read:" + actorFile.canRead)
      require(actorFile.exists() && actorFile.canRead)
      val fileLength = actorFile.length()

      val chunks = HttpData(actorFile).toChunkStream(65000)
      val parts = chunks.zipWithIndex.flatMap {
        case (httpData, index) => Seq(BodyPart(HttpEntity(httpData), s"segment-$index"))
      } toSeq


      val multipartFormData = MultipartFormData(parts)

      val response = spray.client.HttpDialog(connector)
        .send(Post(uri = "/serviceproxyactor/file-upload", content = multipartFormData))
        .end
        .await(timeout)

      //println(response.entity.data.length)
      //println(response)

      response.entity.data.length should be(fileLength)

      response.headers.find(h => h.name.equals("dummyReqHeader")).get.value should be("PayPal")
      response.headers.find(h => h.name.equals("dummyRespHeader")).get.value should be("CDC")
    }
  }
}