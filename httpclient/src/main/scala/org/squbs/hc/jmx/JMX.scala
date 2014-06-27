package org.squbs.hc.jmx

import java.beans.ConstructorProperties
import scala.beans.BeanProperty
import javax.management.{MXBean}
import org.squbs.hc.routing.RoutingRegistry
import org.squbs.hc.pipeline.EmptyPipelineDefinition
import org.squbs.hc.config.ServiceConfiguration
import org.squbs.hc.config.Configuration
import org.squbs.hc.config.HostConfiguration
import org.squbs.hc.{HttpClientFactory, IHttpClient, HttpClient}
import spray.can.Http.ClientConnectionType.{Proxied, AutoProxied, Direct}
import org.squbs.hc.actor.HttpClientManager

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
  def getInfo: java.util.List[HttpClientInfo]
}

object HttpClientBean extends HttpClientMXBean {

  val HTTPCLIENTNFO = "org.squbs.unicomplex:type=HttpClient"

  import scala.collection.JavaConversions._

  override def getInfo: java.util.List[HttpClientInfo] = {
    val httpClients = HttpClientFactory.httpClientMap
    val httpClientActors = HttpClientManager.httpClientMap
    (httpClientActors ++ httpClients).values.toList map {mapToHttpClientInfo(_)}
  }

  def mapToHttpClientInfo(httpClient: IHttpClient) = {
    val name = httpClient.name
    val endpoint = RoutingRegistry.resolve(name).getOrElse("")
    val status = httpClient.status.toString
    val config = httpClient.config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration()))
    val svcConfig = config.svcConfig
    val hostConfig = config.hostConfig
    val pipelines = httpClient.pipelineDefinition.getOrElse(EmptyPipelineDefinition)
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



