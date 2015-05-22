/*
* Licensed to Typesafe under one or more contributor license agreements.
* See the AUTHORS file distributed with this work for
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

import java.beans.ConstructorProperties
import org.squbs.proxy.SimplePipelineConfig
import org.squbs.unicomplex.JMX

import scala.beans.BeanProperty
import javax.management.{ObjectName, MXBean}
import org.squbs.httpclient.endpoint.{EndpointResolver, EndpointRegistry}
import spray.can.Http.ClientConnectionType.{Proxied, AutoProxied, Direct}
import java.util
import org.squbs.httpclient.env.{EnvironmentRegistry, EnvironmentResolver}
import scala.collection.JavaConversions._
import akka.actor.ActorSystem
import org.squbs.httpclient.ServiceCallStatus.ServiceCallStatus

object HttpClientJMX {

  val httpClientBean = "org.squbs.unicomplex:type=HttpClientInfo"
  val endpointResolverBean = "org.squbs.unicomplex:type=HttpClientEndpointResolverInfo"
  val environmentResolverBean = "org.squbs.unicomplex:type=HttpClientEnvironmentResolverInfo"
  val circuitBreakerBean = "org.squbs.unicomplex:type=HttpClientCircuitBreakerInfo"

  implicit def string2ObjectName(name:String):ObjectName = new ObjectName(name)

  def registryBeans(implicit system: ActorSystem) = {
    registryHCBean
    registryHCEndpointResolverBean
    registryHCEnvResolverBean
  }

  def registryHCBean(implicit system: ActorSystem) = {
    if (!JMX.isRegistered(httpClientBean)){
      JMX.register(HttpClientBean(system), JMX.prefix(system) + httpClientBean)
    }
  }

  def registryHCEndpointResolverBean(implicit system: ActorSystem) = {
    if (!JMX.isRegistered(endpointResolverBean)){
      JMX.register(EndpointResolverBean(system), JMX.prefix(system) + endpointResolverBean)
    }
  }

  def registryHCEnvResolverBean(implicit system: ActorSystem) = {
    if (!JMX.isRegistered(environmentResolverBean)){
      JMX.register(EnvironmentResolverBean(system), JMX.prefix(system) + environmentResolverBean)
    }
  }

  def registryHCCircuitBreakerBean(implicit system: ActorSystem) = {
    if (!JMX.isRegistered(circuitBreakerBean)){
      JMX.register(CircuitBreakerBean(system), JMX.prefix(system) + circuitBreakerBean)
    }
  }
}

// $COVERAGE-OFF$
case class HttpClientInfo @ConstructorProperties(
  Array("name", "env", "endpoint", "status", "connectionType", "maxConnections", "maxRetries",
    "maxRedirects", "requestTimeout", "connectingTimeout", "requestPipelines", "responsePipelines"))(
                                                                                                      @BeanProperty name: String,
                                                                                                      @BeanProperty env: String,
                                                                                                      @BeanProperty endpoint: String,
                                                                                                      @BeanProperty status: String,
                                                                                                      @BeanProperty connectionType: String,
                                                                                                      @BeanProperty maxConnections: Int,
                                                                                                      @BeanProperty maxRetries: Int,
                                                                                                      @BeanProperty maxRedirects: Int,
                                                                                                      @BeanProperty requestTimeout: Long,
                                                                                                      @BeanProperty connectingTimeout: Long,
                                                                                                      @BeanProperty requestPipelines: String,
                                                                                                      @BeanProperty responsePipelines: String)

// $COVERAGE-ON$

@MXBean
trait HttpClientMXBean {
  def getHttpClientInfo: java.util.List[HttpClientInfo]
}

case class HttpClientBean(system: ActorSystem) extends HttpClientMXBean {

  override def getHttpClientInfo: java.util.List[HttpClientInfo] =
    HttpClientManager(system).httpClientMap.values.toList map mapToHttpClientInfo

  private def mapToHttpClientInfo(httpClient: HttpClient) = {
    val name = httpClient.name
    val env  = httpClient.env.lowercaseName
    val endpoint = httpClient.endpoint.uri
    val status = httpClient.status.toString
    val configuration = httpClient.endpoint.config
    val pipelines = configuration.pipeline.getOrElse(SimplePipelineConfig.empty)
    val requestPipelines = pipelines.reqPipe.map(_.getClass.getName).foldLeft[String](""){
      (result, each) => if (result == "") each else result + "=>" + each
    }
    val responsePipelines = pipelines.respPipe.map(_.getClass.getName).foldLeft[String](""){
      (result, each) => if (result == "") each else result + "=>" + each
    }
    val connectionType = configuration.settings.connectionType match {
      case Direct => "Direct"
      case AutoProxied => "AutoProxied"
      case Proxied(host, port) => s"$host:$port"
    }
    val hostSettings = configuration.settings.hostSettings
    val maxConnections = hostSettings.maxConnections
    val maxRetries = hostSettings.maxRetries
    val maxRedirects = hostSettings.maxRedirects
    val requestTimeout = hostSettings.connectionSettings.requestTimeout.toMillis
    val connectingTimeout = hostSettings.connectionSettings.connectingTimeout.toMillis
    HttpClientInfo(name, env, endpoint, status, connectionType, maxConnections, maxRetries, maxRedirects,
      requestTimeout, connectingTimeout, requestPipelines, responsePipelines)
  }
}

// $COVERAGE-OFF$
case class EndpointResolverInfo @ConstructorProperties(
  Array("position", "resolver"))(
                                  @BeanProperty position: Int,
                                  @BeanProperty resolver: String
                                  )

// $COVERAGE-ON$

@MXBean
trait EndpointResolverMXBean {
  def getHttpClientEndpointResolverInfo: java.util.List[EndpointResolverInfo]
}

case class EndpointResolverBean(system: ActorSystem) extends EndpointResolverMXBean {

  override def getHttpClientEndpointResolverInfo: util.List[EndpointResolverInfo] = {
    EndpointRegistry(system).endpointResolvers.zipWithIndex map toEndpointResolverInfo
  }

  private def toEndpointResolverInfo(resolverWithIndex: (EndpointResolver, Int)): EndpointResolverInfo = {
    EndpointResolverInfo(resolverWithIndex._2, resolverWithIndex._1.getClass.getCanonicalName)
  }
}

// $COVERAGE-OFF$
case class EnvironmentResolverInfo @ConstructorProperties(
  Array("position", "resolver"))(
                                  @BeanProperty position: Int,
                                  @BeanProperty resolver: String
                                  )

// $COVERAGE-ON$

@MXBean
trait EnvironmentResolverMXBean {
  def getHttpClientEnvironmentResolverInfo: java.util.List[EnvironmentResolverInfo]
}

case class EnvironmentResolverBean(system: ActorSystem) extends EnvironmentResolverMXBean {

  override def getHttpClientEnvironmentResolverInfo: util.List[EnvironmentResolverInfo] = {
    EnvironmentRegistry(system).environmentResolvers.zipWithIndex map toEnvironmentResolverInfo
  }

  private def toEnvironmentResolverInfo(resolverWithIndex: (EnvironmentResolver, Int)): EnvironmentResolverInfo = {
    EnvironmentResolverInfo(resolverWithIndex._2, resolverWithIndex._1.getClass.getCanonicalName)
  }
}

// $COVERAGE-OFF$
case class CircuitBreakerInfo @ConstructorProperties(
  Array("name", "status", "lastDurationConfig", "successTimes", "fallbackTimes", "failFastTimes", "exceptionTimes",
    "lastDurationErrorRate", "lastDurationFailFastRate", "lastDurationExceptionRate"))(
                                                                                        @BeanProperty name: String,
                                                                                        @BeanProperty status: String,
                                                                                        @BeanProperty lastDurationConfig: String,
                                                                                        @BeanProperty successTimes: Long,
                                                                                        @BeanProperty fallbackTimes: Long,
                                                                                        @BeanProperty failFastTimes: Long,
                                                                                        @BeanProperty exceptionTimes: Long,
                                                                                        @BeanProperty lastDurationErrorRate: String,
                                                                                        @BeanProperty lastDurationFailFastRate: String,
                                                                                        @BeanProperty lastDurationExceptionRate: String
                                                                                        )

// $COVERAGE-ON$

trait CircuitBreakerMXBean {
  def getHttpClientCircuitBreakerInfo: java.util.List[CircuitBreakerInfo]
}

case class CircuitBreakerBean(system: ActorSystem) extends CircuitBreakerMXBean {

  override def getHttpClientCircuitBreakerInfo: util.List[CircuitBreakerInfo] = {
    HttpClientManager(system).httpClientMap.values.toList map mapToHttpClientCircuitBreakerInfo
  }

  private def mapToHttpClientCircuitBreakerInfo(httpClient: HttpClient) = {
    val name = httpClient.name
    val status = httpClient.cbMetrics.status.toString
    val lastDurationConfig = httpClient.endpoint.config.settings.circuitBreakerConfig.lastDuration.toSeconds + " Seconds"
    val successTimes = httpClient.cbMetrics.successTimes
    val fallbackTimes = httpClient.cbMetrics.fallbackTimes
    val failFastTimes = httpClient.cbMetrics.failFastTimes
    val exceptionTimes = httpClient.cbMetrics.exceptionTimes
    val currentTime = System.currentTimeMillis
    val lastDuration = httpClient.endpoint.config.settings.circuitBreakerConfig.lastDuration.toMillis
    httpClient.cbMetrics.cbLastDurationCall = httpClient.cbMetrics.cbLastDurationCall.dropWhile{
      _.callTime + lastDuration <= currentTime
    }
    val cbLastMinMetrics = httpClient.cbMetrics.cbLastDurationCall.groupBy[ServiceCallStatus](_.status) map { data =>
      data._1 -> data._2.size
    }
    val lastDurationSuccess = cbLastMinMetrics.getOrElse(ServiceCallStatus.Success, 0)
    val lastDurationFallback = cbLastMinMetrics.getOrElse(ServiceCallStatus.Fallback, 0)
    val lastDurationFailFast = cbLastMinMetrics.getOrElse(ServiceCallStatus.FailFast, 0)
    val lastDurationException = cbLastMinMetrics.getOrElse(ServiceCallStatus.Exception, 0)
    val lastDurationTotal = lastDurationSuccess + lastDurationFallback + lastDurationFailFast + lastDurationException
    val df = new java.text.DecimalFormat("0.00%")
    val lastDurationErrorRate = lastDurationTotal match {
      case 0 => "0%"
      case _ => df.format((lastDurationTotal - lastDurationSuccess) * 1.0 / lastDurationTotal)
    }
    val lastDurationFailFastRate = lastDurationTotal match {
      case 0 => "0%"
      case _ => df.format(lastDurationFallback * 1.0 / lastDurationTotal)
    }
    val lastDurationExceptionRate = lastDurationTotal match {
      case 0 => "0%"
      case _ => df.format(lastDurationException * 1.0 / lastDurationTotal)
    }
    CircuitBreakerInfo(name, status, lastDurationConfig, successTimes, fallbackTimes, failFastTimes, exceptionTimes,
      lastDurationErrorRate, lastDurationFailFastRate, lastDurationExceptionRate)
  }
}