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

package org.squbs.metrics

import java.lang.management.ManagementFactory
import javax.management.ObjectName

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.PeerClosedConnectionException
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source, Flow}
import akka.testkit.TestKit
import org.scalatest.{AsyncFlatSpecLike, Matchers}
import org.squbs.pipeline.streaming.RequestContext

import scala.concurrent.Future
import scala.util.{Failure, Try}

class MetricsFlowSpec extends TestKit(ActorSystem("MetricsFlowSpec")) with AsyncFlatSpecLike with Matchers {

  val dummyEndpoint = Flow[RequestContext].map { r =>
    r.request.getUri().path() match {
      case "/hello" => r.copy(response = Some(Try(HttpResponse(entity = "Hello World!"))))
      case "/redirect" => r.copy(response = Some(Try(HttpResponse(status = StatusCodes.Found))))
      case "/notfound" => r.copy(response = Some(Try(HttpResponse(status = StatusCodes.NotFound))))
      case "/internalservererror" => r.copy(response = Some(Try(HttpResponse(StatusCodes.InternalServerError))))
      case "/connectionException" => r.copy(response = Some(Failure(new  PeerClosedConnectionException(0, ""))))
      case "/timeoutException" => r.copy(response =
        Some(Failure(new  RuntimeException(RequestTimeoutException(r.request, "")))))
    }
  }

  val hello = RequestContext(HttpRequest(uri = "/hello"), 0)
  val redirect = RequestContext(HttpRequest(uri = "/redirect"), 0)
  val notFound = RequestContext(HttpRequest(uri = "/notfound"), 0)
  val internalServerError = RequestContext(HttpRequest(uri = "/internalservererror"), 0)
  val connectionException = RequestContext(HttpRequest(uri = "/connectionException"), 0)
  val timeoutException = RequestContext(HttpRequest(uri = "/timeoutException"), 0)

  implicit val materializer = ActorMaterializer()

  it should "collect request count and time metrics" in {
    val future = Source(hello :: hello :: Nil).
      via(MetricsFlow("sample").join(dummyEndpoint)).
      runWith(Sink.ignore)

    future map { _ =>
      assertJmxValue("sample-request-count", "Count", 2)
      assertJmxValue("sample-request-time", "Count", 2)
      assertJmxEntryExists("sample-request-time", "FifteenMinuteRate")
    }
  }

  it should "collect metrics per http status code category" in {
    val future = Source(hello :: hello :: redirect :: notFound :: internalServerError :: internalServerError :: Nil).
      via(MetricsFlow("sample2").join(dummyEndpoint)).
      runWith(Sink.ignore)

    future map { _ =>
      assertJmxValue("sample2-request-count", "Count", 6)
      assertJmxValue("sample2-2XX-count", "Count", 2)
      assertJmxValue("sample2-3XX-count", "Count", 1)
      assertJmxValue("sample2-4XX-count", "Count", 1)
      assertJmxValue("sample2-5XX-count", "Count", 2)
    }
  }

  it should "collect metrics for multiple clients" in {

    val f1 = Source(hello :: hello :: internalServerError :: internalServerError :: Nil).
      via(MetricsFlow("sample3").join(dummyEndpoint)).
      runWith(Sink.ignore)

    val f2 = Source.single(redirect).
      via(MetricsFlow("sample4").join(dummyEndpoint)).
      runWith(Sink.ignore)

    val f3 = Source.single(notFound).
      via(MetricsFlow("sample5").join(dummyEndpoint)).
      runWith(Sink.ignore)

    val f = Future.sequence(List(f1, f2, f3))

    f map { _ =>
      assertJmxValue("sample3-request-count", "Count", 4)
      assertJmxValue("sample3-2XX-count", "Count", 2)
      assertJmxValue("sample3-5XX-count", "Count", 2)

      assertJmxValue("sample4-request-count", "Count", 1)
      assertJmxValue("sample4-3XX-count", "Count", 1)

      assertJmxValue("sample5-request-count", "Count", 1)
      assertJmxValue("sample5-4XX-count", "Count", 1)
    }
  }

  it should "collect metrics for exceptions" in {

    val future = Source(hello :: hello ::
      connectionException :: connectionException :: timeoutException :: timeoutException :: Nil).
      via(MetricsFlow("sample6").join(dummyEndpoint)).
      runWith(Sink.ignore)

    future map { _ =>
      assertJmxValue("sample6-request-count", "Count", 6)
      assertJmxValue("sample6-2XX-count", "Count", 2)
      assertJmxValue("sample6-PeerClosedConnectionException-count", "Count", 2)
      assertJmxValue("sample6-RequestTimeoutException-count", "Count", 2)
    }
  }

  def assertJmxValue(beanName: String, key: String, expectedValue: Any) = {
    val oName = ObjectName.getInstance(s"${MetricsExtension(system).Domain}:name=${MetricsExtension(system).Domain}.$beanName")
    val actualValue = ManagementFactory.getPlatformMBeanServer.getAttribute(oName, key)
    actualValue shouldEqual expectedValue
  }

  def assertJmxEntryExists(beanName: String, key: String) = {
    val oName = ObjectName.getInstance(s"${MetricsExtension(system).Domain}:name=${MetricsExtension(system).Domain}.$beanName")
    val actualValue = ManagementFactory.getPlatformMBeanServer.getAttribute(oName, key)
    actualValue.toString should not be empty
  }
}
