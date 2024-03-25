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

package org.squbs.streams

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.event.Logging
import org.apache.pekko.stream._
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.stream.stage._
import com.codahale.metrics.MetricRegistry
import org.squbs.metrics.MetricsExtension

object DemandSupplyMetrics {

  /**
    * Creates a linear [[GraphStage]] that captures (downstream) demand and (upstream) supply metrics.
    *
    * @tparam T the type of the elements flowing from upstream to downstream
    * @param name A name to identify this instance's metric
    * @param system The Actor system
    * @return a [[DemandSupplyMetricsStage]] that can be joined with a [[Flow]] with the corresponding type to capture
    *         demand/supply metrics.
    */
  def apply[T](name: String)(implicit system: ActorSystem): DemandSupplyMetricsStage[T] =
    new DemandSupplyMetricsStage[T](name)
}

/**
  * A linear [[GraphStage]] that is used to capture (downstream) demand and (upstream) supply metrics for
  * backpressure visibility.
  */
class DemandSupplyMetricsStage[T](name: String)(implicit system: ActorSystem) extends GraphStage[FlowShape[T, T]] {

  val domain = MetricsExtension(system).Domain
  val metrics = MetricsExtension(system).metrics

  val in = Inlet[T](Logging.simpleName(this) + ".in")
  val out = Outlet[T](Logging.simpleName(this) + ".out")

  override val shape = FlowShape.of(in, out)

  // naming convention "domain:key-property-list"
  val upstreamCounter = MetricRegistry.name(domain, s"$name-upstream-counter")
  val downstreamCounter = MetricRegistry.name(domain, s"$name-downstream-counter")

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {

      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          val elem = grab(in)
          metrics.meter(upstreamCounter).mark
          push(out, elem)
        }
      })

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          metrics.meter(downstreamCounter).mark
          pull(in)
        }
      })
    }

}
