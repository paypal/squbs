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

import akka.util.Timeout
import org.slf4j.LoggerFactory
import org.squbs.httpclient.HttpClientActorMessage.{MarkUpSuccess, MarkDownSuccess}
import org.squbs.httpclient.endpoint.EndpointRegistry
import akka.actor.{ActorRef, ActorSystem}
import org.squbs.proxy.SimplePipelineConfig
import spray.httpx.marshalling.Marshaller
import spray.httpx.unmarshalling.FromResponseUnmarshaller
import scala.concurrent._
import spray.http.{Uri, HttpResponse}
import org.squbs.httpclient.env.{Default, Environment}
import akka.pattern.CircuitBreaker
import scala.collection.mutable.ListBuffer
import akka.pattern._
import org.squbs.httpclient.pipeline.HttpClientUnmarshal._
import scala.concurrent.duration._

object Status extends Enumeration {
  type Status = Value
  val UP, DOWN = Value
}

trait HttpClientPathBuilder {

  val logger = LoggerFactory.getLogger(this.getClass)

  def buildRequestUri(path: String, paramMap: Map[String, Any] = Map.empty[String, Any]): String = {
    if (paramMap.isEmpty) {
      Uri(path).toString
    } else {
      var trimPath = path
      if (path.length > 1 && path.charAt(path.length - 1) == '/'){
        trimPath = path.substring(0, path.length - 1)
      }
      val filteredParamMap = paramMap.filter{ pair =>
        pair._2.isInstanceOf[String] ||
        pair._2.isInstanceOf[Double] ||
        pair._2.isInstanceOf[Float]  ||
        pair._2.isInstanceOf[Int]    ||
        pair._2.isInstanceOf[Short]  ||
        pair._2.isInstanceOf[Byte]   ||
        pair._2.isInstanceOf[Char]   ||
        pair._2.isInstanceOf[Boolean]
      }.map(pair => (pair._1, pair._2.toString))
      Uri(trimPath).withQuery(filteredParamMap).toString
    }
  }
}

object HttpClientPathBuilder extends HttpClientPathBuilder

case class HttpClient(name: String,
                      env: Environment = Default,
                      var status: Status.Status = Status.UP,
                      var config: Option[Configuration] = None,
                      var fActorRef: Future[ActorRef] = null)(implicit system: ActorSystem) {

  implicit val ec = system.dispatcher

  val endpoint = {
    val serviceEndpoint = EndpointRegistry(system).resolve(name, env)
    serviceEndpoint match {
      case Some(se) => config match {
        case Some(conf) => se.copy(config = conf)
        case None       => se
      }
      case None     => throw HttpClientEndpointNotExistException(name, env)
    }
  }

  val cbMetrics = CircuitBreakerMetrics(CircuitBreakerStatus.Closed, 0, 0, 0, 0, ListBuffer.empty[ServiceCall])

  val cb: CircuitBreaker = {
    val cbConfig = endpoint.config.settings.circuitBreakerConfig
    new CircuitBreaker(system.scheduler, cbConfig.maxFailures, cbConfig.callTimeout, cbConfig.resetTimeout)(system.dispatcher)
  }

  cb.onClose{
    cbMetrics.status= CircuitBreakerStatus.Closed
  }

  cb.onOpen{
    cbMetrics.status = CircuitBreakerStatus.Open
  }

  cb.onHalfOpen{
    cbMetrics.status = CircuitBreakerStatus.HalfOpen
  }

  def get[R: FromResponseUnmarshaller](uri: String, reqSettings: RequestSettings = Configuration.defaultRequestSettings)
                                      (implicit timeout: Timeout = 1 second): Future[R] = {
    val fHttpResponse = fActorRef flatMap { ref => (ref ? HttpClientActorMessage.Get(uri, reqSettings)).mapTo[HttpResponse]}
    fHttpResponse flatMap { response =>
      try {
        Future.fromTry(response.unmarshalTo[R])
      } catch {
        case t: Throwable => Future.failed(t)
      }
    }
  }

  def options[R: FromResponseUnmarshaller](uri: String, reqSettings: RequestSettings = Configuration.defaultRequestSettings)
                                          (implicit timeout: Timeout = 1 second): Future[R] = {
    val fHttpResponse = fActorRef flatMap { ref => (ref ? HttpClientActorMessage.Options(uri, reqSettings)).mapTo[HttpResponse]}
    fHttpResponse flatMap { response =>
      try {
        Future.fromTry(response.unmarshalTo[R])
      } catch {
        case t: Throwable => Future.failed(t)
      }
    }
  }

  def delete[R: FromResponseUnmarshaller](uri: String, reqSettings: RequestSettings = Configuration.defaultRequestSettings)
                                         (implicit timeout: Timeout = 1 second): Future[R] = {
    val fHttpResponse = fActorRef flatMap { ref => (ref ? HttpClientActorMessage.Delete(uri, reqSettings)).mapTo[HttpResponse]}
    fHttpResponse flatMap { response =>
      try {
        Future.fromTry(response.unmarshalTo[R])
      } catch {
        case t: Throwable => Future.failed(t)
      }
    }
  }

  def post[T: Marshaller, R: FromResponseUnmarshaller](uri: String, content: Option[T], reqSettings: RequestSettings = Configuration.defaultRequestSettings)
                                                      (implicit timeout: Timeout = 1 second, marshaller: Marshaller[T]): Future[R] = {
    val fHttpResponse = fActorRef flatMap { ref => (ref ? HttpClientActorMessage.Post(uri, content, marshaller, reqSettings)).mapTo[HttpResponse]}
    fHttpResponse flatMap { response =>
      try {
        Future.fromTry(response.unmarshalTo[R])
      } catch {
        case t: Throwable => Future.failed(t)
      }
    }
  }

  def put[T: Marshaller, R: FromResponseUnmarshaller](uri: String, content: Option[T], reqSettings: RequestSettings = Configuration.defaultRequestSettings)
                                                     (implicit timeout: Timeout = 1 second, marshaller: Marshaller[T]): Future[R] = {
    val fHttpResponse = fActorRef flatMap { ref => (ref ? HttpClientActorMessage.Put(uri, content, marshaller, reqSettings)).mapTo[HttpResponse]}
    fHttpResponse flatMap { response =>
      try {
        Future.fromTry(response.unmarshalTo[R])
      } catch {
        case t: Throwable => Future.failed(t)
      }
    }
  }

  def raw = new RawHttpClient

  class RawHttpClient {
    def get(uri: String, reqSettings: RequestSettings = Configuration.defaultRequestSettings)
           (implicit timeout: Timeout = 1 second): Future[HttpResponse] = {
      fActorRef flatMap { ref => (ref ? HttpClientActorMessage.Get(uri, reqSettings)).mapTo[HttpResponse]}
    }

    def post[T: Marshaller](uri: String, content: Option[T], reqSettings: RequestSettings = Configuration.defaultRequestSettings)
                           (implicit timeout: Timeout = 1 second, marshaller: Marshaller[T]): Future[HttpResponse] = {
      fActorRef flatMap { ref => (ref ? HttpClientActorMessage.Post[T](uri, content, marshaller, reqSettings)).mapTo[HttpResponse]}
    }

    def put[T: Marshaller](uri: String, content: Option[T], reqSettings: RequestSettings = Configuration.defaultRequestSettings)
                          (implicit timeout: Timeout = 1 second, marshaller: Marshaller[T]): Future[HttpResponse] = {
      fActorRef flatMap { ref => (ref ? HttpClientActorMessage.Put[T](uri, content, marshaller, reqSettings)).mapTo[HttpResponse]}
    }

    def head(uri: String, reqSettings: RequestSettings = Configuration.defaultRequestSettings)
            (implicit timeout: Timeout = 1 second): Future[HttpResponse] = {
      fActorRef flatMap { ref => (ref ? HttpClientActorMessage.Head(uri, reqSettings)).mapTo[HttpResponse]}
    }

    def delete(uri: String, reqSettings: RequestSettings = Configuration.defaultRequestSettings)
              (implicit timeout: Timeout = 1 second): Future[HttpResponse] = {
      fActorRef flatMap { ref => (ref ? HttpClientActorMessage.Delete(uri, reqSettings)).mapTo[HttpResponse]}
    }

    def options(uri: String, reqSettings: RequestSettings = Configuration.defaultRequestSettings)
               (implicit timeout: Timeout = 1 second): Future[HttpResponse] = {
      fActorRef flatMap { ref => (ref ? HttpClientActorMessage.Options(uri, reqSettings)).mapTo[HttpResponse]}
    }
  }

  def withConfig(config: Configuration)(implicit timeout: Timeout = 1 second): HttpClient = {
    val hcActorRef = (HttpClientManager(system).httpClientManager ? HttpClientManagerMessage.Get(name, env)).mapTo[ActorRef]
    val newActorRef = hcActorRef flatMap { ref => (ref ? HttpClientActorMessage.UpdateConfig(config)).mapTo[ActorRef]}
    HttpClient(name, env, status, Some(config), newActorRef)
  }

  def withSettings(settings: Settings)(implicit timeout: Timeout = 1 second): HttpClient = {
    val hcActorRef = (HttpClientManager(system).httpClientManager ? HttpClientManagerMessage.Get(name, env)).mapTo[ActorRef]
    val newActorRef = hcActorRef flatMap { ref => (ref ? HttpClientActorMessage.UpdateSettings(settings)).mapTo[ActorRef]}
    HttpClient(name, env, status, Some(config.getOrElse(Configuration()).copy(settings = settings)), newActorRef)
  }

  def withPipeline(pipeline: Option[SimplePipelineConfig])(implicit timeout: Timeout = 1 second): HttpClient = {
    val hcActorRef = (HttpClientManager(system).httpClientManager ? HttpClientManagerMessage.Get(name, env)).mapTo[ActorRef]
    val newActorRef = hcActorRef flatMap { ref => (ref ? HttpClientActorMessage.UpdatePipeline(pipeline)).mapTo[ActorRef]}
    HttpClient(name, env, status, Some(config.getOrElse(Configuration()).copy(pipeline = pipeline)), newActorRef)
  }

  def markDown(implicit timeout: Timeout = 1 second): Future[MarkDownSuccess.type] = {
    val hcActorRef = (HttpClientManager(system).httpClientManager ? HttpClientManagerMessage.Get(name, env)).mapTo[ActorRef]
    hcActorRef flatMap { ref => (ref ? HttpClientActorMessage.MarkDown).mapTo[MarkDownSuccess.type]}
  }

  def markUp(implicit timeout: Timeout = 1 second): Future[MarkUpSuccess.type] = {
    val hcActorRef = (HttpClientManager(system).httpClientManager ? HttpClientManagerMessage.Get(name, env)).mapTo[ActorRef]
    hcActorRef flatMap { ref => (ref ? HttpClientActorMessage.MarkUp).mapTo[MarkUpSuccess.type]}
  }

  def readyFuture: Future[Unit] = {
    fActorRef map {_ => }
  }
}

object HttpClientFactory {

  def get(name: String, env: Environment = Default)(implicit system: ActorSystem, timeout: Timeout = 1 second): HttpClient = {
    HttpClientManager(system).httpClientMap.get((name, env)) match {
      case Some(httpClient) => httpClient
      case None =>
        val hcActorRef = (HttpClientManager(system).httpClientManager ? HttpClientManagerMessage.Get(name, env)).mapTo[ActorRef]
        val httpClient = HttpClient(name, env, Status.UP, None, hcActorRef)
        HttpClientManager(system).httpClientMap.put((name, env), httpClient)
        httpClient
    }
  }
}