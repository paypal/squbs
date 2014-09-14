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

import akka.actor._
import spray.http._
import spray.can.Http
import akka.io.IO
import akka.pattern._
import akka.util.Timeout
import spray.httpx.marshalling.Marshaller
import scala.collection.concurrent.TrieMap
import scala.concurrent._
import scala.util.Try
import scala.util.Failure
import scala.Some
import spray.http.HttpResponse
import scala.util.Success
import spray.http.HttpRequest
import org.squbs.httpclient.env.Environment
import org.squbs.httpclient.pipeline.PipelineManager
import org.squbs.httpclient.endpoint.Endpoint

class HttpClientManagerExtension(system: ExtendedActorSystem) extends Extension {

  val httpClientManager = system.actorOf(Props[HttpClientManager], "httpClientManager")
}

object HttpClientManager extends ExtensionId[HttpClientManagerExtension] with ExtensionIdProvider {

  val httpClientMap: TrieMap[(String, Environment), (Client, ActorRef)] = TrieMap[(String, Environment), (Client, ActorRef)]()

  override def createExtension(system: ExtendedActorSystem): HttpClientManagerExtension = new HttpClientManagerExtension(system)

  override def lookup(): ExtensionId[_ <: Extension] = HttpClientManager
}


/**
 * Without setup HttpConnection
 */
trait HttpCallActorSupport extends PipelineManager with CircuitBreakerSupport {

  import spray.httpx.RequestBuilding._

  def handle(client: Client,
             pipeline: Try[HttpRequest => Future[HttpResponse]],
             httpRequest: HttpRequest)(implicit system: ActorSystem): Future[HttpResponse] = {
    implicit val ec = system.dispatcher
    pipeline match {
      case Success(res) =>
        withCircuitBreaker(client, res(httpRequest))
      case Failure(t@HttpClientMarkDownException(_, _)) =>
        httpClientLogger.debug("HttpClient has been mark down!", t)
        collectCbMetrics(client, ServiceCallStatus.Exception)
        future {throw t}
      case Failure(t) =>
        httpClientLogger.debug("HttpClient Pipeline execution failure!", t)
        collectCbMetrics(client, ServiceCallStatus.Exception)
        future {throw t}
    }
  }

  def get(client: Client, actorRef: ActorRef, uri: String)
         (implicit system: ActorSystem): Future[HttpResponse] = {
    httpClientLogger.debug("Service call url is:" + (client.endpoint + uri))
    handle(client, invokeToHttpResponseWithoutSetup(client, actorRef), Get(uri))
  }

  def post[T: Marshaller](client: Client, actorRef: ActorRef, uri: String, content: T)
                         (implicit system: ActorSystem): Future[HttpResponse] = {
    httpClientLogger.debug("Service call url is:" + (client.endpoint + uri))
    handle(client, invokeToHttpResponseWithoutSetup(client, actorRef), Post(uri, content))
  }

  def put[T: Marshaller](client: Client, actorRef: ActorRef, uri: String, content: T)
                        (implicit system: ActorSystem): Future[HttpResponse] = {
    httpClientLogger.debug("Service call url is:" + (client.endpoint + uri))
    handle(client, invokeToHttpResponseWithoutSetup(client, actorRef), Put(uri, content))
  }

  def head(client: Client, actorRef: ActorRef, uri: String)
          (implicit system: ActorSystem): Future[HttpResponse] = {
    httpClientLogger.debug("Service call url is:" + (client.endpoint + uri))
    handle(client, invokeToHttpResponseWithoutSetup(client, actorRef), Head(uri))
  }

  def delete(client: Client, actorRef: ActorRef, uri: String)
            (implicit system: ActorSystem): Future[HttpResponse] = {
    httpClientLogger.debug("Service call url is:" + (client.endpoint + uri))
    handle(client, invokeToHttpResponseWithoutSetup(client, actorRef), Delete(uri))
  }

  def options(client: Client, actorRef: ActorRef, uri: String)
             (implicit system: ActorSystem): Future[HttpResponse] = {
    httpClientLogger.debug("Service call url is:" + (client.endpoint + uri))
    handle(client, invokeToHttpResponseWithoutSetup(client, actorRef), Options(uri))
  }
}

class HttpClientCallerActor(client: Client) extends Actor with HttpCallActorSupport with ActorLogging {

  implicit val system = context.system

  implicit val ec = system.dispatcher

  override def receive: Actor.Receive = {
    case HttpClientActorMessage.Get(uri) =>
      IO(Http) ! hostConnectorSetup(client)
      context.become(receiveGetConnection(HttpMethods.GET, uri, client, sender()))
    case HttpClientActorMessage.Delete(uri) =>
      IO(Http) ! hostConnectorSetup(client)
      context.become(receiveGetConnection(HttpMethods.DELETE, uri, client, sender()))
    case HttpClientActorMessage.Head(uri) =>
      IO(Http) ! hostConnectorSetup(client)
      context.become(receiveGetConnection(HttpMethods.HEAD, uri, client, sender()))
    case HttpClientActorMessage.Options(uri) =>
      IO(Http) ! hostConnectorSetup(client)
      context.become(receiveGetConnection(HttpMethods.OPTIONS, uri, client, sender()))
    case HttpClientActorMessage.Put(uri, content, marshaller) =>
      IO(Http) ! hostConnectorSetup(client)
      context.become(receivePostConnection(HttpMethods.PUT, uri, content, client, sender(), marshaller))
    case HttpClientActorMessage.Post(uri, content, marshaller) =>
      IO(Http) ! hostConnectorSetup(client)
      context.become(receivePostConnection(HttpMethods.POST, uri, content, client, sender(), marshaller))
  }

  def receiveGetConnection(httpMethod: HttpMethod, uri: String, client: Client, actorRef: ActorRef): Actor.Receive = {
    case Http.HostConnectorInfo(connector, _) =>
      implicit val timeout: Timeout = client.endpoint.config.hostSettings.connectionSettings.connectingTimeout.toMillis
      httpMethod match {
        case HttpMethods.GET =>
          get(client, connector, uri).pipeTo(actorRef)
          context.unbecome
        case HttpMethods.DELETE =>
          delete(client, connector, uri).pipeTo(actorRef)
          context.unbecome
        case HttpMethods.HEAD =>
          head(client, connector, uri).pipeTo(actorRef)
          context.unbecome
        case HttpMethods.OPTIONS =>
          options(client, connector, uri).pipeTo(actorRef)
          context.unbecome
      }
  }

  def receivePostConnection[T <: AnyRef](httpMethod: HttpMethod, 
                                         uri: String, 
                                         content: T,
                                         client: Client, 
                                         actorRef: ActorRef,
                                         marshaller: Marshaller[T]): Actor.Receive = {
    case Http.HostConnectorInfo(connector, _) =>
      implicit val marshallerSupport = marshaller
      implicit val timeout: Timeout = client.endpoint.config.hostSettings.connectionSettings.connectingTimeout.toMillis
      httpMethod match {
        case HttpMethods.POST =>
          post[T](client, connector, uri, content).pipeTo(actorRef)
          context.unbecome
        case HttpMethods.PUT =>
          put[T](client, connector, uri, content).pipeTo(actorRef)
          context.unbecome
      }
  }
}

class HttpClientActor(client: Client) extends Actor with HttpCallActorSupport with ActorLogging {

  import org.squbs.httpclient.HttpClientActorMessage._

  private[HttpClientActor] val httpClientCallerActor = context.actorOf(Props(new HttpClientCallerActor(client)))

  override def receive: Actor.Receive = {
    case msg: HttpClientActorMessage.Get =>
      httpClientCallerActor.forward(msg)
    case msg: HttpClientActorMessage.Delete =>
      httpClientCallerActor.forward(msg)
    case msg: HttpClientActorMessage.Head =>
      httpClientCallerActor.forward(msg)
    case msg: HttpClientActorMessage.Options =>
      httpClientCallerActor.forward(msg)
    case msg@ HttpClientActorMessage.Put(uri, content, json4sSupport) =>
      httpClientCallerActor.forward(msg)
    case msg@ HttpClientActorMessage.Post(uri, content, json4sSupport) =>
      httpClientCallerActor.forward(msg)
    case MarkDown =>
      client.markDown
      HttpClientManager.httpClientMap.put((client.name, client.env), (client, self))
      sender ! MarkDownSuccess
    case MarkUp =>
      client.markUp
      HttpClientManager.httpClientMap.put((client.name, client.env), (client, self))
      sender ! MarkUpSuccess
    case Update(conf) =>
      HttpClientManager.httpClientMap.get(client.name, client.env) match {
        case Some(_) =>
          client.endpoint = Endpoint(client.endpoint.uri, conf)
          HttpClientManager.httpClientMap.put((client.name, client.env), (client, self))
          sender ! self
        case None    =>
          sender ! HttpClientNotExistException(client.name, client.env)
      }
    case Close =>
      context.stop(httpClientCallerActor)
      context.stop(self)
      HttpClientManager.httpClientMap.remove((client.name, client.env))
      sender ! CloseSuccess
  }
}

class HttpClientManager extends Actor {

  import org.squbs.httpclient.HttpClientManagerMessage._

  import HttpClientManager.httpClientMap
  override def receive: Receive = {
    case client @ Get(name, env) =>
      httpClientMap.get((name, env)) match {
        case Some((_, httpClientActor)) =>
          sender ! httpClientActor
        case None    =>
          val httpClientActor = context.actorOf(Props(new HttpClientActor(client)))
          httpClientMap.put((name, env), (client, httpClientActor))
          sender ! httpClientActor
      }
    case Delete(name, env) =>
      httpClientMap.get((name, env)) match {
        case Some(_) =>
          httpClientMap.remove((name, env))
          sender ! DeleteSuccess
        case None    =>
          sender ! HttpClientNotExistException(name, env)
      }
    case DeleteAll =>
      httpClientMap.clear
      sender ! DeleteAllSuccess
    case GetAll =>
      sender ! httpClientMap
  }

}