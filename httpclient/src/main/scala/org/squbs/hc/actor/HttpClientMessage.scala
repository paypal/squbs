package org.squbs.hc.actor

import org.squbs.hc.pipeline.PipelineDefinition
import spray.httpx.marshalling.Marshaller
import spray.http.HttpMethod
import org.squbs.hc.config.Configuration
import org.squbs.hc.{HttpClientException, IHttpClient}
import org.squbs.hc.routing.RoutingRegistry

/**
* Created by hakuang on 6/18/2014.
*/
object HttpClientMessage {

  /**
   * Success => IHttpClient
   * Failure => HttpClientExistException
   * @param name
   * @param env
   * @param config
   * @param pipelineDefinition
   */
  case class CreateHttpClientMsg(name: String,
                                env: Option[String] = None,
                                config: Option[Configuration] = None,
                                pipelineDefinition: Option[PipelineDefinition] = None) extends IHttpClient {
    override def endpoint: String = RoutingRegistry.resolve(name, env).getOrElse("")
  }

  /**
   * Success => IHttpClient
   * Failure => HttpClientNotExistException
   * @param name
   * @param env
   * @param config
   * @param pipelineDefinition
   */
  case class UpdateHttpClientMsg(name: String,
                                env: Option[String] = None,
                                config: Option[Configuration] = None,
                                pipelineDefinition: Option[PipelineDefinition] = None) extends IHttpClient {
    override def endpoint: String = RoutingRegistry.resolve(name, env).getOrElse("")
  }

  /**
   * Success => IHttpClient
   * Failure => HttpClientNotExistException
   * @param name
   */
  case class DeleteHttpClientMsg(name: String)

  /**
   * Success => IHttpClient
   * Failure => HttpClientNotExistException
   * @param name
   */
  case class GetHttpClientMsg(name: String)

  /**
   * Success => HTrieMap[String, IHttpClient]
   */
  case object GetAllHttpClientMsg

  /**
   * Success => IHttpClient
   * Failure => HttpClientNotExistException
   * @param name
   */
  case class MarkDownHttpClientMsg(name: String)

  /**
   * Success => IHttpClient
   * Failure => HttpClientNotExistException
   * @param name
   */
  case class MarkUpHttpClientMsg(name: String)

  /**
   *  Success => HttpResponseWrapper(status: StatusCode, content: Right[HttpResponse])
   *  Failure => HttpResponseWrapper(status: StatusCode, content: Left[Throwable])
   * @param name
   * @param httpMethod
   * @param uri
   */
  case class HttpClientGetMsg(name: String, httpMethod: HttpMethod, uri: String)

  /**
   *  Success => HttpResponseWrapper(status: StatusCode, content: Right[HttpResponse])
   *  Failure => HttpResponseWrapper(status: StatusCode, content: Left[Throwable])
   * @param name
   * @param httpMethod
   * @param uri
   * @param content
   * @tparam T
   */
  case class HttpClientPostMsg[T: Marshaller](name: String, httpMethod: HttpMethod, uri: String, content: Some[T])

//  case class HttpClientCallSuccess[T](content: T)
//
//  case class HttpClientCallFailure(e: HttpClientException)
  
//  case class HttpClientEntityMsg[T: Marshaller, R: FromResponseUnmarshaller](name: String,
//                                                                             uri: String,
//                                                                             httpMethod: HttpMethod,
//                                                                             content: Option[T],
//                                                                             env: Option[String] = None,
//                                                                             config: Option[Configuration] = None,
//                                                                             pipelineDefinition: Option[PipelineDefinition] = None) extends IHttpClient {
//    override def endpoint: String = RoutingRegistry.resolve(name, env).getOrElse("")
//  }
}
