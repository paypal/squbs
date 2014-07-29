package org.squbs.httpclient

import spray.can.client.HostConnectorSettings
import spray.can.Http.ClientConnectionType
import javax.net.ssl.SSLContext
import com.typesafe.config.ConfigFactory

/**
 * Created by hakuang on 5/30/14.
 */
case class Configuration(hostSettings: HostConnectorSettings = Configuration.defaultHostSettings,
                         connectionType: ClientConnectionType = ClientConnectionType.AutoProxied,
                         sslContext: Option[SSLContext] = None)

object Configuration {
  val defaultHostSettings = HostConnectorSettings(ConfigFactory.load)
}
