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

import org.apache.pekko.actor._
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.jmx.JmxReporter

object MetricsExtension extends ExtensionId[MetricsExtensionImpl] with ExtensionIdProvider {

  override def lookup: MetricsExtension.type = MetricsExtension

  override def createExtension(system: ExtendedActorSystem) = new MetricsExtensionImpl(system)

  /**
    * Java API: retrieve the Count extension for the given system.
    */
  override def get(system: ActorSystem): MetricsExtensionImpl = super.get(system)
}

class MetricsExtensionImpl(system: ActorSystem) extends Extension {
  val Domain = s"org.squbs.metrics.${system.name}"
  val metrics = new MetricRegistry()
  val jmxReporter = JmxReporter.forRegistry(metrics).inDomain(Domain).build()
  jmxReporter.start()
}
