package org.squbs.hc.actor

import org.squbs.hc.pipeline.PipelineDefinition
import spray.httpx.marshalling.Marshaller
import spray.http.{HttpResponse, StatusCode, HttpMethod}
import org.squbs.hc.config.Configuration
import org.squbs.hc._
import org.squbs.hc.routing.RoutingRegistry
import org.squbs.hc.config.Configuration
import org.squbs.hc.HttpClientExistException
import scala.Some
import org.squbs.hc.HttpClientNotExistException
import scala.collection.concurrent.TrieMap

/**
* Created by hakuang on 6/18/2014.
*/
object HttpClientMessage {

  /**
   * Success => CreateHttpClientSuccessMsg(hc:IHttpClient)
   * Failure => CreateHttpClientFailureMsg(e:HttpClientExistException)
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
   * Success => UpdateHttpClientSuccessMsg(hc:IHttpClient)
   * Failure => UpdateHttpClientFailureMsg(e:HttpClientNotExistException)
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
   * Success => DeleteHttpClientSuccessMsg(hc:IHttpClient)
   * Failure => DeleteHttpClientFailureMsg(e:HttpClientNotExistException)
   * @param name
   */
  case class DeleteHttpClientMsg(name: String)

  /**
   * Success => DeleteAllHttpClientSuccessMsg(map:TrieMap[String, IHttpClient])
   */
  case object DeleteAllHttpClientMsg
                              
  /**
   * Success => GetHttpClientSuccessMsg(hc:IHttpClient)
   * Failure => GetHttpClientFailureMsg(e:HttpClientNotExistException)
   * @param name
   */
  case class GetHttpClientMsg(name: String)

  /**
   * Success => GetAllHttpClientSuccessMsg(map:TrieMap[String, IHttpClient])
   */
  case object GetAllHttpClientMsg

  /**
   * Success => MarkDownHttpClientSuccessMsg(hc:IHttpClient)
   * Failure => MarkDownHttpClientFailureMsg(e:HttpClientNotExistException)
   * @param name
   */
  case class MarkDownHttpClientMsg(name: String)

  /**
   * Success => MarkUpHttpClientSuccessMsg(hc:IHttpClient)
   * Failure => MarkUpHttpClientFailureMsg(e:HttpClientNotExistException)
   * @param name
   */
  case class MarkUpHttpClientMsg(name: String)

  /**
   *  Success => HttpResponseWrapper(status: StatusCode, content: Right[HttpResponse])
   *  Failure => HttpResponseWrapper(status: StatusCode, content: Left[Throwable])
   *  Failure => HttpClientGetCallFailureMsg(e: HttpClientNotExistException)
   *  Failure => HttpClientGetCallFailureMsg(e: HttpClientNotSupportMethodException)
   * @param name
   * @param httpMethod
   * @param uri
   */
  case class HttpClientGetCallMsg(name: String, httpMethod: HttpMethod, uri: String)

  /**
   *  Success => HttpResponseWrapper(status: StatusCode, content: Right[HttpResponse])
   *  Failure => HttpResponseWrapper(status: StatusCode, content: Left[Throwable])
   *  Failure => HttpClientPostCallFailureMsg(e: HttpClientNotExistException)
   *  Failure => HttpClientPostCallFailureMsg(e: HttpClientNotSupportMethodException)
   * @param name
   * @param httpMethod
   * @param uri
   * @param content
   * @tparam T
   */
  case class HttpClientPostCallMsg[T: Marshaller](name: String, httpMethod: HttpMethod, uri: String, content: Some[T])

  class HttpClientSuccessMsg[T](content: T)
  case class CreateHttpClientSuccessMsg[IHttpClient](hc: IHttpClient) extends HttpClientSuccessMsg(hc)
  case class UpdateHttpClientSuccessMsg[IHttpClient](hc: IHttpClient) extends HttpClientSuccessMsg(hc)
  case class DeleteHttpClientSuccessMsg[IHttpClient](hc: IHttpClient) extends HttpClientSuccessMsg(hc)
  case class GetHttpClientSuccessMsg[IHttpClient](hc: IHttpClient) extends HttpClientSuccessMsg(hc)
  case class GetAllHttpClientSuccessMsg[TrieMap[String, IHttpClient]](map: TrieMap[String, IHttpClient]) extends HttpClientSuccessMsg(map)
  case class DeleteAllHttpClientSuccessMsg[TrieMap[String, IHttpClient]](map: TrieMap[String, IHttpClient]) extends HttpClientSuccessMsg(map)
  case class MarkUpHttpClientSuccessMsg[IHttpClient](hc: IHttpClient) extends HttpClientSuccessMsg(hc)
  case class MarkDownHttpClientSuccessMsg[IHttpClient](hc: IHttpClient) extends HttpClientSuccessMsg(hc)

  class HttpClientFailureMsg(e: HttpClientException)
  case class CreateHttpClientFailureMsg(e: HttpClientExistException) extends HttpClientFailureMsg(e)
  case class UpdateHttpClientFailureMsg(e: HttpClientNotExistException) extends HttpClientFailureMsg(e)
  case class DeleteHttpClientFailureMsg(e: HttpClientNotExistException) extends HttpClientFailureMsg(e)
  case class GetHttpClientFailureMsg(e: HttpClientNotExistException) extends HttpClientFailureMsg(e)
  case class MarkUpHttpClientFailureMsg(e: HttpClientNotExistException) extends HttpClientFailureMsg(e)
  case class MarkDownHttpClientFailureMsg(e: HttpClientNotExistException) extends HttpClientFailureMsg(e)
  case class HttpClientGetCallFailureMsg(e: HttpClientException) extends HttpClientFailureMsg(e)
  case class HttpClientPostCallFailureMsg(e: HttpClientException) extends HttpClientFailureMsg(e)

  
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
