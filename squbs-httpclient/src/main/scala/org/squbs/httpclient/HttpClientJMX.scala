/*
 *  Copyright 2017 PayPal
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

import org.apache.pekko.http.scaladsl.settings.ConnectionPoolSettings
import org.apache.pekko.io.Inet.SO.{ReceiveBufferSize, ReuseAddress, SendBufferSize, TrafficClass}
import org.apache.pekko.io.Tcp.SO.{KeepAlive, OOBInline, TcpNoDelay}
import javax.management.MXBean

import scala.language.implicitConversions

@MXBean
trait HttpClientConfigMXBean {
  def getName: String
  def getEndpointUri: String
  def getEndpointPort: String
  def getEnvironment: String
  def getPipeline: String
  def getDefaultPipeline: String
  def getCircuitBreakerStateName: String
  def getMaxConnections: Int
  def getMinConnections: Int
  def getMaxRetries: Int
  def getMaxOpenRequests: Int
  def getPipeliningLimit: Int
  def getConnectionPoolIdleTimeout: String
  def getUserAgentHeader: String
  def getConnectingTimeout: String
  def getConnectionIdleTimeout: String
  def getRequestHeaderSizeHint: Int
  def getSoReceiveBufferSize: String
  def getSoSendBufferSize: String
  def getSoReuseAddress: String
  def getSoTrafficClass: String
  def getTcpKeepAlive: String
  def getTcpOobInline: String
  def getTcpNoDelay: String
  def getMaxUriLength: Int
  def getMaxMethodLength: Int
  def getMaxResponseReasonLength: Int
  def getMaxHeaderNameLength: Int
  def getMaxHeaderValueLength: Int
  def getMaxHeaderCount: Int
  def getMaxContentLength: Long
  def getMaxChunkExtLength: Int
  def getMaxChunkSize: Int
  def getUriParsingMode: String
  def getCookieParsingMode: String
  def getIllegalHeaderWarnings: String
  def getErrorLoggingVerbosity: String
  def getIllegalResponseHeaderValueProcessingMode: String
  def getHeaderCacheDefault: Int
  def getHeaderCacheContentMD5: Int
  def getHeaderCacheDate: Int
  def getHeaderCacheIfMatch: Int
  def getHeaderCacheIfModifiedSince: Int
  def getHeaderCacheIfNoneMatch: Int
  def getHeaderCacheIfRange: Int
  def getHeaderCacheIfUnmodifiedSince: Int
  def getHeaderCacheUserAgent: Int
  def getTlsSessionInfoHeader: String

  // TODO Make bytes human readable:
  // http://stackoverflow.com/questions/3758606/how-to-convert-byte-size-into-human-readable-format-in-java

  // TODO Consider matching JMX attribute names with configuration names, .e.g, max-connections vs MaxConnections
}

case class HttpClientConfigMXBeanImpl(name: String,
                                      endpointUri: String,
                                      endpointPort: Int,
                                      environment: String,
                                      pipeline: Option[String],
                                      defaultPipelineOn: Option[Boolean],
                                      circuitBreakerStateName: String,
                                      cps: ConnectionPoolSettings) extends HttpClientConfigMXBean {

  val `N/A` = "N/A"
  val undefined = "undefined"
  val on = "on"
  val off = "off"

  val userAgentHeader = cps.connectionSettings.userAgentHeader.map(_.value()).getOrElse(`N/A`)

  val soReceiveBufferSize = cps.connectionSettings.socketOptions.collectFirst {
    case ReceiveBufferSize(size) => size.toString
  } getOrElse undefined

  val soSendBufferSize = cps.connectionSettings.socketOptions.collectFirst {
    case SendBufferSize(size) => size.toString
  } getOrElse undefined

  val soReuseAddress = cps.connectionSettings.socketOptions.collectFirst {
    case ReuseAddress(on) => on.toString
  } getOrElse undefined

  val soTrafficClass = cps.connectionSettings.socketOptions.collectFirst {
    case TrafficClass(tc) => tc.toString
  } getOrElse undefined

  val tcpKeepAlive = cps.connectionSettings.socketOptions.collectFirst {
    case KeepAlive(on) => on.toString
  } getOrElse undefined

  val tcpOobInline = cps.connectionSettings.socketOptions.collectFirst {
    case OOBInline(on) => on.toString
  } getOrElse undefined

  val tcpNoDelay = cps.connectionSettings.socketOptions.collectFirst {
    case TcpNoDelay(on) => on.toString
  } getOrElse undefined

  val uriParsingMode = cps.connectionSettings.parserSettings.uriParsingMode.toString.toLowerCase

  val cookieParsingMode = cps.connectionSettings.parserSettings.cookieParsingMode.toString.toLowerCase

  val illegalHeaderWarnings = cps.connectionSettings.parserSettings.illegalHeaderWarnings match {
    case true => "on"
    case false => "off"
  }

  val errorLoggingVerbosity = cps.connectionSettings.parserSettings.errorLoggingVerbosity.toString.toLowerCase

  val illegalResponseHeaderValueProcessingMode =
    cps.connectionSettings.parserSettings.illegalResponseHeaderValueProcessingMode.toString.toLowerCase

  override def getName: String = name

  override def getEndpointUri: String = endpointUri

  override def getEndpointPort: String = endpointPort.toString

  override def getEnvironment: String = environment

  override def getPipeline: String = pipeline.getOrElse(`N/A`)

  override def getDefaultPipeline: String = defaultPipelineOn.map(if(_) on else off ).getOrElse(on)

  override def getCircuitBreakerStateName: String = circuitBreakerStateName

  override def getMaxConnections: Int = cps.maxConnections

  override def getMinConnections: Int = cps.minConnections

  override def getMaxRetries: Int = cps.maxRetries

  override def getMaxOpenRequests: Int = cps.maxOpenRequests

  override def getPipeliningLimit: Int = cps.pipeliningLimit

  override def getConnectionPoolIdleTimeout: String = cps.idleTimeout.toString

  override def getUserAgentHeader: String = userAgentHeader

  override def getConnectingTimeout: String = cps.connectionSettings.connectingTimeout.toString()

  override def getConnectionIdleTimeout: String = cps.connectionSettings.idleTimeout.toString

  override def getRequestHeaderSizeHint: Int = cps.connectionSettings.requestHeaderSizeHint

  override def getSoReceiveBufferSize: String = soReceiveBufferSize

  override def getSoSendBufferSize: String = soSendBufferSize

  override def getSoReuseAddress: String = soReuseAddress

  override def getSoTrafficClass: String = soTrafficClass

  override def getTcpKeepAlive: String = tcpKeepAlive

  override def getTcpOobInline: String = tcpOobInline

  override def getTcpNoDelay: String = tcpNoDelay

  override def getMaxUriLength: Int = cps.connectionSettings.parserSettings.maxUriLength

  override def getMaxMethodLength: Int = cps.connectionSettings.parserSettings.maxMethodLength

  override def getMaxResponseReasonLength: Int = cps.connectionSettings.parserSettings.maxResponseReasonLength

  override def getMaxHeaderNameLength: Int = cps.connectionSettings.parserSettings.maxHeaderNameLength

  override def getMaxHeaderValueLength: Int = cps.connectionSettings.parserSettings.maxHeaderValueLength

  override def getMaxHeaderCount: Int = cps.connectionSettings.parserSettings.maxHeaderCount

  override def getMaxContentLength: Long = cps.connectionSettings.parserSettings.maxContentLength

  override def getMaxChunkExtLength: Int = cps.connectionSettings.parserSettings.maxChunkExtLength

  override def getMaxChunkSize: Int = cps.connectionSettings.parserSettings.maxChunkSize

  override def getUriParsingMode: String = uriParsingMode

  override def getCookieParsingMode: String = cookieParsingMode

  override def getIllegalHeaderWarnings: String = illegalHeaderWarnings

  override def getErrorLoggingVerbosity: String = errorLoggingVerbosity

  override def getIllegalResponseHeaderValueProcessingMode: String = illegalResponseHeaderValueProcessingMode

  override def getHeaderCacheDefault: Int =
    cps.connectionSettings.parserSettings.headerValueCacheLimits("default")

  override def getHeaderCacheContentMD5: Int =
    cps.connectionSettings.parserSettings.headerValueCacheLimits("Content-MD5")

  override def getHeaderCacheDate: Int =
    cps.connectionSettings.parserSettings.headerValueCacheLimits("Date")

  override def getHeaderCacheIfMatch: Int =
    cps.connectionSettings.parserSettings.headerValueCacheLimits("If-Match")

  override def getHeaderCacheIfModifiedSince: Int =
    cps.connectionSettings.parserSettings.headerValueCacheLimits("If-Modified-Since")

  override def getHeaderCacheIfNoneMatch: Int =
    cps.connectionSettings.parserSettings.headerValueCacheLimits("If-None-Match")

  override def getHeaderCacheIfRange: Int =
    cps.connectionSettings.parserSettings.headerValueCacheLimits("If-Range")

  override def getHeaderCacheIfUnmodifiedSince: Int =
    cps.connectionSettings.parserSettings.headerValueCacheLimits("If-Unmodified-Since")

  override def getHeaderCacheUserAgent: Int =
    cps.connectionSettings.parserSettings.headerValueCacheLimits("User-Agent")

  override def getTlsSessionInfoHeader: String =
    cps.connectionSettings.parserSettings.includeTlsSessionInfoHeader match {
      case true => "on"
      case false => "off"
    }
}
