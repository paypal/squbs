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
import java.lang.management.ManagementFactory
import java.util
import javax.management.{MXBean, ObjectName}

import akka.actor.ActorRef
import org.squbs.httpclient.ServiceCallStatus.ServiceCallStatus
import org.squbs.httpclient.endpoint.{EndpointRegistry, EndpointResolver}
import org.squbs.httpclient.env.{EnvironmentRegistry, EnvironmentResolver}
import spray.can.Http.ClientConnectionType.{AutoProxied, Direct, Proxied}

import scala.beans.BeanProperty
import scala.collection.JavaConversions._
import org.squbs.proxy.SimplePipelineConfig

object HttpClientJMX {

  implicit def string2ObjectName(name:String):ObjectName = new ObjectName(name)
  
  def registryBeans = {
    registryHCBean
    registryHCEndpointResolverBean
    registryHCEnvResolverBean
  }

  def registryHCBean = {
    if (!ManagementFactory.getPlatformMBeanServer.isRegistered(HttpClientBean.httpClientBean)){
      ManagementFactory.getPlatformMBeanServer.registerMBean(HttpClientBean, HttpClientBean.httpClientBean)
    }
  }
  
  def registryHCEndpointResolverBean = {
    if (!ManagementFactory.getPlatformMBeanServer.isRegistered(EndpointResolverBean.endpointResolverBean)){
      ManagementFactory.getPlatformMBeanServer.registerMBean(EndpointResolverBean, EndpointResolverBean.endpointResolverBean)
    }  
  }

  def registryHCEnvResolverBean = {
    if (!ManagementFactory.getPlatformMBeanServer.isRegistered(EnvironmentResolverBean.environmentResolverBean)){
      ManagementFactory.getPlatformMBeanServer.registerMBean(EnvironmentResolverBean, EnvironmentResolverBean.environmentResolverBean)
    }
  }

  def registryHCCircuitBreakerBean = {
    if (!ManagementFactory.getPlatformMBeanServer.isRegistered(CircuitBreakerBean.circuitBreakerBean)){
      ManagementFactory.getPlatformMBeanServer.registerMBean(CircuitBreakerBean, CircuitBreakerBean.circuitBreakerBean)
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

object HttpClientBean extends HttpClientMXBean {

  val httpClientBean = "org.squbs.unicomplex:type=HttpClientInfo"

  override def getHttpClientInfo: java.util.List[HttpClientInfo] = {
		val httpClientsFromFactory = HttpClientFactory.httpClientMap.values
		val httpClientsFromManager = HttpClientManager.httpClientMap.values map {value: (Client, ActorRef) => value._1}
		(httpClientsFromFactory ++ httpClientsFromManager).toList map {mapToHttpClientInfo(_)}
	}

  def mapToHttpClientInfo(httpClient: Client) = {
    val name = httpClient.name
    val env  = httpClient.env.lowercaseName
    val endpoint = httpClient.endpoint.uri
    val status = httpClient.status.toString
    val configuration = httpClient.endpoint.config
    val pipelines = configuration.pipeline.getOrElse(SimplePipelineConfig.empty)
    val requestPipelines = pipelines.reqPipe.map(_.getClass.getName).foldLeft("")((l, r) => if (l == "") r else l + "=>" + r)
    val responsePipelines = pipelines.respPipe.map(_.getClass.getName).foldLeft("")((l, r) => if (l == "") r else l + "=>" + r)
    val connectionType = configuration.connectionType match {
      case Direct => "Direct"
      case AutoProxied => "AutoProxied"
      case Proxied(host, port) => s"$host:$port"
    }
    val hostSettings = configuration.hostSettings
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

object EndpointResolverBean extends EndpointResolverMXBean {

  val endpointResolverBean = "org.squbs.unicomplex:type=HttpClientEndpointResolverInfo"

  override def getHttpClientEndpointResolverInfo: util.List[EndpointResolverInfo] = {
    EndpointRegistry.endpointResolvers.zipWithIndex map {toEndpointResolverInfo(_)}
  }

  def toEndpointResolverInfo(resolverWithIndex: (EndpointResolver, Int)): EndpointResolverInfo = {
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

object EnvironmentResolverBean extends EnvironmentResolverMXBean {

  val environmentResolverBean = "org.squbs.unicomplex:type=HttpClientEnvironmentResolverInfo"

  override def getHttpClientEnvironmentResolverInfo: util.List[EnvironmentResolverInfo] = {
    EnvironmentRegistry.environmentResolvers.zipWithIndex map {toEnvironmentResolverInfo(_)}
  }

  def toEnvironmentResolverInfo(resolverWithIndex: (EnvironmentResolver, Int)): EnvironmentResolverInfo = {
    EnvironmentResolverInfo(resolverWithIndex._2, resolverWithIndex._1.getClass.getCanonicalName)
  }
}

// $COVERAGE-OFF$
case class CircuitBreakerInfo @ConstructorProperties(
  Array("name", "status", "lastDurationConfig", "successTimes", "fallbackTimes", "failFastTimes", "exceptionTimes", "lastDurationErrorRate", "lastDurationFailFastRate", "lastDurationExceptionRate"))(
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

object CircuitBreakerBean extends CircuitBreakerMXBean {

  val circuitBreakerBean = "org.squbs.unicomplex:type=HttpClientCircuitBreakerInfo"

  override def getHttpClientCircuitBreakerInfo: util.List[CircuitBreakerInfo] = {
    val httpClientsFromFactory = HttpClientFactory.httpClientMap.values
    val httpClientsFromManager = HttpClientManager.httpClientMap.values map {value: (Client, ActorRef) => value._1}
    (httpClientsFromFactory ++ httpClientsFromManager).toList map {mapToHttpClientCircuitBreakerInfo(_)}
  }

  def mapToHttpClientCircuitBreakerInfo(httpClient: Client) = {
    val name = httpClient.name
    val status = httpClient.cbMetrics.status.toString
    val lastDurationConfig = httpClient.endpoint.config.circuitBreakerConfig.lastDuration.toSeconds + " Seconds"
    val successTimes = httpClient.cbMetrics.successTimes
    val fallbackTimes = httpClient.cbMetrics.fallbackTimes
    val failFastTimes = httpClient.cbMetrics.failFastTimes
    val exceptionTimes = httpClient.cbMetrics.exceptionTimes
    val currentTime = System.currentTimeMillis
    val lastDuration = httpClient.endpoint.config.circuitBreakerConfig.lastDuration.toMillis
    httpClient.cbMetrics.cbLastDurationCall = httpClient.cbMetrics.cbLastDurationCall.dropWhile(_.callTime + lastDuration <= currentTime)
    val cbLastMinMetrics = httpClient.cbMetrics.cbLastDurationCall.groupBy[ServiceCallStatus](_.status) map { data =>
      data._1 -> data._2.size
    }
    val lastDurationSuccess = cbLastMinMetrics.get(ServiceCallStatus.Success).getOrElse(0)
    val lastDurationFallback = cbLastMinMetrics.get(ServiceCallStatus.Fallback).getOrElse(0)
    val lastDurationFailFast = cbLastMinMetrics.get(ServiceCallStatus.FailFast).getOrElse(0)
    val lastDurationException = cbLastMinMetrics.get(ServiceCallStatus.Exception).getOrElse(0)
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
    CircuitBreakerInfo(name, status, lastDurationConfig, successTimes, fallbackTimes, failFastTimes, exceptionTimes, lastDurationErrorRate, lastDurationFailFastRate, lastDurationExceptionRate)
  }
}



