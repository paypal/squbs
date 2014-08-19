/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the CONTRIBUTING file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.squbs.httpclient

import spray.can.client.HostConnectorSettings
import spray.can.Http.ClientConnectionType
import javax.net.ssl.SSLContext
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import spray.http.HttpResponse
import org.squbs.httpclient.pipeline.Pipeline

case class Configuration(pipeline: Option[Pipeline] = None,
                         hostSettings: HostConnectorSettings = Configuration.defaultHostSettings,
                         connectionType: ClientConnectionType = ClientConnectionType.AutoProxied,
                         sslContext: Option[SSLContext] = None,
                         circuitBreakerConfig: CircuitBreakerConfiguration = CircuitBreakerConfiguration())

object Configuration {
  val defaultHostSettings = HostConnectorSettings(ConfigFactory.load)
}

case class CircuitBreakerConfiguration(maxFailures: Int = 5,
                                       callTimeout: FiniteDuration = 10 seconds,
                                       resetTimeout: FiniteDuration = 1 minute,
                                       fallbackHttpResponse: Option[HttpResponse] = None)
