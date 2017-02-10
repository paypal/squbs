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

import java.lang.management.ManagementFactory
import javax.management.ObjectName

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.scalatest._
import org.squbs.resolver._
import org.squbs.env.QA

import scala.concurrent.duration.Duration

object HttpClientJMXSpec {

  val config = ConfigFactory.parseString(
    """
      |clientWithParsingOverride {
      |  type = squbs.httpclient
      |  akka.http.parsing {
      |    max-uri-length             = 1k
      |    max-method-length          = 16
      |    max-response-reason-length = 17
      |    max-header-name-length     = 18
      |    max-header-value-length    = 2k
      |    max-header-count           = 19
      |    max-chunk-ext-length       = 20
      |    max-chunk-size             = 3m
      |    max-content-length = 4m
      |    uri-parsing-mode = relaxed
      |    cookie-parsing-mode = raw
      |    illegal-header-warnings = off
      |    error-logging-verbosity = simple
      |    illegal-response-header-value-processing-mode = warn
      |    header-cache {
      |      default = 21
      |      Content-MD5 = 22
      |      Date = 23
      |      If-Match = 24
      |      If-Modified-Since = 25
      |      If-None-Match = 26
      |      If-Range = 27
      |      If-Unmodified-Since = 28
      |      User-Agent = 29
      |    }
      |    tls-session-info-header = on
      |  }
      |}
      |
      |clientWithParsingOverride2 {
      |  type = squbs.httpclient
      |
      |  akka.http.parsing {
      |    error-logging-verbosity = off
      |    illegal-response-header-value-processing-mode = ignore
      |  }
      |}
      |
      |clientWithSocketOptionsOverride {
      |  type = squbs.httpclient
      |
      |  akka.http.host-connection-pool {
      |    client = {
      |      socket-options {
      |        so-receive-buffer-size = 1k
      |        so-send-buffer-size = 2k
      |        so-reuse-address = true
      |        so-traffic-class = 3
      |        tcp-keep-alive = true
      |        tcp-oob-inline = true
      |        tcp-no-delay = true
      |      }
      |    }
      |  }
      |}
    """.stripMargin).withFallback(ConfigFactory.load())

  implicit val system = ActorSystem("HttpClientJMXSpec", config)
  implicit val materializer = ActorMaterializer()

  ResolverRegistry(system).register[HttpEndpoint]("DummyEndpointResolver")
    { (_, _) => Some(HttpEndpoint("http://localhost:8080")) }
}

class HttpClientJMXSpec extends FlatSpecLike with Matchers {

  import HttpClientJMXSpec._

  it should "show the configuration of an http client" in {
    import org.squbs.util.ConfigUtil._

    ClientFlow("sampleClient")

    assertJmxValue("sampleClient", "Name", "sampleClient")
    assertJmxValue("sampleClient", "EndpointUri", "http://localhost:8080")
    assertJmxValue("sampleClient", "Environment", "DEFAULT")
    assertJmxValue("sampleClient", "MaxConnections", config.getInt("akka.http.host-connection-pool.max-connections"))
    assertJmxValue("sampleClient", "MinConnections", config.getInt("akka.http.host-connection-pool.min-connections"))
    assertJmxValue("sampleClient", "MaxRetries", config.getInt("akka.http.host-connection-pool.max-retries"))
    assertJmxValue("sampleClient", "MaxOpenRequests", config.getInt("akka.http.host-connection-pool.max-open-requests"))
    assertJmxValue("sampleClient", "PipeliningLimit", config.getInt("akka.http.host-connection-pool.pipelining-limit"))
    assertJmxValue("sampleClient", "ConnectionPoolIdleTimeout",
      config.get[Duration]("akka.http.host-connection-pool.idle-timeout").toString)
    assertJmxValue("sampleClient", "UserAgentHeader",
      config.getString("akka.http.host-connection-pool.client.user-agent-header"))
    assertJmxValue("sampleClient", "ConnectingTimeout",
      config.get[Duration]("akka.http.host-connection-pool.client.connecting-timeout").toString)
    assertJmxValue("sampleClient", "ConnectionIdleTimeout",
      config.get[Duration]("akka.http.host-connection-pool.client.idle-timeout").toString)
    assertJmxValue("sampleClient", "RequestHeaderSizeHint",
      config.getInt("akka.http.host-connection-pool.client.request-header-size-hint"))
    assertJmxValue("sampleClient", "SoReceiveBufferSize",
      config.getString("akka.http.host-connection-pool.client.socket-options.so-receive-buffer-size"))
    assertJmxValue("sampleClient", "SoSendBufferSize",
      config.getString("akka.http.host-connection-pool.client.socket-options.so-send-buffer-size"))
    assertJmxValue("sampleClient", "SoReuseAddress",
      config.getString("akka.http.host-connection-pool.client.socket-options.so-reuse-address"))
    assertJmxValue("sampleClient", "SoTrafficClass",
      config.getString("akka.http.host-connection-pool.client.socket-options.so-traffic-class"))
    assertJmxValue("sampleClient", "TcpKeepAlive",
      config.getString("akka.http.host-connection-pool.client.socket-options.tcp-keep-alive"))
    assertJmxValue("sampleClient", "TcpOobInline",
      config.getString("akka.http.host-connection-pool.client.socket-options.tcp-oob-inline"))
    assertJmxValue("sampleClient", "TcpNoDelay",
      config.getString("akka.http.host-connection-pool.client.socket-options.tcp-no-delay"))
    assertJmxValue("sampleClient", "MaxUriLength", config.getBytes("akka.http.parsing.max-uri-length"))
    assertJmxValue("sampleClient", "MaxMethodLength", config.getInt("akka.http.parsing.max-method-length"))
    assertJmxValue("sampleClient", "MaxResponseReasonLength",
      config.getInt("akka.http.parsing.max-response-reason-length"))
    assertJmxValue("sampleClient", "MaxHeaderNameLength", config.getInt("akka.http.parsing.max-header-name-length"))
    assertJmxValue("sampleClient", "MaxHeaderValueLength", config.getBytes("akka.http.parsing.max-header-value-length"))
    assertJmxValue("sampleClient", "MaxHeaderCount", config.getInt("akka.http.parsing.max-header-count"))
    assertJmxValue("sampleClient", "MaxChunkExtLength", config.getInt("akka.http.parsing.max-chunk-ext-length"))
    assertJmxValue("sampleClient", "MaxChunkSize", config.getBytes("akka.http.parsing.max-chunk-size"))
    assertJmxValue("sampleClient", "MaxContentLength", config.getBytes("akka.http.parsing.max-content-length"))
    assertJmxValue("sampleClient", "UriParsingMode", config.getString("akka.http.parsing.uri-parsing-mode"))
    assertJmxValue("sampleClient", "CookieParsingMode", config.getString("akka.http.parsing.cookie-parsing-mode"))
    assertJmxValue("sampleClient", "IllegalHeaderWarnings",
      config.getString("akka.http.parsing.illegal-header-warnings"))
    assertJmxValue("sampleClient", "ErrorLoggingVerbosity",
      config.getString("akka.http.parsing.error-logging-verbosity"))
    assertJmxValue("sampleClient", "IllegalResponseHeaderValueProcessingMode",
      config.getString("akka.http.parsing.illegal-response-header-value-processing-mode"))
    assertJmxValue("sampleClient", "HeaderCacheDefault", config.getInt("akka.http.parsing.header-cache.default"))
    assertJmxValue("sampleClient", "HeaderCacheContentMD5", config.getInt("akka.http.parsing.header-cache.Content-MD5"))
    assertJmxValue("sampleClient", "HeaderCacheDate", config.getInt("akka.http.parsing.header-cache.Date"))
    assertJmxValue("sampleClient", "HeaderCacheIfMatch", config.getInt("akka.http.parsing.header-cache.If-Match"))
    assertJmxValue("sampleClient", "HeaderCacheIfModifiedSince",
      config.getInt("akka.http.parsing.header-cache.If-Modified-Since"))
    assertJmxValue("sampleClient", "HeaderCacheIfNoneMatch",
      config.getInt("akka.http.parsing.header-cache.If-None-Match"))
    assertJmxValue("sampleClient", "HeaderCacheIfRange", config.getInt("akka.http.parsing.header-cache.If-Range"))
    assertJmxValue("sampleClient", "HeaderCacheIfUnmodifiedSince",
      config.getInt("akka.http.parsing.header-cache.If-Unmodified-Since"))
    assertJmxValue("sampleClient", "HeaderCacheUserAgent", config.getInt("akka.http.parsing.header-cache.User-Agent"))
    assertJmxValue("sampleClient", "TlsSessionInfoHeader",
      config.getString("akka.http.parsing.tls-session-info-header"))
  }

  it should "show non-default parsing settings" in {
    ClientFlow("clientWithParsingOverride")

    assertJmxValue("clientWithParsingOverride", "Name", "clientWithParsingOverride")
    assertJmxValue("clientWithParsingOverride", "MaxUriLength",
      config.getBytes("clientWithParsingOverride.akka.http.parsing.max-uri-length"))
    assertJmxValue("clientWithParsingOverride", "MaxMethodLength",
      config.getInt("clientWithParsingOverride.akka.http.parsing.max-method-length"))
    assertJmxValue("clientWithParsingOverride", "MaxResponseReasonLength",
      config.getInt("clientWithParsingOverride.akka.http.parsing.max-response-reason-length"))
    assertJmxValue("clientWithParsingOverride", "MaxHeaderNameLength",
      config.getInt("clientWithParsingOverride.akka.http.parsing.max-header-name-length"))
    assertJmxValue("clientWithParsingOverride", "MaxHeaderValueLength",
      config.getBytes("clientWithParsingOverride.akka.http.parsing.max-header-value-length"))
    assertJmxValue("clientWithParsingOverride", "MaxHeaderCount",
      config.getInt("clientWithParsingOverride.akka.http.parsing.max-header-count"))
    assertJmxValue("clientWithParsingOverride", "MaxChunkExtLength",
      config.getInt("clientWithParsingOverride.akka.http.parsing.max-chunk-ext-length"))
    assertJmxValue("clientWithParsingOverride", "MaxChunkSize",
      config.getBytes("clientWithParsingOverride.akka.http.parsing.max-chunk-size"))
    assertJmxValue("clientWithParsingOverride", "MaxContentLength",
      config.getBytes("clientWithParsingOverride.akka.http.parsing.max-content-length"))
    assertJmxValue("clientWithParsingOverride", "UriParsingMode",
      config.getString("clientWithParsingOverride.akka.http.parsing.uri-parsing-mode"))
    assertJmxValue("clientWithParsingOverride", "CookieParsingMode",
      config.getString("clientWithParsingOverride.akka.http.parsing.cookie-parsing-mode"))
    assertJmxValue("clientWithParsingOverride", "IllegalHeaderWarnings",
      config.getString("clientWithParsingOverride.akka.http.parsing.illegal-header-warnings"))
    assertJmxValue("clientWithParsingOverride", "ErrorLoggingVerbosity",
      config.getString("clientWithParsingOverride.akka.http.parsing.error-logging-verbosity"))
    assertJmxValue("clientWithParsingOverride", "IllegalResponseHeaderValueProcessingMode",
      config.getString("clientWithParsingOverride.akka.http.parsing.illegal-response-header-value-processing-mode"))
    assertJmxValue("clientWithParsingOverride", "HeaderCacheDefault",
      config.getInt("clientWithParsingOverride.akka.http.parsing.header-cache.default"))
    assertJmxValue("clientWithParsingOverride", "HeaderCacheContentMD5",
      config.getInt("clientWithParsingOverride.akka.http.parsing.header-cache.Content-MD5"))
    assertJmxValue("clientWithParsingOverride", "HeaderCacheDate",
      config.getInt("clientWithParsingOverride.akka.http.parsing.header-cache.Date"))
    assertJmxValue("clientWithParsingOverride", "HeaderCacheIfMatch",
      config.getInt("clientWithParsingOverride.akka.http.parsing.header-cache.If-Match"))
    assertJmxValue("clientWithParsingOverride", "HeaderCacheIfModifiedSince",
      config.getInt("clientWithParsingOverride.akka.http.parsing.header-cache.If-Modified-Since"))
    assertJmxValue("clientWithParsingOverride", "HeaderCacheIfNoneMatch",
      config.getInt("clientWithParsingOverride.akka.http.parsing.header-cache.If-None-Match"))
    assertJmxValue("clientWithParsingOverride", "HeaderCacheIfRange",
      config.getInt("clientWithParsingOverride.akka.http.parsing.header-cache.If-Range"))
    assertJmxValue("clientWithParsingOverride", "HeaderCacheIfUnmodifiedSince",
      config.getInt("clientWithParsingOverride.akka.http.parsing.header-cache.If-Unmodified-Since"))
    assertJmxValue("clientWithParsingOverride", "HeaderCacheUserAgent",
      config.getInt("clientWithParsingOverride.akka.http.parsing.header-cache.User-Agent"))
    assertJmxValue("clientWithParsingOverride", "TlsSessionInfoHeader",
      config.getString("clientWithParsingOverride.akka.http.parsing.tls-session-info-header"))

    ClientFlow("clientWithParsingOverride2")
    assertJmxValue("clientWithParsingOverride2", "ErrorLoggingVerbosity",
      config.getString("clientWithParsingOverride2.akka.http.parsing.error-logging-verbosity"))
    assertJmxValue("clientWithParsingOverride2", "IllegalResponseHeaderValueProcessingMode",
      config.getString("clientWithParsingOverride2.akka.http.parsing.illegal-response-header-value-processing-mode"))
  }

  it should "show socket options if set" in {
    ClientFlow("clientWithSocketOptionsOverride")
    val `c.a.h.hcp.c.so` = "clientWithSocketOptionsOverride.akka.http.host-connection-pool.client.socket-options"
    assertJmxValue("clientWithSocketOptionsOverride", "SoReceiveBufferSize",
      config.getBytes(s"${`c.a.h.hcp.c.so`}.so-receive-buffer-size").toString)
    assertJmxValue("clientWithSocketOptionsOverride", "SoSendBufferSize",
      config.getBytes(s"${`c.a.h.hcp.c.so`}.so-send-buffer-size").toString)
    assertJmxValue("clientWithSocketOptionsOverride", "SoReuseAddress",
      config.getBoolean(s"${`c.a.h.hcp.c.so`}.so-reuse-address").toString)
    assertJmxValue("clientWithSocketOptionsOverride", "SoTrafficClass",
      config.getInt(s"${`c.a.h.hcp.c.so`}.so-traffic-class").toString)
    assertJmxValue("clientWithSocketOptionsOverride", "TcpKeepAlive",
      config.getBoolean(s"${`c.a.h.hcp.c.so`}.tcp-keep-alive").toString)
    assertJmxValue("clientWithSocketOptionsOverride", "TcpOobInline",
      config.getBoolean(s"${`c.a.h.hcp.c.so`}.tcp-oob-inline").toString)
  }

  it should "show different environment values correctly" in {
    ClientFlow("clientWithDifferntEnvironment", env = QA)
    assertJmxValue("clientWithDifferntEnvironment", "Name", "clientWithDifferntEnvironment")
    assertJmxValue("clientWithDifferntEnvironment", "Environment", "QA")
  }

  def assertJmxValue(clientName: String, key: String, expectedValue: Any) = {
    val oName = ObjectName.getInstance(
      s"org.squbs.configuration.${system.name}:type=squbs.httpclient,name=${ObjectName.quote(clientName)}")
    val actualValue = ManagementFactory.getPlatformMBeanServer.getAttribute(oName, key)
    actualValue shouldEqual expectedValue
  }
}
