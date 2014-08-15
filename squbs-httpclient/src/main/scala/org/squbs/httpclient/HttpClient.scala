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

import org.squbs.httpclient.endpoint.{Endpoint, EndpointRegistry}
import akka.actor.ActorSystem
import spray.client.pipelining._
import spray.httpx.unmarshalling._
import spray.httpx.marshalling.Marshaller
import scala.util.Try
import scala.concurrent._
import org.squbs.httpclient.pipeline.{Pipeline, PipelineManager}
import scala.util.Failure
import scala.Some
import scala.util.Success
import scala.collection.concurrent.TrieMap
import spray.http.{HttpResponse, HttpRequest}
import org.squbs.httpclient.env.{EnvironmentRegistry, Default, Environment}
import akka.pattern.CircuitBreaker
import spray.httpx.{UnsuccessfulResponseException, PipelineException}

object Status extends Enumeration {
  type Status = Value
  val UP, DOWN = Value
}

object CircuitBreakerStatus extends Enumeration {
  type CircuitBreakerStatus = Value
  val Closed, Open, HalfOpen = Value
}

trait Client {

  var status = Status.UP

  var cbStatus = CircuitBreakerStatus.Closed

  val cb: CircuitBreaker

  cb.onClose{
    cbStatus = CircuitBreakerStatus.Closed
  }

  cb.onOpen{
    cbStatus = CircuitBreakerStatus.Open
  }

  cb.onHalfOpen{
    cbStatus = CircuitBreakerStatus.HalfOpen
  }

  val name: String

  val env: Environment

  val pipeline: Option[Pipeline]

  var endpoint = EndpointRegistry.resolve(name, env)

  def markUp = {
    status = Status.UP
  }

  def markDown = {
    status = Status.DOWN
  }
}

trait ConfigurationSupport {
  def config(client: Client) = {
    client.endpoint match {
      case Some(endpoint) =>
        endpoint.config
      case None =>
        throw HttpClientEndpointNotExistException(client.name, client.env)
    }
  }

  def hostSettings(client: Client)(implicit actorSystem: ActorSystem) = {
    config(client).hostSettings
  }

  implicit def endpointToUri(endpoint: Option[Endpoint]): String = {
    endpoint.getOrElse(Endpoint("")).uri
  }
}

trait HttpCallSupport extends ConfigurationSupport with PipelineManager {

  import ExecutionContext.Implicits.global

  def client: Client

  def handle(pipeline: Try[HttpRequest => Future[HttpResponseWrapper]], httpRequest: HttpRequest)(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    pipeline match {
      case Success(res) =>
        val runCircuitBreaker = client.cb.withCircuitBreaker[HttpResponseWrapper](res(httpRequest))
        client.endpoint.get.config.circuitBreakerConfig.fallbackHttpResponse match {
          case Some(response) =>
            val fallbackResponse = future {
              HttpResponseWrapper(response.status, Right(response))
            }
            runCircuitBreaker fallbackTo fallbackResponse
          case None           =>
            runCircuitBreaker
        }
      case Failure(t@HttpClientMarkDownException(_, _)) => future {
        HttpResponseWrapper(HttpClientException.httpClientMarkDownError, Left(t))
      }
      case Failure(t) => future {
        HttpResponseWrapper(999, Left(t))
      }
    }
  }

  def get(uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(invokeToHttpResponse(client), Get(client.endpoint + uri))
  }

  def post[T: Marshaller](uri: String, content: Some[T])(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(invokeToHttpResponse(client), Post(client.endpoint + uri, content))
  }

  def put[T: Marshaller](uri: String, content: Some[T])(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(invokeToHttpResponse(client), Put(client.endpoint + uri, content))
  }

  def head(uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(invokeToHttpResponse(client), Head(client.endpoint + uri))
  }

  def delete(uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(invokeToHttpResponse(client), Delete(client.endpoint + uri))
  }

  def options(uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(invokeToHttpResponse(client), Options(client.endpoint + uri))
  }
}

trait HttpEntityCallSupport extends ConfigurationSupport with PipelineManager {

  import ExecutionContext.Implicits.global

  def client: Client

  def handleEntity[T: FromResponseUnmarshaller](pipeline: Try[HttpRequest => Future[HttpResponseEntityWrapper[T]]],
                                                httpRequest: HttpRequest)(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[T]] = {
    pipeline match {
      case Success(res) =>
        val runCircuitBreaker = client.cb.withCircuitBreaker[HttpResponseEntityWrapper[T]](res(httpRequest))
        client.endpoint.get.config.circuitBreakerConfig.fallbackHttpResponse match {
          case Some(response) =>
            val fallbackResponse = future {
              if (response.status.isSuccess)
                response.as[T] match {
                  case Right(value) ⇒ HttpResponseEntityWrapper[T](response.status, Right(value), Some(response))
                  case Left(error) ⇒ HttpResponseEntityWrapper[T](response.status, Left(throw new PipelineException(error.toString)), Some(response))
                }
              else HttpResponseEntityWrapper[T](response.status, Left(new UnsuccessfulResponseException(response)), Some(response))
            }
            runCircuitBreaker fallbackTo fallbackResponse
          case None           =>
            runCircuitBreaker
        }
      case Failure(t@HttpClientMarkDownException(_, _)) =>
        future {HttpResponseEntityWrapper[T](HttpClientException.httpClientMarkDownError, Left(t), None)}
      case Failure(t) =>
        future {HttpResponseEntityWrapper[T](999, Left(t), None)}
    }
  }

  def getEntity[R: FromResponseUnmarshaller](uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](invokeToEntity[R](client), Get(client.endpoint + uri))
  }

  def postEntity[T: Marshaller, R: FromResponseUnmarshaller](uri: String, content: Some[T])(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](invokeToEntity[R](client), Post(client.endpoint + uri, content))
  }

  def putEntity[T: Marshaller, R: FromResponseUnmarshaller](uri: String, content: Some[T])(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](invokeToEntity[R](client), Put(client.endpoint + uri, content))
  }

  def headEntity[R: FromResponseUnmarshaller](uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](invokeToEntity[R](client), Head(client.endpoint + uri))
  }

  def deleteEntity[R: FromResponseUnmarshaller](uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](invokeToEntity[R](client), Delete(client.endpoint + uri))
  }

  def optionsEntity[R: FromResponseUnmarshaller](uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](invokeToEntity[R](client), Options(client.endpoint + uri))
  }
}

trait HttpClientSupport extends HttpCallSupport with HttpEntityCallSupport

case class HttpClient(name: String,
                      env: Environment = Default,
                      pipeline: Option[Pipeline] = None,
                      cb: CircuitBreaker) extends Client with HttpClientSupport {

  require(endpoint != None, "endpoint should be resolved!")
  Endpoint.check(endpoint.get.uri)

  def client: Client = this

  def withConfig(config: Configuration): HttpClient = {
    endpoint = Some(Endpoint(endpoint.get.uri, config))
    HttpClientFactory.httpClientMap.put((name, env), this)
    this
  }

  def withFallback(response: HttpResponse): HttpClient = {
    val oldConfig = endpoint.get.config
    val cbConfig = oldConfig.circuitBreakerConfig.copy(fallbackHttpResponse = Some(response))
    val newConfig = oldConfig.copy(circuitBreakerConfig = cbConfig)
    endpoint = Some(Endpoint(endpoint.get.uri, newConfig))
    HttpClientFactory.httpClientMap.put((name, env), this)
    this
  }
}

object HttpClientFactory {

  import scala.concurrent.ExecutionContext.Implicits.global

  HttpClientJMX.registryBeans

  val httpClientMap: TrieMap[(String, Environment), HttpClient] = TrieMap[(String, Environment), HttpClient]()

  def getOrCreate(name: String)(implicit system: ActorSystem): HttpClient = {
    getOrCreate(name, Default)
  }

  def getOrCreate(name: String, env: Environment)(implicit system: ActorSystem): HttpClient = {
    getOrCreate(name, env, None)
  }

  def getOrCreate(name: String, env: Environment = Default, pipeline: Option[Pipeline] = None)(implicit system: ActorSystem): HttpClient = {
    val newEnv = env match {
      case Default => EnvironmentRegistry.resolve(name)
      case _ => env
    }
    httpClientMap.get((name, newEnv)) match {
      case Some(httpClient) =>
        httpClient
      case None             =>
        EndpointRegistry.resolve(name, env) match {
          case Some(endpoint) =>
            val cbConfig = endpoint.config.circuitBreakerConfig
            val cb = new CircuitBreaker(system.scheduler, cbConfig.maxFailures, cbConfig.callTimeout, cbConfig.resetTimeout)
            val httpClient = HttpClient(name, newEnv, pipeline, cb)
            httpClientMap.put((name, env), httpClient)
            httpClient
          case None           =>
            throw HttpClientEndpointNotExistException(name, env)
        }
    }
  }
}