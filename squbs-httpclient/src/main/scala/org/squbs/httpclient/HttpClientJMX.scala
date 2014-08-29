/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the CONTRIBUTING file distributed with this work for
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
import scala.beans.BeanProperty
import javax.management.{ObjectName, MXBean}
import org.squbs.httpclient.endpoint.{EndpointResolver, EndpointRegistry}
import org.squbs.httpclient.pipeline.EmptyPipeline
import spray.can.Http.ClientConnectionType.{Proxied, AutoProxied, Direct}
import java.util
import org.squbs.httpclient.env.{EnvironmentRegistry, EnvironmentResolver}
import scala.collection.JavaConversions._
import java.lang.management.ManagementFactory
import akka.actor.ActorRef
import org.squbs.httpclient.ServiceCallStatus.ServiceCallStatus

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
    val pipelines = configuration.pipeline.getOrElse(EmptyPipeline)
    val requestPipelines = pipelines.requestPipelines.map(_.getClass.getName).foldLeft[String]("")((result, each) => if (result == "") each else result + "=>" + each)
    val responsePipelines = pipelines.responsePipelines.map(_.getClass.getName).foldLeft[String]("")((result, each) => if (result == "") each else result + "=>" + each)
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

case class CircuitBreakerInfo @ConstructorProperties(
  Array("name", "status", "successTimes", "fallbackTimes", "failFastTimes", "exceptionTimes", "lastMinErrorRate", "lastMinFailFastRate", "lastMinExceptionRate"))(
    @BeanProperty name: String,
    @BeanProperty status: String,
    @BeanProperty successTimes: Long,
    @BeanProperty fallbackTimes: Long,
    @BeanProperty failFastTimes: Long,
    @BeanProperty exceptionTimes: Long,
    @BeanProperty lastMinErrorRate: String,
    @BeanProperty lastMinFailFastRate: String,
    @BeanProperty lastMinExceptionRate: String
  )

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
    val successTimes = httpClient.cbMetrics.successTimes
    val fallbackTimes = httpClient.cbMetrics.fallbackTimes
    val failFastTimes = httpClient.cbMetrics.failFastTimes
    val exceptionTimes = httpClient.cbMetrics.exceptionTimes
    val currentTime = System.currentTimeMillis
    val lastDuration = httpClient.endpoint.config.circuitBreakerConfig.lastDurationMetrics.toMillis
    httpClient.cbMetrics.cbLastMinCall = httpClient.cbMetrics.cbLastMinCall.dropWhile(_.callTime + lastDuration <= currentTime)
    val cbLastMinMetrics = httpClient.cbMetrics.cbLastMinCall.groupBy[ServiceCallStatus](_.status) map { data =>
      data._1 -> data._2.size
    }
    println(cbLastMinMetrics)
    val lastMinSuccess = cbLastMinMetrics.get(ServiceCallStatus.Success).getOrElse(0)
    val lastMinFallback = cbLastMinMetrics.get(ServiceCallStatus.Fallback).getOrElse(0)
    val lastMinFailFast = cbLastMinMetrics.get(ServiceCallStatus.FailFast).getOrElse(0)
    val lastMinException = cbLastMinMetrics.get(ServiceCallStatus.Exception).getOrElse(0)
    val lastMinTotal = lastMinSuccess + lastMinFallback + lastMinFailFast + lastMinException
    val df = new java.text.DecimalFormat("0.00%")
    val lastMinErrorRate = lastMinTotal match {
      case 0 => "0%"
      case _ => df.format((lastMinTotal - lastMinSuccess) * 1.0 / lastMinTotal)
    }
    val lastMinFailFastRate = lastMinTotal match {
      case 0 => "0%"
      case _ => df.format(lastMinFallback * 1.0 / lastMinTotal)
    }
    val lastMinExceptionRate = lastMinTotal match {
      case 0 => "0%"
      case _ => df.format(lastMinException * 1.0 / lastMinTotal)
    }
    CircuitBreakerInfo(name, status, successTimes, fallbackTimes, failFastTimes, exceptionTimes, lastMinErrorRate, lastMinFailFastRate, lastMinExceptionRate)
  }
}



