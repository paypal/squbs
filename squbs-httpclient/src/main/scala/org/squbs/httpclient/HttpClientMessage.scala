/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the CONTRIBUTING file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.squbs.httpclient

import org.squbs.httpclient.pipeline.Pipeline
import scala.Some
import org.squbs.httpclient.env.{Default, Environment}
import spray.httpx.BaseJson4sSupport
import akka.pattern.CircuitBreaker

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
  case class Create(name: String, env: Environment = Default, pipeline: Option[Pipeline] = None) extends Client {
    override val cb: CircuitBreaker = ???
  }

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