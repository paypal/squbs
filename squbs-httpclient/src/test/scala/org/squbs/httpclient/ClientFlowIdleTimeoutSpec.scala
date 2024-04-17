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
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.stream.scaladsl.{Sink, Source, TcpIdleTimeoutException}
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.squbs.resolver.ResolverRegistry
import org.squbs.testkit.Timeouts.awaitMax

import scala.concurrent.{Await, Promise}
import scala.util.{Failure, Success}

object ClientFlowIdleTimeoutSpec {

  val config = ConfigFactory.parseString(
    """
      |pekko {
      |  loggers = [
      |    "org.apache.pekko.event.Logging$DefaultLogger"
      |  ]
      |
      |  loglevel = "DEBUG"
      |
      |  http {
      |    server {
      |      idle-timeout = 240 s
      |      request-timeout = 120 s
      |    }
      |
      |    client.idle-timeout = 1 s
      |
      |    host-connection-pool.max-retries = 0
      |  }
      |}
    """.stripMargin)

  implicit val system = ActorSystem("ClientFlowIdleTimeoutSpec", config)

  ResolverRegistry(system).register[HttpEndpoint]("LocalhostEndpointResolver") { (svcName, _) => svcName match {
    case "slow" => Some(HttpEndpoint(s"http://localhost:$port"))
    case _ => None
  }}

  import org.apache.pekko.http.scaladsl.server.Directives._

  val route =
    path("slow") {
      get {
        val promise = Promise[String]()
        // Never completing the promise
        onComplete(promise.future) {
          case Success(value) => complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "Slow...!"))
          case Failure(ex)    => complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "Slow failed...!"))
        }
      }
    }

  val serverBinding = Await.result(Http().newServerAt("localhost", 0).bind(route), awaitMax)
  val port = serverBinding.localAddress.getPort
}

class ClientFlowIdleTimeoutSpec  extends AsyncFlatSpec with Matchers with BeforeAndAfterAll {

  import ClientFlowIdleTimeoutSpec._

  override def afterAll(): Unit = {
    serverBinding.unbind() map {_ => system.terminate()}
  }

  it should "drop the connection after idle-timeout and resume the stream with new connections" in {
    val clientFlow = ClientFlow[Int]("slow")

    val result =
      Source(1 to 10)
        .map(HttpRequest(uri = "/slow") -> _)
        .via(clientFlow)
        .runWith(Sink.seq)

    result map { r =>
      val failures = r.map(_._1).filter(_.isFailure).map(_.failed)
      failures should have size 10
      failures.forall(_.get.isInstanceOf[TcpIdleTimeoutException]) shouldBe true
      r.map(_._2) should contain theSameElementsAs(1 to 10)
    }
  }
}