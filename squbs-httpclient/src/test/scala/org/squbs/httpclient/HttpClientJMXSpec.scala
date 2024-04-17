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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
import com.typesafe.config.ConfigFactory
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.env.QA
import org.squbs.resolver._
import org.squbs.streams.circuitbreaker.CircuitBreakerSettings
import org.squbs.streams.circuitbreaker.impl.AtomicCircuitBreakerState

import java.lang.management.ManagementFactory
import javax.management.ObjectName
import scala.concurrent.duration.Duration
import scala.util.Try

object HttpClientJMXSpec {

  val config = ConfigFactory.parseString(
    """
      |clientWithParsingOverride {
      |  type = squbs.httpclient
      |  pekko.http.parsing {
      |    max-uri-length             = 1k
      |    max-method-length          = 16
      |    max-response-reason-length = 17
      |    max-header-name-length     = 18
      |    max-header-value-length    = 2k
      |    max-header-count           = 19
      |    max-chunk-ext-length       = 20
      |    max-chunk-size             = 3m
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
      |  pekko.http.client.parsing { // Needs to override this value at the client level.
      |    max-content-length = 4m
      |  }
      |}
      |
      |clientWithParsingOverride2 {
      |  type = squbs.httpclient
      |
      |  pekko.http.parsing {
      |    error-logging-verbosity = off
      |    illegal-response-header-value-processing-mode = ignore
      |  }
      |}
      |
      |clientWithSocketOptionsOverride {
      |  type = squbs.httpclient
      |
      |  pekko.http.client {
      |    socket-options {
      |      so-receive-buffer-size = 1k
      |      so-send-buffer-size = 2k
      |      so-reuse-address = true
      |      so-traffic-class = 3
      |      tcp-keep-alive = true
      |      tcp-oob-inline = true
      |      tcp-no-delay = true
      |    }
      |  }
      |}
      |
      |clientWithPipeline {
      |  type = squbs.httpclient
      |
      |  pipeline = dummyPipeline
      |}
      |
      |clientWithDefaultsOn {
      |  type = squbs.httpclient
      |
      |  defaultPipeline = on
      |}
      |
      |clientWithDefaultsOff {
      |  type = squbs.httpclient
      |
      |  defaultPipeline = off
      |}
      |
      |clientWithCircuitBreakerDisabled {
      |  type = squbs.httpclient
      |}
      |
      |clientWithCircuitBreakerEnabled {
      |  type = squbs.httpclient
      |
      |  circuit-breaker {}
      |}
    """.stripMargin).withFallback(ConfigFactory.load())

  implicit val system = ActorSystem("HttpClientJMXSpec", config)

  ResolverRegistry(system).register[HttpEndpoint]("DummyEndpointResolver")
    { (_, _) => Some(HttpEndpoint("http://localhost:8080")) }
}

class HttpClientJMXSpec extends AnyFlatSpecLike with Matchers {

  import HttpClientJMXSpec._

  it should "show the configuration of an http client" in {
    import org.squbs.util.ConfigUtil._

    ClientFlow("sampleClient")

    assertJmxValue("sampleClient", "Name", "sampleClient")
    assertJmxValue("sampleClient", "EndpointUri", "http://localhost:8080")
    assertJmxValue("sampleClient", "EndpointPort", "8080")
    assertJmxValue("sampleClient", "Environment", "DEFAULT")
    assertJmxValue("sampleClient", "Pipeline", "N/A")
    assertJmxValue("sampleClient", "DefaultPipeline", "on")
    assertJmxValue("sampleClient", "CircuitBreakerStateName", "N/A")
    assertJmxValue("sampleClient", "MaxConnections", config.getInt("pekko.http.host-connection-pool.max-connections"))
    assertJmxValue("sampleClient", "MinConnections", config.getInt("pekko.http.host-connection-pool.min-connections"))
    assertJmxValue("sampleClient", "MaxRetries", config.getInt("pekko.http.host-connection-pool.max-retries"))
    assertJmxValue("sampleClient", "MaxOpenRequests", config.getInt("pekko.http.host-connection-pool.max-open-requests"))
    assertJmxValue("sampleClient", "PipeliningLimit", config.getInt("pekko.http.host-connection-pool.pipelining-limit"))
    assertJmxDuration("sampleClient", "ConnectionPoolIdleTimeout",
      config.get[Duration]("pekko.http.host-connection-pool.idle-timeout"))
    assertJmxValue("sampleClient", "UserAgentHeader",
      config.getString("pekko.http.client.user-agent-header"))
    assertJmxDuration("sampleClient", "ConnectingTimeout",
      config.get[Duration]("pekko.http.client.connecting-timeout"))
    assertJmxDuration("sampleClient", "ConnectionIdleTimeout",
      config.get[Duration]("pekko.http.client.idle-timeout"))
    assertJmxValue("sampleClient", "RequestHeaderSizeHint",
      config.getInt("pekko.http.client.request-header-size-hint"))
    assertJmxValue("sampleClient", "SoReceiveBufferSize",
      config.getString("pekko.http.client.socket-options.so-receive-buffer-size"))
    assertJmxValue("sampleClient", "SoSendBufferSize",
      config.getString("pekko.http.client.socket-options.so-send-buffer-size"))
    assertJmxValue("sampleClient", "SoReuseAddress",
      config.getString("pekko.http.client.socket-options.so-reuse-address"))
    assertJmxValue("sampleClient", "SoTrafficClass",
      config.getString("pekko.http.client.socket-options.so-traffic-class"))
    assertJmxValue("sampleClient", "TcpKeepAlive",
      config.getString("pekko.http.client.socket-options.tcp-keep-alive"))
    assertJmxValue("sampleClient", "TcpOobInline",
      config.getString("pekko.http.client.socket-options.tcp-oob-inline"))
    assertJmxValue("sampleClient", "TcpNoDelay",
      config.getString("pekko.http.client.socket-options.tcp-no-delay"))
    assertJmxValue("sampleClient", "MaxUriLength", config.getBytes("pekko.http.parsing.max-uri-length"))
    assertJmxValue("sampleClient", "MaxMethodLength", config.getInt("pekko.http.parsing.max-method-length"))
    assertJmxValue("sampleClient", "MaxResponseReasonLength",
      config.getInt("pekko.http.parsing.max-response-reason-length"))
    assertJmxValue("sampleClient", "MaxHeaderNameLength", config.getInt("pekko.http.parsing.max-header-name-length"))
    assertJmxValue("sampleClient", "MaxHeaderValueLength", config.getBytes("pekko.http.parsing.max-header-value-length"))
    assertJmxValue("sampleClient", "MaxHeaderCount", config.getInt("pekko.http.parsing.max-header-count"))
    assertJmxValue("sampleClient", "MaxChunkExtLength", config.getInt("pekko.http.parsing.max-chunk-ext-length"))
    assertJmxValue("sampleClient", "MaxChunkSize", config.getBytes("pekko.http.parsing.max-chunk-size"))
    val contentLengthValue =
      if (config.getString("pekko.http.client.parsing.max-content-length") == "infinite") Long.MaxValue
      else config.getBytes("pekko.http.client.parsing.max-content-length")
      assertJmxValue("sampleClient", "MaxContentLength", contentLengthValue)
    assertJmxValue("sampleClient", "UriParsingMode", config.getString("pekko.http.parsing.uri-parsing-mode"))
    assertJmxValue("sampleClient", "CookieParsingMode", config.getString("pekko.http.parsing.cookie-parsing-mode"))
    assertJmxValue("sampleClient", "IllegalHeaderWarnings",
      config.getString("pekko.http.parsing.illegal-header-warnings"))
    assertJmxValue("sampleClient", "ErrorLoggingVerbosity",
      config.getString("pekko.http.parsing.error-logging-verbosity"))
    assertJmxValue("sampleClient", "IllegalResponseHeaderValueProcessingMode",
      config.getString("pekko.http.parsing.illegal-response-header-value-processing-mode"))
    assertJmxValue("sampleClient", "HeaderCacheDefault", config.getInt("pekko.http.parsing.header-cache.default"))
    assertJmxValue("sampleClient", "HeaderCacheContentMD5", config.getInt("pekko.http.parsing.header-cache.Content-MD5"))
    assertJmxValue("sampleClient", "HeaderCacheDate", config.getInt("pekko.http.parsing.header-cache.Date"))
    assertJmxValue("sampleClient", "HeaderCacheIfMatch", config.getInt("pekko.http.parsing.header-cache.If-Match"))
    assertJmxValue("sampleClient", "HeaderCacheIfModifiedSince",
      config.getInt("pekko.http.parsing.header-cache.If-Modified-Since"))
    assertJmxValue("sampleClient", "HeaderCacheIfNoneMatch",
      config.getInt("pekko.http.parsing.header-cache.If-None-Match"))
    assertJmxValue("sampleClient", "HeaderCacheIfRange", config.getInt("pekko.http.parsing.header-cache.If-Range"))
    assertJmxValue("sampleClient", "HeaderCacheIfUnmodifiedSince",
      config.getInt("pekko.http.parsing.header-cache.If-Unmodified-Since"))
    assertJmxValue("sampleClient", "HeaderCacheUserAgent", config.getInt("pekko.http.parsing.header-cache.User-Agent"))
    assertJmxValue("sampleClient", "TlsSessionInfoHeader",
      config.getString("pekko.http.parsing.tls-session-info-header"))
  }

  it should "show non-default parsing settings" in {
    ClientFlow("clientWithParsingOverride")

    assertJmxValue("clientWithParsingOverride", "Name", "clientWithParsingOverride")
    assertJmxValue("clientWithParsingOverride", "MaxUriLength",
      config.getBytes("clientWithParsingOverride.pekko.http.parsing.max-uri-length"))
    assertJmxValue("clientWithParsingOverride", "MaxMethodLength",
      config.getInt("clientWithParsingOverride.pekko.http.parsing.max-method-length"))
    assertJmxValue("clientWithParsingOverride", "MaxResponseReasonLength",
      config.getInt("clientWithParsingOverride.pekko.http.parsing.max-response-reason-length"))
    assertJmxValue("clientWithParsingOverride", "MaxHeaderNameLength",
      config.getInt("clientWithParsingOverride.pekko.http.parsing.max-header-name-length"))
    assertJmxValue("clientWithParsingOverride", "MaxHeaderValueLength",
      config.getBytes("clientWithParsingOverride.pekko.http.parsing.max-header-value-length"))
    assertJmxValue("clientWithParsingOverride", "MaxHeaderCount",
      config.getInt("clientWithParsingOverride.pekko.http.parsing.max-header-count"))
    assertJmxValue("clientWithParsingOverride", "MaxChunkExtLength",
      config.getInt("clientWithParsingOverride.pekko.http.parsing.max-chunk-ext-length"))
    assertJmxValue("clientWithParsingOverride", "MaxChunkSize",
      config.getBytes("clientWithParsingOverride.pekko.http.parsing.max-chunk-size"))
    val contentLengthValue =
      if (config.getString("pekko.http.client.parsing.max-content-length") == "infinite") Long.MaxValue
      else config.getBytes("pekko.http.client.parsing.max-content-length")
    assertJmxValue("sampleClient", "MaxContentLength", contentLengthValue)
    assertJmxValue("clientWithParsingOverride", "UriParsingMode",
      config.getString("clientWithParsingOverride.pekko.http.parsing.uri-parsing-mode"))
    assertJmxValue("clientWithParsingOverride", "CookieParsingMode",
      config.getString("clientWithParsingOverride.pekko.http.parsing.cookie-parsing-mode"))
    assertJmxValue("clientWithParsingOverride", "IllegalHeaderWarnings",
      config.getString("clientWithParsingOverride.pekko.http.parsing.illegal-header-warnings"))
    assertJmxValue("clientWithParsingOverride", "ErrorLoggingVerbosity",
      config.getString("clientWithParsingOverride.pekko.http.parsing.error-logging-verbosity"))
    assertJmxValue("clientWithParsingOverride", "IllegalResponseHeaderValueProcessingMode",
      config.getString("clientWithParsingOverride.pekko.http.parsing.illegal-response-header-value-processing-mode"))
    assertJmxValue("clientWithParsingOverride", "HeaderCacheDefault",
      config.getInt("clientWithParsingOverride.pekko.http.parsing.header-cache.default"))
    assertJmxValue("clientWithParsingOverride", "HeaderCacheContentMD5",
      config.getInt("clientWithParsingOverride.pekko.http.parsing.header-cache.Content-MD5"))
    assertJmxValue("clientWithParsingOverride", "HeaderCacheDate",
      config.getInt("clientWithParsingOverride.pekko.http.parsing.header-cache.Date"))
    assertJmxValue("clientWithParsingOverride", "HeaderCacheIfMatch",
      config.getInt("clientWithParsingOverride.pekko.http.parsing.header-cache.If-Match"))
    assertJmxValue("clientWithParsingOverride", "HeaderCacheIfModifiedSince",
      config.getInt("clientWithParsingOverride.pekko.http.parsing.header-cache.If-Modified-Since"))
    assertJmxValue("clientWithParsingOverride", "HeaderCacheIfNoneMatch",
      config.getInt("clientWithParsingOverride.pekko.http.parsing.header-cache.If-None-Match"))
    assertJmxValue("clientWithParsingOverride", "HeaderCacheIfRange",
      config.getInt("clientWithParsingOverride.pekko.http.parsing.header-cache.If-Range"))
    assertJmxValue("clientWithParsingOverride", "HeaderCacheIfUnmodifiedSince",
      config.getInt("clientWithParsingOverride.pekko.http.parsing.header-cache.If-Unmodified-Since"))
    assertJmxValue("clientWithParsingOverride", "HeaderCacheUserAgent",
      config.getInt("clientWithParsingOverride.pekko.http.parsing.header-cache.User-Agent"))
    assertJmxValue("clientWithParsingOverride", "TlsSessionInfoHeader",
      config.getString("clientWithParsingOverride.pekko.http.parsing.tls-session-info-header"))

    ClientFlow("clientWithParsingOverride2")
    assertJmxValue("clientWithParsingOverride2", "ErrorLoggingVerbosity",
      config.getString("clientWithParsingOverride2.pekko.http.parsing.error-logging-verbosity"))
    assertJmxValue("clientWithParsingOverride2", "IllegalResponseHeaderValueProcessingMode",
      config.getString("clientWithParsingOverride2.pekko.http.parsing.illegal-response-header-value-processing-mode"))
  }

  it should "show socket options if set" in {
    ClientFlow("clientWithSocketOptionsOverride")
    val `c.a.h.c.so` = "clientWithSocketOptionsOverride.pekko.http.client.socket-options"
    assertJmxValue("clientWithSocketOptionsOverride", "SoReceiveBufferSize",
      config.getBytes(s"${`c.a.h.c.so`}.so-receive-buffer-size").toString)
    assertJmxValue("clientWithSocketOptionsOverride", "SoSendBufferSize",
      config.getBytes(s"${`c.a.h.c.so`}.so-send-buffer-size").toString)
    assertJmxValue("clientWithSocketOptionsOverride", "SoReuseAddress",
      config.getBoolean(s"${`c.a.h.c.so`}.so-reuse-address").toString)
    assertJmxValue("clientWithSocketOptionsOverride", "SoTrafficClass",
      config.getInt(s"${`c.a.h.c.so`}.so-traffic-class").toString)
    assertJmxValue("clientWithSocketOptionsOverride", "TcpKeepAlive",
      config.getBoolean(s"${`c.a.h.c.so`}.tcp-keep-alive").toString)
    assertJmxValue("clientWithSocketOptionsOverride", "TcpOobInline",
      config.getBoolean(s"${`c.a.h.c.so`}.tcp-oob-inline").toString)
  }

  it should "show different environment values correctly" in {
    ClientFlow("clientWithDifferntEnvironment", env = QA)
    assertJmxValue("clientWithDifferntEnvironment", "Name", "clientWithDifferntEnvironment")
    assertJmxValue("clientWithDifferntEnvironment", "Environment", "QA")
  }

  it should "show pipeline name correctly" in {
    Try(ClientFlow("clientWithPipeline")) // Since pipeline does not exist, it may throw an exception
    assertJmxValue("clientWithPipeline", "Pipeline", "dummyPipeline")
  }

  it should "show defaults on" in {
    ClientFlow("clientWithDefaultsOn")
    assertJmxValue("clientWithDefaultsOn", "DefaultPipeline", "on")
  }

  it should "show defaults off" in {
    ClientFlow("clientWithDefaultsOff")
    assertJmxValue("clientWithDefaultsOff", "DefaultPipeline", "off")
  }

  it should "show circuit breaker state as N/A" in {
    ClientFlow("clientWithCircuitBreakerDisabled")
    assertJmxValue("clientWithCircuitBreakerDisabled", "CircuitBreakerStateName", "N/A")
  }

  it should "show circuit breaker name with default pattern correctly" in {
    ClientFlow("clientWithCircuitBreakerEnabled")
    assertJmxValue(
      "clientWithCircuitBreakerEnabled",
      "CircuitBreakerStateName",
      "clientWithCircuitBreakerEnabled-httpclient")
  }

  it should "show custom circuit breaker state name correctly" in {
    val circuitBreakerSettings =
      CircuitBreakerSettings[HttpRequest, HttpResponse, Int](
        AtomicCircuitBreakerState("SomeDummyCircuitBreakerStateName", ConfigFactory.empty()))

    ClientFlow("clientWithCustomCircuitBreakerState", circuitBreakerSettings = Some(circuitBreakerSettings))
    assertJmxValue("clientWithCustomCircuitBreakerState", "CircuitBreakerStateName", "SomeDummyCircuitBreakerStateName")
  }

  def assertJmxValue(clientName: String, key: String, expectedValue: Any) = {
    val oName = ObjectName.getInstance(
      s"org.squbs.configuration.${system.name}:type=squbs.httpclient,name=${ObjectName.quote(clientName)}")
    val actualValue = ManagementFactory.getPlatformMBeanServer.getAttribute(oName, key)
    actualValue shouldEqual expectedValue
  }

  def assertJmxDuration(clientName: String, key: String, expectedValue: Duration) = {
    val oName = ObjectName.getInstance(
      s"org.squbs.configuration.${system.name}:type=squbs.httpclient,name=${ObjectName.quote(clientName)}")
    val actualValue = ManagementFactory.getPlatformMBeanServer.getAttribute(oName, key)
    Duration(actualValue.toString) shouldEqual expectedValue
  }
}
