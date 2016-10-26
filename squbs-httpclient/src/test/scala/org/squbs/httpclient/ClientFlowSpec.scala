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

package org.squbs.httpclient

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{FlatSpec, BeforeAndAfterAll, AsyncFlatSpec, Matchers}
import org.squbs.endpoint.{Endpoint, EndpointResolver, EndpointResolverRegistry}
import org.squbs.env.Environment
import org.squbs.testkit.Timeouts._

import scala.concurrent.{Future, Await}
import scala.util.{Success, Try}

object ClientFlowSpec {

  implicit val system = ActorSystem("ClientFlowSpec")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher
  import akka.http.scaladsl.server.Directives._

  val route =
    path("hello") {
      get {
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "Hello World!"))
      }
    }

  val serverBinding = Await.result(Http().bindAndHandle(route, "localhost", 0), awaitMax)
  val port = serverBinding.localAddress.getPort
}

class ClientFlowSpec  extends AsyncFlatSpec with Matchers with BeforeAndAfterAll {

  import ClientFlowSpec._

  override def afterAll: Unit = {
    serverBinding.unbind() map {_ => system.terminate()}
  }

  EndpointResolverRegistry(system).register(new EndpointResolver {
    override def name: String = "LocalhostEndpointResolver"

    override def resolve(svcName: String, env: Environment): Option[Endpoint] = svcName match {
      case "hello" => Some(Endpoint(s"http://localhost:$port"))
      case _ => None
    }
  })

  it should "make a call to Hello Service" in {
    val clientFlow = ClientFlow[Int]("hello")
    val responseFuture: Future[(Try[HttpResponse], Int)] =
      Source.single(HttpRequest(uri = "/hello") -> 42)
        .via(clientFlow)
        .runWith(Sink.head)

    val (Success(response), _) = Await.result(responseFuture, awaitMax)
    response.status should be (StatusCodes.OK)
    val entity = response.entity.dataBytes.runFold(ByteString(""))(_ ++ _) map(_.utf8String)
    entity map { e => e shouldEqual ("Hello World!") }
  }

//  it should "not resolve an invalid client" in {
//
//  }
}

class ClientConfigurationSpec extends FlatSpec with Matchers {

  val defaultConfig = ConfigFactory.load()

  it should "give priority to client specific configuration" in {
    val config = ConfigFactory.parseString(
      """
        |sampleclient {
        | type = squbs.httpclient
        |
        | akka.http.host-connection-pool {
        |   max-connections = 987
        |   max-retries = 123
        | }
        |}
      """.stripMargin).withFallback(defaultConfig)

    val cps = ClientFlow.connectionPoolSettings("sampleclient", config, None)
    cps.maxConnections shouldBe 987
    cps.maxRetries shouldBe 123
    cps.idleTimeout.toSeconds should equal (defaultConfig.getDuration("akka.http.host-connection-pool.idle-timeout").getSeconds)
  }

  it should "fallback to default values if no client specific configuration is provided" in {
    assertDefaults("sampleclient", ConfigFactory.empty().withFallback(defaultConfig))
  }

  it should "fallback to default values if client configuration does not override any properties" in {
    val config = ConfigFactory.parseString(
      """
        |sampleclient {
        | type = squbs.httpclient
        |}
      """.stripMargin).withFallback(defaultConfig)

    assertDefaults("sampleclient", config)
  }

  it should "ignore client specific configuration if type is not set to squbs.httpclient" in {
    val config = ConfigFactory.parseString(
      """
        |sampleclient {
        |
        | akka.http.host-connection-pool {
        |   max-connections = 987
        |   max-retries = 123
        | }
        |}
      """.stripMargin).withFallback(defaultConfig)

    assertDefaults("sampleclient", config)
  }

  it should "let configuring multiple clients" in {
    val config = ConfigFactory.parseString(
      """
        |sampleclient {
        | type = squbs.httpclient
        |
        | akka.http.host-connection-pool {
        |   max-connections = 987
        |   max-retries = 123
        | }
        |}
        |
        |sampleclient2 {
        | type = squbs.httpclient
        |
        | akka.http.host-connection-pool {
        |   max-connections = 666
        | }
        |}
      """.stripMargin).withFallback(defaultConfig)

    val cps = ClientFlow.connectionPoolSettings("sampleclient", config, None)
    cps.maxConnections shouldBe 987
    cps.maxRetries shouldBe 123
    cps.idleTimeout.toSeconds should equal (defaultConfig.getDuration("akka.http.host-connection-pool.idle-timeout").getSeconds)

    val cps2 = ClientFlow.connectionPoolSettings("sampleclient2", config, None)
    cps2.maxConnections shouldBe 666
    cps2.maxRetries shouldBe defaultConfig.getInt("akka.http.host-connection-pool.max-retries")
    cps2.idleTimeout.toSeconds should equal (defaultConfig.getDuration("akka.http.host-connection-pool.idle-timeout").getSeconds)

    assertDefaults("sampleclient3", config) //
  }

  private def assertDefaults(clientName: String, config: Config) = {
    val cps = ClientFlow.connectionPoolSettings(clientName, config, None)
    cps.maxConnections shouldEqual defaultConfig.getInt("akka.http.host-connection-pool.max-connections")
    cps.maxRetries shouldEqual defaultConfig.getInt("akka.http.host-connection-pool.max-retries")
    cps.idleTimeout.toSeconds shouldEqual defaultConfig.getDuration("akka.http.host-connection-pool.idle-timeout").getSeconds
  }
}
