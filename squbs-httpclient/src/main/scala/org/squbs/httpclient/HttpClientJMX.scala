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

import java.beans.ConstructorProperties
import java.util
import javax.management.{MXBean, ObjectName}

import akka.actor.ActorSystem
import org.squbs.endpoint.{EndpointResolverRegistry, EndpointResolver}
import org.squbs.env.{EnvironmentRegistry, EnvironmentResolver}
import org.squbs.pipeline.PipelineSetting
import org.squbs.unicomplex.JMX
import spray.can.Http.ClientConnectionType.{AutoProxied, Direct, Proxied}

import scala.beans.BeanProperty
import scala.collection.JavaConversions._
import scala.language.implicitConversions

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
//      JMX.register(HttpClientBean(system), JMX.prefix(system) + httpClientBean)
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
//      JMX.register(CircuitBreakerBean(system), JMX.prefix(system) + circuitBreakerBean)
    }
  }
}

// $COVERAGE-OFF$
case class HttpClientInfo @ConstructorProperties(Array("name", "env", "endpoint", "status", "connectionType",
  "maxConnections", "maxRetries", "maxRedirects", "requestTimeout", "connectingTimeout", "requestPipelines",
  "responsePipelines")) (@BeanProperty name: String,
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
    EndpointResolverRegistry(system).endpointResolvers.zipWithIndex map toEndpointResolverInfo
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
case class CircuitBreakerInfo @ConstructorProperties(Array("name", "status", "historyUnitDuration", "successTimes",
  "fallbackTimes", "failFastTimes", "exceptionTimes", "history")) (@BeanProperty name: String,
                                                                   @BeanProperty status: String,
                                                                   @BeanProperty historyUnitDuration: String,
                                                                   @BeanProperty successTimes: Long,
                                                                   @BeanProperty fallbackTimes: Long,
                                                                   @BeanProperty failFastTimes: Long,
                                                                   @BeanProperty exceptionTimes: Long,
                                                                   @BeanProperty history: java.util.List[CBHistory])

case class CBHistory @ConstructorProperties(Array("period", "successes", "fallbacks", "failFasts", "exceptions",
  "errorRate", "failFastRate", "exceptionRate"))(@BeanProperty period: String,
                                                 @BeanProperty successes: Int,
                                                 @BeanProperty fallbacks: Int,
                                                 @BeanProperty failFasts: Int,
                                                 @BeanProperty exceptions: Int,
                                                 @BeanProperty errorRate: String,
                                                 @BeanProperty failFastRate: String,
                                                 @BeanProperty exceptionRate: String)

// $COVERAGE-ON$

trait CircuitBreakerMXBean {
  def getHttpClientCircuitBreakerInfo: java.util.List[CircuitBreakerInfo]
}
