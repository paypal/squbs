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
import org.apache.pekko.stream.scaladsl.{BidiFlow, Flow}
import org.apache.pekko.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import org.apache.pekko.stream.{Attributes, FlowShape, Inlet, Outlet}
import com.codahale.metrics.{MetricRegistry, Timer}
import org.squbs.pipeline.{PipelineFlow, RequestContext}

import scala.util.{Failure, Success, Try}

object MetricsFlow {

  def apply(name: String)(implicit system: ActorSystem): PipelineFlow = {

    val domain = MetricsExtension(system).Domain
    val metrics = MetricsExtension(system).metrics

    val requestCount = MetricRegistry.name(domain, s"$name-request-count")
    val requestTime = MetricRegistry.name(domain, s"$name-request-time")
    val responseCount = MetricRegistry.name(domain, s"$name-response-count")
    val count2XX = MetricRegistry.name(domain, s"$name-2XX-count")
    val count3XX = MetricRegistry.name(domain, s"$name-3XX-count")
    val count4XX = MetricRegistry.name(domain, s"$name-4XX-count")
    val count5XX = MetricRegistry.name(domain, s"$name-5XX-count")

    val inbound = Flow[RequestContext].map { rc =>
      metrics.meter(requestCount).mark()
      rc.withAttribute(requestTime, metrics.timer(requestTime).time())
    }

    val outbound = Flow[RequestContext].map { rc =>
      rc.attribute[Timer.Context](requestTime) map {_.stop()}

      rc.response map {
        case Success(response) =>
          metrics.meter(responseCount).mark()
          val statusCode = response.status.intValue()

          if (statusCode >= 500) metrics.meter(count5XX).mark()
          else if (statusCode >= 400) metrics.meter(count4XX).mark()
          else if (statusCode >= 300) metrics.meter(count3XX).mark()
          else if (statusCode >= 200) metrics.meter(count2XX).mark()
        case Failure(ex) =>
          metrics.meter(responseCount).mark()
          val causeExceptionClass = Try(ex.getCause.getClass.getSimpleName) getOrElse ex.getClass.getSimpleName
          metrics.meter(MetricRegistry.name(domain, s"$name-$causeExceptionClass-count")).mark()
      }

      rc
    }

    BidiFlow.fromFlows(inbound, outbound)
  }
}

object MaterializationMetricsCollector {

  def apply[T](name: String)(implicit system: ActorSystem) = new MaterializationMetricsCollector[T](name)

  /**
    * Java API
    */
  def create[T](name: String, system: ActorSystem) = apply[T](name)(system)
}

class MaterializationMetricsCollector[T] private[metrics] (name: String)(implicit system: ActorSystem)
    extends GraphStage[FlowShape[T, T]] {

  val domain = MetricsExtension(system).Domain
  val metrics = MetricsExtension(system).metrics

  private val in = Inlet[T]("ActiveMaterializationCounter.in")
  private val out = Outlet[T]("ActiveMaterializationCounter.out")

  val activeMaterializationCount = metrics.counter(MetricRegistry.name(domain, s"$name-active-count"))
  val newMaterializationCount = metrics.meter(MetricRegistry.name(domain, s"$name-creation-count"))
  val materializationTerminationCount = metrics.meter(MetricRegistry.name(domain, s"$name-termination-count"))

  override def shape: FlowShape[T, T] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    activeMaterializationCount.inc()
    newMaterializationCount.mark()

    setHandler(in, new InHandler {
      override def onPush(): Unit = push(out, grab(in))

      override def onUpstreamFailure(ex: Throwable): Unit = {
        activeMaterializationCount.dec()
        materializationTerminationCount.mark()
        super.onUpstreamFailure(ex)
      }

      override def onUpstreamFinish(): Unit = {
        activeMaterializationCount.dec()
        materializationTerminationCount.mark()
        super.onUpstreamFinish()
      }
    })

    setHandler(out, new OutHandler {
      override def onPull(): Unit = pull(in)

      override def onDownstreamFinish(cause: Throwable): Unit = {
        activeMaterializationCount.dec()
        materializationTerminationCount.mark()
        super.onDownstreamFinish(cause)
      }
    })
  }
}