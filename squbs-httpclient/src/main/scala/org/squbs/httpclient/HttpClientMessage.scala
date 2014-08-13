package org.squbs.httpclient

import org.squbs.httpclient.pipeline.Pipeline
import scala.Some
import org.squbs.httpclient.env.{Default, Environment}
import spray.httpx.BaseJson4sSupport

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
  case class Create(name: String, env: Environment = Default, pipeline: Option[Pipeline] = None) extends Client

  /**
   * Success => DeleteSuccess
   * Failure => HttpClientNotExistException
   * @param name
   * @param env
   */
  case class Delete(name: String, env: Environment = Default)

  case object DeleteSuccess

  /**
   * Success => DeleteAllSuccess
   */
  case object DeleteAll

  case object DeleteAllSuccess

  /**
   * Success => HttpClientActor
   * Failure => HttpClientNotExistException
   * @param name
   * @param env
   */
  case class Get(name: String, env: Environment = Default)

  /**
   * Success => TrieMap[(String, Environment), (Client, ActorRef)]
   */
  case object GetAll
}

object HttpClientActorMessage {

  /**
   * Success => UpdateSuccess
   * Failure => HttpClientNotExistException
   * @param config
   */
  case class Update(config: Configuration)

  case object UpdateSuccess

  /**
   * Success => MarkDownSuccess
   */
  case object MarkDown

  case object MarkDownSuccess

  /**
   * Success => MarkUpSuccess
   */
  case object MarkUp

  case object MarkUpSuccess


  /**
   * Success => CloseSuccess
    */
  case object Close

  case object CloseSuccess

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
   * @param json4sSupport
   * @tparam T
   */
//  case class Post[T: Marshaller](uri: String, content: Some[T], support: BaseJson4sSupport)
  case class Post[T <: AnyRef](uri: String, content: Some[T], json4sSupport: BaseJson4sSupport = org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol)

  /**
   * Success => HttpResponseWrapper(status: StatusCode, content: Right[HttpResponse])
   * Failure => HttpResponseWrapper(status: StatusCode, content: Left[Throwable])
   * @param uri
   * @param content
   * @param json4sSupport
   * @tparam T
   */
//  case class Put[T: Marshaller](uri: String, content: Some[T], support: BaseJson4sSupport)
  case class Put[T <: AnyRef](uri: String, content: Some[T], json4sSupport: BaseJson4sSupport = org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol)
}