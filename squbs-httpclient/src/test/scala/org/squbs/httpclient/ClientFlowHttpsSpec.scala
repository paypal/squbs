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

import java.io.InputStream
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterAll, Matchers}
import org.squbs.resolver.ResolverRegistry
import org.squbs.testkit.Timeouts._

import scala.concurrent.{Await, Future}
import scala.util.{Success, Try}

object ClientFlowHttpsSpec {

  val config = ConfigFactory.parseString(
    """
      |helloHttps {
      |  type = squbs.httpclient
      |  akka.ssl-config.loose.disableHostnameVerification = true
      |}
    """.stripMargin)

  implicit val system = ActorSystem("ClientFlowHttpsSpec", config)
  implicit val materializer = ActorMaterializer()

  ResolverRegistry(system).register[HttpEndpoint]("LocalhostHttpsEndpointResolver") { (name, _) =>
    name match {
      case "helloHttps" =>
        Some(HttpEndpoint(s"https://localhost:$port", Some(sslContext("exampletrust.jks", "changeit"))))
      case _ => None
    }
  }

  import akka.http.scaladsl.server.Directives._
  import system.dispatcher

  val route =
    path("hello") {
      get {
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "Hello World!"))
      }
    }

  val serverBinding = Await.result(Http().bindAndHandle(route, "localhost", 0,
    ConnectionContext.https(sslContext("example.com.jks", "1234567890"))), awaitMax)
  val port = serverBinding.localAddress.getPort

  def sslContext(store: String, pw: String) = {
    val password: Array[Char] = pw.toCharArray // do not store passwords in code, read them from somewhere safe!

    val ks: KeyStore = KeyStore.getInstance("JKS")
    val keystore: InputStream = getClass.getClassLoader.getResourceAsStream("ClientFlowHttpsSpec/" + store)

    require(keystore != null, "Keystore required!")
    ks.load(keystore, password)

    val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(ks, password)

    val tmf: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
    tmf.init(ks)

    val sslContext: SSLContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, new SecureRandom)
    sslContext
  }
}

class ClientFlowHttpsSpec  extends AsyncFlatSpec with Matchers with BeforeAndAfterAll {

  import ClientFlowHttpsSpec._

  override def afterAll: Unit = {
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
