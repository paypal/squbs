package org.squbs.httpclient.config

import scala.concurrent.duration._
import spray.can.client.HostConnectorSettings
import spray.can.Http.ClientConnectionType

/**
 * Created by hakuang on 5/30/14.
 */

case class ServiceConfiguration(maxRetryCount: Int = 0, serviceTimeout: Duration = 1 seconds, connectionTimeout: Duration = 1 seconds)

case class Configuration(svcConfig: ServiceConfiguration, hostConfig: HostConfiguration)

case class HostConfiguration(hostSettings: Option[HostConnectorSettings] = None, connectionType: ClientConnectionType = ClientConnectionType.AutoProxied)
