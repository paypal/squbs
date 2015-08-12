/*
 *  Copyright 2015 PayPal
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.squbs.httpclient

import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext

import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.squbs.proxy.{PipelineSetting, SimplePipelineConfig}
import spray.can.Http.ClientConnectionType
import spray.can.client.HostConnectorSettings
import spray.http.{HttpHeader, HttpResponse}

import scala.concurrent.duration._

case class Configuration(pipeline: Option[PipelineSetting] = None, settings: Settings = Settings())

case class Settings(hostSettings: HostConnectorSettings = Configuration.defaultHostSettings,
                    connectionType: ClientConnectionType = ClientConnectionType.AutoProxied,
                    sslContext: Option[SSLContext] = None,
                    circuitBreakerConfig: CircuitBreakerSettings = Configuration.defaultCircuitBreakerSettings)

object Configuration {
  val defaultHostSettings = HostConnectorSettings(ConfigFactory.load)

  val defaultCircuitBreakerSettings = CircuitBreakerSettings()

  val defaultRequestSettings = RequestSettings()

  private[httpclient] def requestTimeout(c: Configuration): Long = requestTimeout(c.settings.hostSettings)

  private[httpclient] def requestTimeout(s: HostConnectorSettings): Long = s.connectionSettings.requestTimeout.toMillis

  def defaultRequestSettings(endpointConfig: Configuration, config: Option[Configuration]) = {
    config match {
      case None => RequestSettings(timeout = Timeout(requestTimeout(endpointConfig), TimeUnit.MILLISECONDS))
      case Some(conf) => RequestSettings(timeout = Timeout(requestTimeout(conf), TimeUnit.MILLISECONDS))
    }
  }

  val defaultFutureTimeout: Timeout = 2 seconds

  implicit def pipelineConfigToSetting(pipelineConfig : SimplePipelineConfig) : PipelineSetting = PipelineSetting(config = Option(pipelineConfig))

  implicit def optionPipelineConfigToOptionSetting(pipelineConfig : Option[SimplePipelineConfig]) : Option[PipelineSetting] = Some(PipelineSetting(config = pipelineConfig))

  def apply(pipelineConfig: Option[SimplePipelineConfig]) = new Configuration(pipelineConfig)



}

case class CircuitBreakerSettings(maxFailures: Int = 5,
                                  callTimeout: FiniteDuration = 10 seconds,
                                  resetTimeout: FiniteDuration = 1 minute,
                                  lastDuration: FiniteDuration = 60 seconds,
                                  fallbackHttpResponse: Option[HttpResponse] = None)

import org.squbs.httpclient.Configuration._

case class RequestSettings(headers: List[HttpHeader] = List.empty[HttpHeader],
                           timeout: Timeout = Timeout(requestTimeout(defaultHostSettings), TimeUnit.MILLISECONDS))