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

import akka.actor.ActorSystem
import akka.stream.scaladsl.{BidiFlow, Flow}
import com.codahale.metrics.{MetricRegistry, Timer}
import org.squbs.pipeline.RequestContext

import scala.util.{Failure, Success, Try}

object MetricsFlow {

  def apply(name: String)(implicit system: ActorSystem) = {

    val domain = MetricsExtension(system).Domain
    val metrics = MetricsExtension(system).metrics

    val requestCount = MetricRegistry.name(domain, s"$name-request-count")
    val requestTime = MetricRegistry.name(domain, s"$name-request-time")
    val count2XX = MetricRegistry.name(domain, s"$name-2XX-count")
    val count3XX = MetricRegistry.name(domain, s"$name-3XX-count")
    val count4XX = MetricRegistry.name(domain, s"$name-4XX-count")
    val count5XX = MetricRegistry.name(domain, s"$name-5XX-count")

    val inbound = Flow[RequestContext].map { rc =>
      metrics.meter(requestCount).mark()
      rc ++ (requestTime -> metrics.timer(requestTime).time())
    }

    val outbound = Flow[RequestContext].map { rc =>
      rc.attribute[Timer.Context](requestTime) map {_.stop()}

      rc.response map {
        case Success(response) =>
          val statusCode = response.status.intValue()

          if(statusCode >= 500) metrics.meter(count5XX).mark()
          else if(statusCode >= 400) metrics.meter(count4XX).mark()
          else if(statusCode >= 300) metrics.meter(count3XX).mark()
          else if(statusCode >= 200) metrics.meter(count2XX).mark()
        case Failure(ex) =>
          val causeExceptionClass = Try(ex.getCause.getClass.getSimpleName) getOrElse ex.getClass.getSimpleName
          metrics.meter(MetricRegistry.name(domain, s"$name-$causeExceptionClass-count")).mark()
      }

      rc
    }

    BidiFlow.fromFlows(inbound, outbound)
  }
}
