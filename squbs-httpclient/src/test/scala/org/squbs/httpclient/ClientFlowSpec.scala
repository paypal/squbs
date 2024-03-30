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
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.util.ByteString
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.squbs.resolver._
import org.squbs.testkit.Timeouts._

import java.lang.management.ManagementFactory
import javax.management.ObjectName
import scala.concurrent.{Await, Future}
import scala.util.{Success, Try}

object ClientFlowSpec {

  implicit val system = ActorSystem("ClientFlowSpec")

  ResolverRegistry(system).register[HttpEndpoint]("LocalhostEndpointResolver") { (svcName, _) => svcName match {
    case "hello" => Some(HttpEndpoint(s"http://localhost:$port"))
    case _ => None
  }}

  import org.apache.pekko.http.scaladsl.server.Directives._

  val route =
    path("hello") {
      get {
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "Hello World!"))
      }
    }

  val serverBinding = Await.result(Http().newServerAt("localhost", 0).bind(route), awaitMax)
  val port = serverBinding.localAddress.getPort
}

class ClientFlowSpec  extends AsyncFlatSpec with Matchers with BeforeAndAfterAll {

  import ClientFlowSpec._

  override def afterAll(): Unit = {
    serverBinding.unbind() map {_ => system.terminate()}
  }

  it should "make a call to Hello Service" in {
    val clientFlow = ClientFlow[Int]("hello")
    val responseFuture: Future[(Try[HttpResponse], Int)] =
      Source.single(HttpRequest(uri = "/hello") -> 42)
        .via(clientFlow)
        .runWith(Sink.head)

    val (Success(response), _) = Await.result(responseFuture, awaitMax)
    response.status should be (StatusCodes.OK)
    val entity = response.entity.dataBytes.runFold(ByteString(""))(_ ++ _) map(_.utf8String)
    entity map { e => e shouldEqual "Hello World!" }
  }

  it should "register the default http endpoint resolver" in {
    val clientFlow = ClientFlow[Int](s"http://localhost:$port")
    val responseFuture: Future[(Try[HttpResponse], Int)] =
      Source.single(HttpRequest(uri = "/hello") -> 42)
        .via(clientFlow)
        .runWith(Sink.head)

    val (Success(response), _) = Await.result(responseFuture, awaitMax)
    response.status should be (StatusCodes.OK)
    val entity = response.entity.dataBytes.runFold(ByteString(""))(_ ++ _) map(_.utf8String)
    entity map { e => e shouldEqual "Hello World!" }
  }

  it should "register the default http endpoint resolver for each actor system" in {
    implicit val system = ActorSystem("ClientFlowSecondSpec")
    ClientFlow[Int]("https://pekko.io")
    Future { ClientFlow.defaultResolverRegistrationRecord.size should be >= 2 }
  }

  it should "throw HttpClientEndpointNotExistException if it cannot resolve the client" in {
    an [HttpClientEndpointNotExistException] should be thrownBy {
      ClientFlow[Int]("cannotResolve")
    }
  }

  it should "use default for the scheme" in {
    // https://github.com/paypal/squbs/issues/616
    ClientFlow[Int]("http://wwww.google.com:80")
    Future {
      assertJmxValue("http://wwww.google.com:80", "EndpointUri", "http://wwww.google.com")
      assertJmxValue("http://wwww.google.com:80", "EndpointPort", "80")
    }
  }

  def assertJmxValue(clientName: String, key: String, expectedValue: Any) = {
    val oName = ObjectName.getInstance(
      s"org.squbs.configuration.${system.name}:type=squbs.httpclient,name=${ObjectName.quote(clientName)}")
    val actualValue = ManagementFactory.getPlatformMBeanServer.getAttribute(oName, key)
    actualValue shouldEqual expectedValue
  }
}
