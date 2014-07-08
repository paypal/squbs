package org.squbs.hc.actor

import org.squbs.hc.pipeline.PipelineDefinition
import spray.httpx.marshalling.Marshaller
import spray.http.{HttpMethod}
import org.squbs.hc._
import org.squbs.hc.config.Configuration
import org.squbs.hc.HttpClientExistException
import scala.Some
import org.squbs.hc.HttpClientNotExistException

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
                                pipelineDefinition: Option[PipelineDefinition] = None) extends IHttpClient

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
                                pipelineDefinition: Option[PipelineDefinition] = None) extends IHttpClient

  /**
   * Success => DeleteHttpClientSuccessMsg(hc:IHttpClient)
   * Failure => DeleteHttpClientFailureMsg(e:HttpClientNotExistException)
   * @param name
   */
  case class DeleteHttpClientMsg(name: String, env: Option[String] = None)

  /**
   * Success => DeleteAllHttpClientSuccessMsg(map:TrieMap[(String, Option[String]), IHttpClient])
   */
  case object DeleteAllHttpClientMsg
                              
  /**
   * Success => GetHttpClientSuccessMsg(hc:IHttpClient)
   * Failure => GetHttpClientFailureMsg(e:HttpClientNotExistException)
   * @param name
   */
  case class GetHttpClientMsg(name: String, env: Option[String] = None)

  /**
   * Success => GetAllHttpClientSuccessMsg(map:TrieMap[(String, Option[String], IHttpClient])
   */
  case object GetAllHttpClientMsg

  /**
   * Success => MarkDownHttpClientSuccessMsg(hc:IHttpClient)
   * Failure => MarkDownHttpClientFailureMsg(e:HttpClientNotExistException)
   * @param name
   */
  case class MarkDownHttpClientMsg(name: String, env: Option[String] = None)

  /**
   * Success => MarkUpHttpClientSuccessMsg(hc:IHttpClient)
   * Failure => MarkUpHttpClientFailureMsg(e:HttpClientNotExistException)
   * @param name
   */
  case class MarkUpHttpClientMsg(name: String, env: Option[String] = None)

  /**
   *  Success => HttpResponseWrapper(status: StatusCode, content: Right[HttpResponse])
   *  Failure => HttpResponseWrapper(status: StatusCode, content: Left[Throwable])
   *  Failure => HttpClientGetCallFailureMsg(e: HttpClientNotExistException)
   *  Failure => HttpClientGetCallFailureMsg(e: HttpClientNotSupportMethodException)
   * @param name
   * @param httpMethod
   * @param uri
   */
  case class HttpClientGetCallMsg(name: String, env: Option[String] = None, httpMethod: HttpMethod, uri: String)

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
  case class HttpClientPostCallMsg[T: Marshaller](name: String, env: Option[String] = None, httpMethod: HttpMethod, uri: String, content: Some[T])

  type HCKEY = (String, Option[String])
  
  class HttpClientSuccessMsg[T](content: T)
  case class CreateHttpClientSuccessMsg[IHttpClient](hc: IHttpClient) extends HttpClientSuccessMsg(hc)
  case class UpdateHttpClientSuccessMsg[IHttpClient](hc: IHttpClient) extends HttpClientSuccessMsg(hc)
  case class DeleteHttpClientSuccessMsg[IHttpClient](hc: IHttpClient) extends HttpClientSuccessMsg(hc)
  case class GetHttpClientSuccessMsg[IHttpClient](hc: IHttpClient) extends HttpClientSuccessMsg(hc)
  case class GetAllHttpClientSuccessMsg[TrieMap[HCKEY, IHttpClient]](map: TrieMap[HCKEY, IHttpClient]) extends HttpClientSuccessMsg(map)
  case class DeleteAllHttpClientSuccessMsg[TrieMap[HCKEY, IHttpClient]](map: TrieMap[HCKEY, IHttpClient]) extends HttpClientSuccessMsg(map)
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

}
