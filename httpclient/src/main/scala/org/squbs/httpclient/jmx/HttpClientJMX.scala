package org.squbs.httpclient.jmx

import java.beans.ConstructorProperties
import scala.beans.BeanProperty
import javax.management.MXBean
import org.squbs.httpclient.endpoint.{Endpoint, EndpointRegistry}
import org.squbs.httpclient.pipeline.EmptyPipeline
import org.squbs.httpclient.{ConfigurationSupport, HttpClientFactory, Client}
import spray.can.Http.ClientConnectionType.{Proxied, AutoProxied, Direct}
import akka.actor.ActorSystem
import spray.can.client.HostConnectorSettings

/**
 * Created by hakuang on 6/9/2014.
 */
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

@MXBean
trait HttpClientMXBean {
  def getHttpClient(implicit actorSystem: ActorSystem): java.util.List[HttpClientInfo]
}

class HttpClientBean extends HttpClientMXBean with ConfigurationSupport {

  import scala.collection.JavaConversions._

  override def getHttpClient(implicit actorSystem: ActorSystem): java.util.List[HttpClientInfo] = {
    val httpClients = HttpClientFactory.httpClientMap
    httpClients.values.toList map {mapToHttpClientInfo(_)}
  }

  def mapToHttpClientInfo(httpClient: Client)(implicit actorSystem: ActorSystem) = {
    val name = httpClient.name
    val env  = httpClient.env.lowercaseName
    val endpoint = EndpointRegistry.resolve(name).getOrElse(Endpoint("")).uri
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
    val hostSettings = configuration.hostSettings.getOrElse(HostConnectorSettings(actorSystem))
    val maxConnections = hostSettings.maxConnections
    val maxRetries = hostSettings.maxRetries
    val maxRedirects = hostSettings.maxRedirects
    val requestTimeout = hostSettings.connectionSettings.requestTimeout.toMillis
    val connectingTimeout = hostSettings.connectionSettings.connectingTimeout.toMillis
    HttpClientInfo(name, env, endpoint, status, connectionType, maxConnections, maxRetries, maxRedirects,
                   requestTimeout, connectingTimeout, requestPipelines, responsePipelines)
  }
}



