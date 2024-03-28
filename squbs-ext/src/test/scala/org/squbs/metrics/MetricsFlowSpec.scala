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

package org.squbs.metrics

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.ws.PeerClosedConnectionException
import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}
import org.apache.pekko.testkit.TestKit
import org.scalatest.OptionValues._
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.pipeline.RequestContext

import java.lang.management.ManagementFactory
import javax.management.ObjectName
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

  it should "collect request count and time metrics" in {
    val future = Source(hello :: hello :: Nil).
      via(MetricsFlow("sample").join(dummyEndpoint)).
      runWith(Sink.ignore)

    future map { _ =>
      jmxValue("sample-request-count", "Count").value shouldBe 2
      jmxValue("sample-request-time", "Count").value shouldBe 2
      jmxValue("sample-request-time", "FifteenMinuteRate") should not be empty
    }
  }

  it should "collect metrics per http status code category" in {
    val future = Source(hello :: hello :: redirect :: notFound :: internalServerError :: internalServerError :: Nil).
      via(MetricsFlow("sample2").join(dummyEndpoint)).
      runWith(Sink.ignore)

    future map { _ =>
      jmxValue("sample2-request-count", "Count").value shouldBe 6
      jmxValue("sample2-2XX-count", "Count").value shouldBe 2
      jmxValue("sample2-3XX-count", "Count").value shouldBe 1
      jmxValue("sample2-4XX-count", "Count").value shouldBe 1
      jmxValue("sample2-5XX-count", "Count").value shouldBe 2
    }
  }

  it should "be able to collect multiple metrics" in {

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
      jmxValue("sample3-request-count", "Count").value shouldBe 4
      jmxValue("sample3-2XX-count", "Count").value shouldBe 2
      jmxValue("sample3-5XX-count", "Count").value shouldBe 2

      jmxValue("sample4-request-count", "Count").value shouldBe 1
      jmxValue("sample4-3XX-count", "Count").value shouldBe 1

      jmxValue("sample5-request-count", "Count").value shouldBe 1
      jmxValue("sample5-4XX-count", "Count").value shouldBe 1
    }
  }

  it should "collect metrics for exceptions" in {

    val future = Source(hello :: hello ::
      connectionException :: connectionException :: timeoutException :: timeoutException :: Nil).
      via(MetricsFlow("sample6").join(dummyEndpoint)).
      runWith(Sink.ignore)

    future map { _ =>
      jmxValue("sample6-request-count", "Count").value shouldBe 6
      jmxValue("sample6-2XX-count", "Count").value shouldBe 2
      jmxValue("sample6-PeerClosedConnectionException-count", "Count").value shouldBe 2
      jmxValue("sample6-RequestTimeoutException-count", "Count").value shouldBe 2
    }
  }

  def jmxValue(beanName: String, key: String): Option[AnyRef] = {
    val oName =
      ObjectName.getInstance(s"${MetricsExtension(system).Domain}:name=${MetricsExtension(system).Domain}.$beanName")
    Option(ManagementFactory.getPlatformMBeanServer.getAttribute(oName, key))
  }
}
