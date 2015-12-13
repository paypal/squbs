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

import akka.actor.ActorRefFactory
import akka.util.Timeout
import org.squbs.pipeline.{PipelineSetting, SimplePipelineConfig}
import spray.can.Http.ClientConnectionType
import spray.can.client.HostConnectorSettings
import spray.http.{HttpHeader, HttpResponse}

import scala.concurrent.duration._
import scala.language.{implicitConversions, postfixOps}

case class Configuration(pipeline: Option[PipelineSetting], settings: Settings)

case class Settings(hostSettings: HostConnectorSettings, connectionType: ClientConnectionType,
                    sslContext: Option[SSLContext], circuitBreakerConfig: CircuitBreakerSettings)

object Settings {

  def apply()(implicit refFactory: ActorRefFactory) =
    new Settings(Configuration.defaultHostSettings, ClientConnectionType.AutoProxied, None,
      Configuration.defaultCircuitBreakerSettings)

  def apply(hostSettings: HostConnectorSettings) =
    new Settings(hostSettings, ClientConnectionType.AutoProxied, None, Configuration.defaultCircuitBreakerSettings)

  def apply(hostSettings: HostConnectorSettings, connectionType: ClientConnectionType) =
    new Settings(hostSettings, connectionType, None, Configuration.defaultCircuitBreakerSettings)

  def apply(hostSettings: HostConnectorSettings, sslContext: Option[SSLContext]) =
    new Settings(hostSettings, ClientConnectionType.AutoProxied, sslContext,
      Configuration.defaultCircuitBreakerSettings)

  def apply(hostSettings: HostConnectorSettings, circuitBreakerConfig: CircuitBreakerSettings) =
    new Settings(hostSettings, ClientConnectionType.AutoProxied, None, circuitBreakerConfig)

  def apply(hostSettings: HostConnectorSettings, connectionType: ClientConnectionType, sslContext: Option[SSLContext]) =
    new Settings(hostSettings, connectionType, sslContext, Configuration.defaultCircuitBreakerSettings)

  def apply(hostSettings: HostConnectorSettings, connectionType: ClientConnectionType,
            circuitBreakerConfig: CircuitBreakerSettings) =
    new Settings(hostSettings, connectionType, None, circuitBreakerConfig)

  def apply(hostSettings: HostConnectorSettings, sslContext: Option[SSLContext],
            circuitBreakerConfig: CircuitBreakerSettings) =
    new Settings(hostSettings, ClientConnectionType.AutoProxied, sslContext, circuitBreakerConfig)

  def apply(connectionType: ClientConnectionType)(implicit refFactory: ActorRefFactory) =
    new Settings(Configuration.defaultHostSettings, connectionType, None, Configuration.defaultCircuitBreakerSettings)

  def apply(connectionType: ClientConnectionType, sslContext: Option[SSLContext])
           (implicit refFactory: ActorRefFactory) =
    new Settings(Configuration.defaultHostSettings, connectionType, sslContext,
      Configuration.defaultCircuitBreakerSettings)

  def apply(connectionType: ClientConnectionType, circuitBreakerConfig: CircuitBreakerSettings)
           (implicit refFactory: ActorRefFactory) =
    new Settings(Configuration.defaultHostSettings, connectionType, None, circuitBreakerConfig)

  def apply(connectionType: ClientConnectionType, sslContext: Option[SSLContext],
            circuitBreakerConfig: CircuitBreakerSettings)(implicit refFactory: ActorRefFactory) =
    new Settings(Configuration.defaultHostSettings, connectionType, sslContext,
      circuitBreakerConfig)

  def apply(sslContext: Option[SSLContext])(implicit refFactory: ActorRefFactory) =
    new Settings(Configuration.defaultHostSettings, ClientConnectionType.AutoProxied, sslContext,
      Configuration.defaultCircuitBreakerSettings)

  def apply(sslContext: Option[SSLContext], circuitBreakerConfig: CircuitBreakerSettings)
           (implicit refFactory: ActorRefFactory) =
    new Settings(Configuration.defaultHostSettings, ClientConnectionType.AutoProxied, sslContext, circuitBreakerConfig)

  def apply(circuitBreakerConfig: CircuitBreakerSettings)(implicit refFactory: ActorRefFactory) =
    new Settings(Configuration.defaultHostSettings, ClientConnectionType.AutoProxied, None, circuitBreakerConfig)
}

object Configuration {

  def apply(pipeline: Option[PipelineSetting])(implicit refFactory: ActorRefFactory) =
    new Configuration(pipeline, Settings())

  def apply(settings: Settings) = new Configuration(None, settings)

  def apply()(implicit refFactory: ActorRefFactory) = new Configuration(None, Settings())

  def defaultHostSettings(implicit refFactory: ActorRefFactory) = HostConnectorSettings(refFactory.settings.config)

  val defaultCircuitBreakerSettings = CircuitBreakerSettings()

  def defaultRequestSettings(implicit refFactory: ActorRefFactory) = RequestSettings()

  private[httpclient] def requestTimeout(c: Configuration): Long = requestTimeout(c.settings.hostSettings)

  private[httpclient] def requestTimeout(s: HostConnectorSettings): Long = s.connectionSettings.requestTimeout.toMillis

  def defaultRequestSettings(endpointConfig: Configuration, config: Option[Configuration]) = {
    config match {
      case None => RequestSettings(timeout = Timeout(requestTimeout(endpointConfig), TimeUnit.MILLISECONDS))
      case Some(conf) => RequestSettings(timeout = Timeout(requestTimeout(conf), TimeUnit.MILLISECONDS))
    }
  }

  val defaultFutureTimeout: Timeout = 2 seconds

  implicit def pipelineConfigToSetting(pipelineConfig : SimplePipelineConfig) : PipelineSetting =
    PipelineSetting(config = Option(pipelineConfig))

  implicit def optionPipelineConfigToOptionSetting(pipelineConfig: Option[SimplePipelineConfig]):
    Option[PipelineSetting] = Some(PipelineSetting(config = pipelineConfig))
}

case class CircuitBreakerSettings(maxFailures: Int = 5,
                                  callTimeout: FiniteDuration = 10 seconds,
                                  resetTimeout: FiniteDuration = 1 minute,
                                  historyUnits: Int = 5,
                                  historyUnitDuration: FiniteDuration = 1 minute,
                                  fallbackHttpResponse: Option[HttpResponse] = None)

import org.squbs.httpclient.Configuration._

object RequestSettings {

  def apply(headers: List[HttpHeader])(implicit refFactory: ActorRefFactory) =
    new RequestSettings(headers, Timeout(requestTimeout(defaultHostSettings), TimeUnit.MILLISECONDS))

  def apply(timeout: Timeout) = new RequestSettings(List.empty, timeout)

  def apply()(implicit refFactory: ActorRefFactory) =
    new RequestSettings(List.empty, Timeout(requestTimeout(defaultHostSettings), TimeUnit.MILLISECONDS))
}

case class RequestSettings(headers: List[HttpHeader], timeout: Timeout)
