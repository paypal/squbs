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
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterAll, Matchers}
import org.squbs.resolver._
import org.squbs.testkit.Timeouts._

import scala.concurrent.{Await, Future}
import scala.util.{Success, Try}

object ClientFlowSpec {

  implicit val system = ActorSystem("ClientFlowSpec")
  implicit val materializer = ActorMaterializer()

  ResolverRegistry(system).register[HttpEndpoint]("LocalhostEndpointResolver") { (svcName, _) => svcName match {
    case "hello" => Some(HttpEndpoint(s"http://localhost:$port"))
    case _ => None
  }}

  import akka.http.scaladsl.server.Directives._
  import system.dispatcher

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

  it should "throw HttpClientEndpointNotExistException if it cannot resolve the client" in {
    an [HttpClientEndpointNotExistException] should be thrownBy {
      ClientFlow[Int]("cannotResolve")
    }
  }
}
