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
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.{ConnectionContext, Http}
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.util.ByteString
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.squbs.resolver.ResolverRegistry
import org.squbs.testkit.Timeouts._

import javax.net.ssl.{SSLContext, SSLEngine}
import scala.concurrent.{Await, Future}
import scala.util.{Success, Try}

object ClientFlowHttpsSpec {

  val config = ConfigFactory.parseString(
    """
      |helloHttps {
      |  type = squbs.httpclient
      |  pekko.ssl-config.loose.disableHostnameVerification = true
      |  ssl-engine-factory = xxx
      |}
    """.stripMargin)

  implicit val system = ActorSystem("ClientFlowHttpsSpec", config)

  object InsecureSSLEngineProvider extends SSLEngineProvider {
    override def createSSLEngine(sslContext: SSLContext, hostname: String, port: Int): SSLEngine = {
      val engine = sslContext.createSSLEngine(hostname, port)
      engine.setUseClientMode(true) // Disables endpoint verification - never use in production
      engine
    }
  }

  ResolverRegistry(system).register[HttpEndpoint]("LocalhostHttpsEndpointResolver") { (name, _) =>
    name match {
      case "helloHttps" =>
        Some(HttpEndpoint(s"https://localhost:$port", Some(sslContext("exampletrust.jks", "changeit")),
          None, Some(InsecureSSLEngineProvider)))
      case _ => None
    }
  }

  import org.apache.pekko.http.scaladsl.server.Directives._

  val route =
    path("hello") {
      get {
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "Hello World!"))
      }
    }

  val serverBinding = Await.result(
    Http().newServerAt("localhost", 0)
      .enableHttps(ConnectionContext.httpsServer(sslContext("example.com.jks", "changeit"))).bind(route),
    awaitMax)
  val port = serverBinding.localAddress.getPort
}

class ClientFlowHttpsSpec  extends AsyncFlatSpec with Matchers with BeforeAndAfterAll {

  import ClientFlowHttpsSpec._

  override def afterAll(): Unit = {
    serverBinding.unbind() map {_ => system.terminate()}
  }

  it should "make a call to Hello Service" in {
    val clientFlow = ClientFlow[Int]("helloHttps")
    val responseFuture: Future[(Try[HttpResponse], Int)] =
      Source.single(HttpRequest(uri = "/hello") -> 42)
        .via(clientFlow)
        .runWith(Sink.head)

    val (Success(response), _) = Await.result(responseFuture, awaitMax)
    response.status should be (StatusCodes.OK)
    val entity = response.entity.dataBytes.runFold(ByteString(""))(_ ++ _) map(_.utf8String)
    entity map { e => e shouldEqual "Hello World!" }
  }
}
