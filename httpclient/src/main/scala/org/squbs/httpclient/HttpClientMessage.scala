package org.squbs.httpclient

import org.squbs.httpclient.pipeline.Pipeline
import spray.httpx.marshalling.Marshaller
import scala.Some
import org.squbs.httpclient.env.{Default, Environment}

/**
* Created by hakuang on 6/18/2014.
*/
object HttpClientManagerMessage {

  /**
   * Success => HttpClientActor
   * Failure => HttpClientExistException
   * @param name
   * @param env
   * @param pipeline
   */
  case class CreateHttpClient(name: String, env: Environment = Default, pipeline: Option[Pipeline] = None) extends Client

  /**
   * Success => DeleteHttpClientSuccess
   * Failure => HttpClientNotExistException
   * @param name
   * @param env
   */
  case class DeleteHttpClient(name: String, env: Environment = Default)

  case object DeleteHttpClientSuccess

  /**
   * Success => DeleteAllHttpClientSuccess
   */
  case object DeleteAllHttpClient

  case object DeleteAllHttpClientSuccess

  /**
   * Success => HttpClientActor
   * Failure => HttpClientNotExistException
   * @param name
   * @param env
   */
  case class GetHttpClient(name: String, env: Environment = Default)

  /**
   * Success => TrieMap[(String, Environment), (Client, ActorRef)])
   */
  case object GetAllHttpClient
}

object HttpClientActorMessage {

  /**
   * Success => UpdateHttpClientSuccess
   * Failure => HttpClientNotExistException
   * @param config
   */
  case class UpdateConfig(config: Configuration)

  case object UpdateHttpClientSuccess

  /**
   * Success => MarkDownHttpClientSuccess
   */
  case object MarkDown

  case object MarkDownHttpClientSuccess

  /**
   * Success => MarkUpHttpClientSuccess
   */
  case object MarkUp

  case object MarkUpHttpClientSuccess


  /**
   * Success => CloseHttpClientSuccess
    */
  case object Close

  case object CloseHttpClientSuccess

  /**
   * Success => HttpResponseWrapper(status: StatusCode, content: Right[HttpResponse])
   * Failure => HttpResponseWrapper(status: StatusCode, content: Left[Throwable])
   * @param uri
   */
  case class Get(uri: String)

  /**
   * Success => HttpResponseWrapper(status: StatusCode, content: Right[HttpResponse])
   * Failure => HttpResponseWrapper(status: StatusCode, content: Left[Throwable])
   * @param uri
   */
  case class Options(uri: String)

  /**
   * Success => HttpResponseWrapper(status: StatusCode, content: Right[HttpResponse])
   * Failure => HttpResponseWrapper(status: StatusCode, content: Left[Throwable])
   * @param uri
   */
  case class Head(uri: String)

  /**
   * Success => HttpResponseWrapper(status: StatusCode, content: Right[HttpResponse])
   * Failure => HttpResponseWrapper(status: StatusCode, content: Left[Throwable])
   * @param uri
   */
  case class Delete(uri: String)

  /**
   * Success => HttpResponseWrapper(status: StatusCode, content: Right[HttpResponse])
   * Failure => HttpResponseWrapper(status: StatusCode, content: Left[Throwable])
   * @param uri
   * @param content
   * @tparam T
   */
  case class Post[T: Marshaller](uri: String, content: Some[T])

  /**
   * Success => HttpResponseWrapper(status: StatusCode, content: Right[HttpResponse])
   * Failure => HttpResponseWrapper(status: StatusCode, content: Left[Throwable])
   * @param uri
   * @param content
   * @tparam T
   */
  case class Put[T: Marshaller](uri: String, content: Some[T])
}