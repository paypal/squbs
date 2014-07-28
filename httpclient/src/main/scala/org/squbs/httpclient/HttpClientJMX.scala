package org.squbs.httpclient

import java.beans.ConstructorProperties
import scala.beans.BeanProperty
import javax.management.{ObjectName, MXBean}
import org.squbs.httpclient.endpoint.{EndpointResolver, Endpoint, EndpointRegistry}
import org.squbs.httpclient.pipeline.EmptyPipeline
import spray.can.Http.ClientConnectionType.{Proxied, AutoProxied, Direct}
import java.util
import org.squbs.httpclient.env.{EnvironmentRegistry, EnvironmentResolver}
import scala.collection.JavaConversions._
import java.lang.management.ManagementFactory

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

/**
 * Created by hakuang on 6/9/2014.
 */

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

object HttpClientBean extends HttpClientMXBean with ConfigurationSupport {

  val httpClientBean = "org.squbs.unicomplex:type=HttpClientInfo"

  override def getHttpClientInfo: java.util.List[HttpClientInfo] = {
    val httpClients = HttpClientFactory.httpClientMap
    httpClients.values.toList map {mapToHttpClientInfo(_)}
  }

  def mapToHttpClientInfo(httpClient: Client) = {
    val name = httpClient.name
    val env  = httpClient.env.lowercaseName
    val endpoint = EndpointRegistry.resolve(name, httpClient.env).getOrElse(Endpoint("")).uri
    val status = httpClient.status.toString
    val configuration = config(httpClient)
    val pipelines = httpClient.pipeline.getOrElse(EmptyPipeline)
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



