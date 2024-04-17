/*
 * Copyright 2018 PayPal
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
import org.apache.pekko.http.scaladsl.Http.ServerBinding
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}
import org.apache.pekko.http.scaladsl.{ClientTransport, ConnectionContext, Http}
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import com.typesafe.config.ConfigFactory
import io.netty.handler.codec.http
import io.netty.handler.codec.http.HttpObject
import org.littleshoot.proxy.impl.DefaultHttpProxyServer
import org.littleshoot.proxy.{HttpFilters, HttpFiltersAdapter, HttpFiltersSourceAdapter, HttpProxyServer}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.squbs.httpclient.ClientFlowHttpsSpec.InsecureSSLEngineProvider
import org.squbs.resolver.ResolverRegistry
import org.squbs.testkit.Timeouts._

import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Success, Try}

object ClientFlowHttpsProxySpec {

  implicit val system = ActorSystem("ClientFlowHttpsProxySpecServers")

  val proxyRequests = new AtomicInteger(0)

  var proxyServer: HttpProxyServer = _
  var proxyPort: Int = _

  private def startServer() = {
    import org.apache.pekko.http.scaladsl.server.Directives._

    val route =
      path("hello") {
        get {
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "Hello World!"))
        }
      }

    Http().newServerAt("localhost", 0)
      .enableHttps(ConnectionContext.httpsServer(sslContext("example.com.jks", "changeit"))).bind(route)
  }

  private def startProxyServer(): Unit = {
    proxyServer = DefaultHttpProxyServer
      .bootstrap()
      .withPort(0)
      .withFiltersSource(new HttpFiltersSourceAdapter {
        override def filterRequest(originalRequest: http.HttpRequest): HttpFilters = {
          new HttpFiltersAdapter(originalRequest) {
            override def clientToProxyRequest(httpObject: HttpObject): http.HttpResponse = {
              proxyRequests.incrementAndGet()
              super.clientToProxyRequest(httpObject)
            }
          }
        }
      })
      .start()
    proxyPort = proxyServer.getListenAddress.getPort
  }

  def startServers(): Future[ServerBinding] = {
    startProxyServer()
    startServer()
  }
}

class ClientFlowHttpsProxySpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll
  with BeforeAndAfterEach with ScalaFutures {

  val serverBinding = Await.result(ClientFlowHttpsProxySpec.startServers(), awaitMax)
  val serverPort = serverBinding.localAddress.getPort

  class TestSystem(val systemName: String, val configText: String) {

    val serverBinding = Await.result(ClientFlowHttpsProxySpec.startServers(), awaitMax)
    val serverPort = serverBinding.localAddress.getPort

    val config = ConfigFactory.parseString(configText)

    implicit val system = ActorSystem(systemName, config)


    ResolverRegistry(system).register[HttpEndpoint]("LocalhostHttpsEndpointResolver") { (name, _) =>
      name match {
        case "helloHttps" =>
          Some(HttpEndpoint(s"https://localhost:$serverPort",
            Some(sslContext("exampletrust.jks", "changeit")), None, Some(InsecureSSLEngineProvider)))
        case _ => None
      }
    }
  }

  override def afterAll(): Unit = {
    import ClientFlowHttpsProxySpec._
    import system.dispatcher
    serverBinding.unbind().onComplete { _ =>
      system.terminate()
      proxyServer.stop()
    }
  }

  override def beforeEach(): Unit = {
    ClientFlowHttpsProxySpec.proxyRequests.set(0)
  }

  it should "honor client-level proxy settings over global settings" in {
    val testSystem = new TestSystem("ClientFlowHttpsProxySpec1",
      s"""
         |helloHttps {
         |  type = squbs.httpclient
         |  pekko.http.client.proxy {
         |    https {
         |      host = localhost
         |      port = ${ClientFlowHttpsProxySpec.proxyPort}
         |    }
         |  }
         |}
         |
         |// This global entry is just here to make sure when a client-level one is specified,
         |// it is honored instead of the global one.
         |pekko.http.client.proxy {
         |  https {
         |    host = doesnotexisthost
         |    port = 80
         |  }
         |}
       """.stripMargin
    )
    import testSystem._

    val clientFlow = ClientFlow[Int]("helloHttps")
    val responseFuture: Future[(Try[HttpResponse], Int)] =
      Source.single(HttpRequest(uri = "/hello") -> 42)
        .via(clientFlow)
        .runWith(Sink.head)

    val (Success(response), _) = Await.result(responseFuture, awaitMax)
    val result = response.entity.toStrict(10.seconds).futureValue.data.utf8String
    system.terminate()

    response.status shouldBe StatusCodes.OK
    result shouldBe "Hello World!"
    ClientFlowHttpsProxySpec.proxyRequests.get shouldBe 1
  }

  it should "honor global proxy settings when no client-level settings are available" in {
    val testSystem = new TestSystem("ClientFlowHttpsProxySpec2",
      s"""
         |helloHttps {
         |  type = squbs.httpclient
         |}
         |
         |pekko.http.client.proxy {
         |  https {
         |    host = localhost
         |    port = ${ClientFlowHttpsProxySpec.proxyPort}
         |  }
         |}
       """.stripMargin
    )
    import testSystem._

    val clientFlow = ClientFlow[Int]("helloHttps")
    val responseFuture: Future[(Try[HttpResponse], Int)] =
      Source.single(HttpRequest(uri = "/hello") -> 42)
        .via(clientFlow)
        .runWith(Sink.head)

    val (Success(response), _) = Await.result(responseFuture, awaitMax)
    val result = response.entity.toStrict(10.seconds).futureValue.data.utf8String
    system.terminate()

    response.status shouldBe StatusCodes.OK
    result shouldBe "Hello World!"
    ClientFlowHttpsProxySpec.proxyRequests.get shouldBe 1
  }

  it should "honor programmatic proxy settings over the configuration" in {
    val testSystem = new TestSystem("ClientFlowHttpsProxySpec3",
      s"""
         |helloHttps {
         |  type = squbs.httpclient
         |  pekko.ssl-config.loose.disableHostnameVerification = true
         |}
         |
         |// This global entry is just here to make sure when it is programmatically specified,
         |// that is honored instead of the global one.
         |pekko.http.client.proxy {
         |  https {
         |    host = doesnotexisthost
         |    port = 80
         |  }
         |}
       """.stripMargin
    )
    import testSystem._

    val httpsProxyTransport = ClientTransport.httpsProxy(
      InetSocketAddress.createUnresolved("localHost", ClientFlowHttpsProxySpec.proxyPort))
    val settings = ConnectionPoolSettings(system)
      .withConnectionSettings(ClientConnectionSettings(system)
        .withTransport(httpsProxyTransport))

    val clientFlow = ClientFlow[Int]("helloHttps", settings = Some(settings))
    val responseFuture: Future[(Try[HttpResponse], Int)] =
      Source.single(HttpRequest(uri = "/hello") -> 42)
        .via(clientFlow)
        .runWith(Sink.head)

    val (Success(response), _) = Await.result(responseFuture, awaitMax)
    val result = response.entity.toStrict(10.seconds).futureValue.data.utf8String
    system.terminate()

    response.status shouldBe StatusCodes.OK
    result shouldBe "Hello World!"
    ClientFlowHttpsProxySpec.proxyRequests.get shouldBe 1
  }
}
