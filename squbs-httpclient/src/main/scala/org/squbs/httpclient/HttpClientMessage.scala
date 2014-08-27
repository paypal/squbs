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

import scala.Some
import org.squbs.httpclient.env.{Default, Environment}
import spray.httpx.BaseJson4sSupport
import akka.pattern.CircuitBreaker
import akka.actor.ActorSystem
import org.squbs.httpclient.endpoint.EndpointRegistry
import spray.httpx.marshalling.Marshaller

object HttpClientManagerMessage {

  /**
   * Success => HttpClientActor
   * @param name
   * @param env
   */
  case class Get(name: String, env: Environment = Default)(implicit system: ActorSystem) extends Client {
    override val cb: CircuitBreaker = {
//      import scala.concurrent.ExecutionContext.Implicits.global
      implicit val ec = system.dispatcher
      EndpointRegistry.resolve(name, env) match {
        case Some(endpoint) =>
          HttpClientManager.httpClientMap.get((name, env)) match {
            case Some((client, _)) =>
              client.cb
            case None              =>
              val cbConfig = endpoint.config.circuitBreakerConfig
              new CircuitBreaker(system.scheduler, cbConfig.maxFailures, cbConfig.callTimeout, cbConfig.resetTimeout)
          }
        case None           =>
          throw HttpClientEndpointNotExistException(name, env)
      }
    }

    cb.onClose{
      cbStatus = CircuitBreakerStatus.Closed
    }

    cb.onOpen{
      cbStatus = CircuitBreakerStatus.Open
    }

    cb.onHalfOpen{
      cbStatus = CircuitBreakerStatus.HalfOpen
    }
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
   * Success => TrieMap[(String, Environment), (Client, ActorRef)]
   */
  case object GetAll
}

object HttpClientActorMessage {

  /**
   * Success => HttpClientActor
   * Failure => HttpClientNotExistException
   * @param config
   */
  case class Update(config: Configuration)

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
   * @param marshaller
   * @tparam T
   */
//  case class Post[T: Marshaller](uri: String, content: Some[T], support: BaseJson4sSupport)
//  case class Post[T <: AnyRef](uri: String, content: Some[T], json4sSupport: BaseJson4sSupport = org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol)
  case class Post[T <: AnyRef](uri: String, content: Some[T], marshaller: Marshaller[T] = org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol.json4sMarshaller)

  /**
   * Success => HttpResponseWrapper(status: StatusCode, content: Right[HttpResponse])
   * Failure => HttpResponseWrapper(status: StatusCode, content: Left[Throwable])
   * @param uri
   * @param content
   * @param marshaller
   * @tparam T
   */
//  case class Put[T: Marshaller](uri: String, content: Some[T], support: BaseJson4sSupport)
//  case class Put[T <: AnyRef](uri: String, content: Some[T], json4sSupport: BaseJson4sSupport = org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol)
  case class Put[T <: AnyRef](uri: String, content: Some[T], marshaller: Marshaller[T] = org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol.json4sMarshaller)
}