package org.squbs.httpclient.config

import spray.can.client.HostConnectorSettings
import spray.can.Http.ClientConnectionType
import javax.net.ssl.SSLContext

/**
 * Created by hakuang on 5/30/14.
 */
case class Configuration(hostSettings: Option[HostConnectorSettings] = None,
                         connectionType: ClientConnectionType = ClientConnectionType.AutoProxied,
                         sslContext: Option[SSLContext] = None)
