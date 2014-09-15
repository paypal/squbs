/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the AUTHORS file distributed with this work for
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
import spray.httpx.marshalling.Marshaller
import scala.concurrent._
import org.squbs.httpclient.pipeline.PipelineManager
import scala.util.{Try, Success, Failure}
import scala.collection.concurrent.TrieMap
import spray.http.{HttpResponse, HttpRequest}
import org.squbs.httpclient.env.{EnvironmentRegistry, Default, Environment}
import akka.pattern.CircuitBreaker
import scala.collection.mutable.ListBuffer

object Status extends Enumeration {
  type Status = Value
  val UP, DOWN = Value
}

trait Client {

  var status = Status.UP

  var cbMetrics = CircuitBreakerMetrics(CircuitBreakerStatus.Closed, 0, 0, 0, 0, ListBuffer.empty[ServiceCall])

  val cb: CircuitBreaker

  val name: String

  val env: Environment

  var endpoint = {
    val serviceEndpoint = EndpointRegistry.resolve(name, env)
    serviceEndpoint match {
      case Some(se) => se
      case None     => throw HttpClientEndpointNotExistException(name, env)
    }
  }

  def markUp = {
    status = Status.UP
  }

  def markDown = {
    status = Status.DOWN
  }
}

trait HttpCallSupport extends PipelineManager with CircuitBreakerSupport {

  def client: Client

  def handle(pipeline: Try[HttpRequest => Future[HttpResponse]], httpRequest: HttpRequest)(implicit system: ActorSystem): Future[HttpResponse] = {
    implicit val ec = system.dispatcher
    pipeline match {
      case Success(res) =>
        withCircuitBreaker(client, res(httpRequest))
      case Failure(t@HttpClientMarkDownException(_, _)) =>
        httpClientLogger.debug("HttpClient has been marked down!", t)
        collectCbMetrics(client, ServiceCallStatus.Exception)
        future {throw t}
      case Failure(t) =>
        httpClientLogger.debug("HttpClient Pipeline execution failure!", t)
        collectCbMetrics(client, ServiceCallStatus.Exception)
        future {throw t}
    }
  }

  def get(uri: String)(implicit system: ActorSystem): Future[HttpResponse] = {
    httpClientLogger.debug("Service call url is:" + (client.endpoint + uri))
    handle(invokeToHttpResponse(client), Get(uri))
  }

  def post[T: Marshaller](uri: String, content: T)(implicit system: ActorSystem): Future[HttpResponse] = {
    httpClientLogger.debug("Service call url is:" + (client.endpoint + uri))
    handle(invokeToHttpResponse(client), Post(uri, content))
  }

  def put[T: Marshaller](uri: String, content: T)(implicit system: ActorSystem): Future[HttpResponse] = {
    httpClientLogger.debug("Service call url is:" + (client.endpoint + uri))
    handle(invokeToHttpResponse(client), Put(uri, content))
  }

  def head(uri: String)(implicit system: ActorSystem): Future[HttpResponse] = {
    httpClientLogger.debug("Service call url is:" + (client.endpoint + uri))
    handle(invokeToHttpResponse(client), Head(uri))
  }

  def delete(uri: String)(implicit system: ActorSystem): Future[HttpResponse] = {
    httpClientLogger.debug("Service call url is:" + (client.endpoint + uri))
    handle(invokeToHttpResponse(client), Delete(uri))
  }

  def options(uri: String)(implicit system: ActorSystem): Future[HttpResponse] = {
    httpClientLogger.debug("Service call url is:" + (client.endpoint + uri))
    handle(invokeToHttpResponse(client), Options(uri))
  }
}

trait HttpClientSupport extends HttpCallSupport

case class HttpClient(name: String,
                      env: Environment = Default)(implicit system: ActorSystem) extends Client with HttpClientSupport {

  Endpoint.check(endpoint.uri)

  override val cb: CircuitBreaker = {
    val cbConfig = endpoint.config.circuitBreakerConfig
    new CircuitBreaker(system.scheduler, cbConfig.maxFailures, cbConfig.callTimeout, cbConfig.resetTimeout)(system.dispatcher)
  }

  def client: Client = this

  cb.onClose{
    cbMetrics.status= CircuitBreakerStatus.Closed
  }

  cb.onOpen{
    cbMetrics.status = CircuitBreakerStatus.Open
  }

  cb.onHalfOpen{
    cbMetrics.status = CircuitBreakerStatus.HalfOpen
  }

  def withConfig(config: Configuration): HttpClient = {
    val hc = HttpClient(name, env)
    hc.endpoint = Endpoint(hc.endpoint.uri, config)
    HttpClientFactory.httpClientMap.put((name, env), hc)
    hc
  }

  def withFallback(response: HttpResponse): HttpClient = {
    val oldConfig = endpoint.config
    val cbConfig = oldConfig.circuitBreakerConfig.copy(fallbackHttpResponse = Some(response))
    val newConfig = oldConfig.copy(circuitBreakerConfig = cbConfig)
    endpoint = Endpoint(endpoint.uri, newConfig)
    HttpClientFactory.httpClientMap.put((name, env), this)
    this
  }

}

object HttpClientFactory {

  HttpClientJMX.registryBeans

  val httpClientMap = TrieMap.empty[(String, Environment), HttpClient]

  def get(name: String)(implicit system: ActorSystem): HttpClient = {
    get(name, Default)
  }

  def get(name: String, env: Environment = Default)(implicit system: ActorSystem): HttpClient = {
    val newEnv = env match {
      case Default => EnvironmentRegistry.resolve(name)
      case _ => env
    }
    httpClientMap.get((name, newEnv)) match {
      case Some(httpClient) =>
        httpClient
      case None             =>
        val httpClient = HttpClient(name, newEnv)
        httpClientMap.put((name, env), httpClient)
        httpClient
    }
  }
}