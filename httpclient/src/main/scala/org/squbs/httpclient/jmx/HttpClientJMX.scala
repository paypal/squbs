package org.squbs.httpclient.jmx

import java.beans.ConstructorProperties
import scala.beans.BeanProperty
import javax.management.{MXBean}
import org.squbs.httpclient.endpoint.EndpointRegistry
import org.squbs.httpclient.pipeline.EmptyPipeline
import org.squbs.httpclient.config.ServiceConfiguration
import org.squbs.httpclient.config.Configuration
import org.squbs.httpclient.config.HostConfiguration
import org.squbs.httpclient.{HttpClientFactory, Client}
import spray.can.Http.ClientConnectionType.{Proxied, AutoProxied, Direct}

/**
 * Created by hakuang on 6/9/2014.
 */
case class HttpClientInfo @ConstructorProperties(
  Array("serviceName", "endpoint", "status", "maxRetryCount", "serviceTimeout", "connectionTimeout", "connectionType", "requestPipeline", "responsePipeline"))(
     @BeanProperty serviceName: String,
     @BeanProperty endpoint: String,
     @BeanProperty status: String,
     @BeanProperty maxRetryCount: Int,
     @BeanProperty serviceTimeout: Long,
     @BeanProperty connectionTimeout: Long,
     @BeanProperty connectionType: String,
     @BeanProperty requestPipeline: String,
     @BeanProperty responsePipeline: String)

@MXBean
trait HttpClientMXBean {
  def getHttpClient: java.util.List[HttpClientInfo]
}

class HttpClientBean extends HttpClientMXBean {

  import scala.collection.JavaConversions._

  override def getHttpClient: java.util.List[HttpClientInfo] = {
    val httpClients = HttpClientFactory.httpClientMap
    httpClients.values.toList map {mapToHttpClientInfo(_)}
  }

  def mapToHttpClientInfo(httpClient: Client) = {
    val name = httpClient.name
    val endpoint = EndpointRegistry.resolve(name).getOrElse("")
    val status = httpClient.status.toString
    val config = httpClient.config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration()))
    val svcConfig = config.svcConfig
    val hostConfig = config.hostConfig
    val pipelines = httpClient.pipeline.getOrElse(EmptyPipeline)
    val requestPipelines = pipelines.requestPipelines.map(_.getClass.getName).foldLeft[String]("")((result, each) => if (result == "") each else result + "=>" + each)
    val responsePipelines = pipelines.responsePipelines.map(_.getClass.getName).foldLeft[String]("")((result, each) => if (result == "") each else result + "=>" + each)
    val connectionType = hostConfig.connectionType match {
      case Direct => "Direct"
      case AutoProxied => "AutoProxied"
      case Proxied(host, port) => s"$host:$port"
    }
    HttpClientInfo(name, endpoint, status, svcConfig.maxRetryCount, svcConfig.serviceTimeout.toMillis, svcConfig.connectionTimeout.toMillis, connectionType, requestPipelines, responsePipelines)
  }
}



