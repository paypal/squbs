package org.squbs.hc.actor

import org.squbs.hc.pipeline.PipelineDefinition
import spray.httpx.marshalling.Marshaller
import spray.http.HttpMethod
import spray.httpx.unmarshalling._
import org.squbs.hc.config.Configuration
import org.squbs.hc.IHttpClient
import org.squbs.hc.routing.RoutingRegistry

/**
* Created by hakuang on 6/18/2014.
*/
object HttpClientMessage {

  case class HttpClientMsg[T: Marshaller](name: String,
                                          uri: String,
                                          httpMethod: HttpMethod,
                                          content: Option[T],
                                          env: Option[String] = None,
                                          config: Option[Configuration] = None,
                                          pipelineDefinition: Option[PipelineDefinition] = None) extends IHttpClient {
    override def endpoint: String = RoutingRegistry.resolve(name, env).getOrElse("")
  }

  case class HttpClientEntityMsg[T: Marshaller, R: FromResponseUnmarshaller](name: String,
                                                                             uri: String,
                                                                             httpMethod: HttpMethod,
                                                                             content: Option[T],
                                                                             env: Option[String] = None,
                                                                             config: Option[Configuration] = None,
                                                                             pipelineDefinition: Option[PipelineDefinition] = None) extends IHttpClient {
    override def endpoint: String = RoutingRegistry.resolve(name, env).getOrElse("")
  }
}
