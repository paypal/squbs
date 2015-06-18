/*
 *  Copyright 2015 PayPal
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
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
import spray.http.HttpResponse
import scala.util.Success
import spray.http.HttpRequest
import org.squbs.httpclient.env.Environment
import org.squbs.httpclient.pipeline.PipelineManager

class HttpClientManagerExtension(system: ExtendedActorSystem) extends Extension {

  val httpClientManager = system.actorOf(Props[HttpClientManager], "httpClientManager")

  val httpClientMap: TrieMap[(String, Environment), HttpClient] = TrieMap[(String, Environment), HttpClient]()
}

object HttpClientManager extends ExtensionId[HttpClientManagerExtension] with ExtensionIdProvider {

  override def createExtension(system: ExtendedActorSystem): HttpClientManagerExtension = new HttpClientManagerExtension(system)

  override def lookup(): ExtensionId[_ <: Extension] = HttpClientManager
}


/**
 * Without setup HttpConnection
 */
trait HttpCallActorSupport extends PipelineManager with CircuitBreakerSupport {

  import spray.httpx.RequestBuilding._

  def handle(client: HttpClient,
             pipeline: Try[HttpRequest => Future[HttpResponse]],
             httpRequest: HttpRequest)(implicit system: ActorSystem): Future[HttpResponse] = {
    implicit val ec = system.dispatcher
    httpClientLogger.debug("HttpRequest headers:" + httpRequest.headers.foreach(header => println(s"${header.name}=${header.value},")))
    pipeline match {
      case Success(res) =>
        withCircuitBreaker(client, res(httpRequest))
      case Failure(t@HttpClientMarkDownException(_, _)) =>
        httpClientLogger.debug("HttpClient has been mark down!", t)
        collectCbMetrics(client, ServiceCallStatus.Exception)
        Future.failed(t)
      case Failure(t) =>
        httpClientLogger.debug("HttpClient Pipeline execution failure!", t)
        collectCbMetrics(client, ServiceCallStatus.Exception)
        Future.failed(t)
    }
  }

  def get(client: HttpClient, actorRef: ActorRef, uri: String,
          reqSettings: RequestSettings)
         (implicit system: ActorSystem): Future[HttpResponse] = {
    val verifiedUri = verifyUri(client, uri)
    httpClientLogger.debug("Service call url is:" + (client.endpoint + verifiedUri))
    handle(client, invokeToHttpResponseWithoutSetup(client, reqSettings, actorRef), Get(verifiedUri))
  }

  def post[T: Marshaller](client: HttpClient, actorRef: ActorRef, uri: String, content: Option[T],
                          reqSettings: RequestSettings)
                         (implicit system: ActorSystem): Future[HttpResponse] = {
    val verifiedUri = verifyUri(client, uri)
    httpClientLogger.debug("Service call url is:" + (client.endpoint + verifiedUri))
    handle(client, invokeToHttpResponseWithoutSetup(client, reqSettings, actorRef), Post(verifiedUri, content))
  }

  def put[T: Marshaller](client: HttpClient, actorRef: ActorRef, uri: String, content: Option[T],
                         reqSettings: RequestSettings)
                        (implicit system: ActorSystem): Future[HttpResponse] = {
    val verifiedUri = verifyUri(client, uri)
    httpClientLogger.debug("Service call url is:" + (client.endpoint + verifiedUri))
    handle(client, invokeToHttpResponseWithoutSetup(client, reqSettings, actorRef), Put(verifiedUri, content))
  }

  def head(client: HttpClient, actorRef: ActorRef, uri: String,
           reqSettings: RequestSettings)
          (implicit system: ActorSystem): Future[HttpResponse] = {
    val verifiedUri = verifyUri(client, uri)
    httpClientLogger.debug("Service call url is:" + (client.endpoint + verifiedUri))
    handle(client, invokeToHttpResponseWithoutSetup(client, reqSettings, actorRef), Head(verifiedUri))
  }

  def delete(client: HttpClient, actorRef: ActorRef, uri: String,
             reqSettings: RequestSettings)
            (implicit system: ActorSystem): Future[HttpResponse] = {
    val verifiedUri = verifyUri(client, uri)
    httpClientLogger.debug("Service call url is:" + (client.endpoint + verifiedUri))
    handle(client, invokeToHttpResponseWithoutSetup(client, reqSettings, actorRef), Delete(verifiedUri))
  }

  def options(client: HttpClient, actorRef: ActorRef, uri: String,
              reqSettings: RequestSettings)
             (implicit system: ActorSystem): Future[HttpResponse] = {
    val verifiedUri = verifyUri(client, uri)
    httpClientLogger.debug("Service call url is:" + (client.endpoint + verifiedUri))
    handle(client, invokeToHttpResponseWithoutSetup(client, reqSettings, actorRef), Options(verifiedUri))
  }

  private def verifyUri(client: HttpClient, uri: String) = {
    val baseUri = Uri(client.endpoint.uri)
    val basePath = baseUri.path.toString()
    var trimBasePath = basePath
    if (trimBasePath != "" && trimBasePath.charAt(0) == '/') {
      trimBasePath = trimBasePath.substring(1)
    }
    if (trimBasePath != "" && trimBasePath.charAt(trimBasePath.length - 1) == '/') {
      trimBasePath = trimBasePath.substring(0, trimBasePath.length - 1)
    }
    (trimBasePath, uri.charAt(0)) match {
      case ("", '/') => uri
      case (_, '/')  => '/' + trimBasePath + uri
      case ("", _)   => '/' + uri
      case (_, _)    => '/' + trimBasePath + '/' + uri
    }
  }

}

class HttpClientCallerActor(svcName: String, env: Environment) extends Actor with HttpCallActorSupport with ActorLogging {

  implicit val system = context.system

  implicit val ec = system.dispatcher

  def client = HttpClientManager(system).httpClientMap(svcName, env)

  override def receive: Actor.Receive = {
    case HttpClientActorMessage.Get(uri, reqSettings) =>
      IO(Http) ! hostConnectorSetup(client, reqSettings)
      context.become(receiveGetConnection(HttpMethods.GET, uri, reqSettings, client, sender()))
    case HttpClientActorMessage.Delete(uri, reqSettings) =>
      IO(Http) ! hostConnectorSetup(client, reqSettings)
      context.become(receiveGetConnection(HttpMethods.DELETE, uri, reqSettings, client, sender()))
    case HttpClientActorMessage.Head(uri, reqSettings) =>
      IO(Http) ! hostConnectorSetup(client, reqSettings)
      context.become(receiveGetConnection(HttpMethods.HEAD, uri, reqSettings, client, sender()))
    case HttpClientActorMessage.Options(uri, reqSettings) =>
      IO(Http) ! hostConnectorSetup(client, reqSettings)
      context.become(receiveGetConnection(HttpMethods.OPTIONS, uri, reqSettings, client, sender()))
    case HttpClientActorMessage.Put(uri, content, marshaller, reqSettings) =>
      IO(Http) ! hostConnectorSetup(client, reqSettings)
      context.become(receivePostConnection(HttpMethods.PUT, uri, content, reqSettings, client, sender(), marshaller))
    case HttpClientActorMessage.Post(uri, content, marshaller, reqSettings) =>
      IO(Http) ! hostConnectorSetup(client, reqSettings)
      context.become(receivePostConnection(HttpMethods.POST, uri, content, reqSettings, client, sender(), marshaller))
  }

  def receiveGetConnection(httpMethod: HttpMethod, uri: String, reqSettings: RequestSettings, httpClient: HttpClient, clientSender: ActorRef): Actor.Receive = {
    case Http.HostConnectorInfo(connector, _) =>
      implicit val timeout: Timeout = httpClient.endpoint.config.settings.hostSettings.connectionSettings.connectingTimeout.toMillis
      httpMethod match {
        case HttpMethods.GET =>
          get(httpClient, connector, uri, reqSettings).pipeTo(clientSender)
          context.unbecome()
        case HttpMethods.DELETE =>
          delete(httpClient, connector, uri, reqSettings).pipeTo(clientSender)
          context.unbecome()
        case HttpMethods.HEAD =>
          head(httpClient, connector, uri, reqSettings).pipeTo(clientSender)
          context.unbecome()
        case HttpMethods.OPTIONS =>
          options(httpClient, connector, uri, reqSettings).pipeTo(clientSender)
          context.unbecome()
      }
  }

  def receivePostConnection[T](httpMethod: HttpMethod,
                               uri: String,
                               content: Option[T],
                               reqSettings: RequestSettings,
                               client: HttpClient,
                               actorRef: ActorRef,
                               marshaller: Marshaller[T]): Actor.Receive = {
    case Http.HostConnectorInfo(connector, _) =>
      implicit val marshallerSupport = marshaller
      implicit val timeout: Timeout = client.endpoint.config.settings.hostSettings.connectionSettings.connectingTimeout.toMillis
      httpMethod match {
        case HttpMethods.POST =>
          post[T](client, connector, uri, content, reqSettings).pipeTo(actorRef)
          context.unbecome()
        case HttpMethods.PUT =>
          put[T](client, connector, uri, content, reqSettings).pipeTo(actorRef)
          context.unbecome()
      }
  }
}

class HttpClientActor(svcName: String, env: Environment) extends Actor with HttpCallActorSupport with ActorLogging {

  import org.squbs.httpclient.HttpClientActorMessage._

  private[HttpClientActor] val httpClientCallerActor = context.actorOf(Props(classOf[HttpClientCallerActor], svcName, env))

  implicit val ec = context.dispatcher

  implicit val system = context.system

  def client = HttpClientManager(system).httpClientMap(svcName, env)

  override def receive: Actor.Receive = {
    case msg: HttpClientActorMessage.Get =>
      httpClientCallerActor.forward(msg)
    case msg: HttpClientActorMessage.Delete =>
      httpClientCallerActor.forward(msg)
    case msg: HttpClientActorMessage.Head =>
      httpClientCallerActor.forward(msg)
    case msg: HttpClientActorMessage.Options =>
      httpClientCallerActor.forward(msg)
    case msg@ HttpClientActorMessage.Put(uri, content, reqSettings, json4sSupport) =>
      httpClientCallerActor.forward(msg)
    case msg@ HttpClientActorMessage.Post(uri, content, reqSettings, json4sSupport) =>
      httpClientCallerActor.forward(msg)
    case MarkDown =>
      HttpClientManager(system).httpClientMap.put((client.name, client.env),
        client.copy(status = Status.DOWN)(actorCreator = (_) => Future.successful(self))
      )
      sender ! MarkDownSuccess
    case MarkUp =>
      HttpClientManager(system).httpClientMap.put((client.name, client.env),
        client.copy(status = Status.UP)(actorCreator = (_) => Future.successful(self))
      )
      sender ! MarkUpSuccess
    case UpdateConfig(conf) =>
      HttpClientManager(system).httpClientMap.put((client.name, client.env),
        client.copy(config = Some(conf))(actorCreator = (_) => Future.successful(self))
      )
      sender ! self
    case UpdateSettings(settings) =>
      val conf = client.config.getOrElse(Configuration()).copy(settings = settings)
      HttpClientManager(system).httpClientMap.put((client.name, client.env),
        client.copy(config = Some(conf))(actorCreator = (_) => Future.successful(self))
      )
      sender ! self
    case UpdatePipeline(pipeline) =>
      val conf = client.config.getOrElse(Configuration()).copy(pipeline = pipeline)
      HttpClientManager(system).httpClientMap.put((client.name, client.env),
        client.copy(config = Some(conf))(actorCreator = (_) => Future.successful(self))
      )
      sender ! self
    case Close =>
      context.stop(httpClientCallerActor)
      context.stop(self)
      HttpClientManager(system).httpClientMap.remove((client.name, client.env))
      sender ! CloseSuccess
  }
}

class HttpClientManager extends Actor {

  import org.squbs.httpclient.HttpClientManagerMessage._

  implicit val ec = context.dispatcher

  implicit val system = context.system

  override def receive: Receive = {
    case client @ Get(name, env) =>
      HttpClientManager(system).httpClientMap.get((name, env)) match {
        case Some(httpClient) =>
          httpClient.fActorRef.pipeTo(sender())
        case None    =>
          val httpClient = HttpClient(name, env)()
          HttpClientManager(system).httpClientMap.put((name, env), httpClient)
          httpClient.fActorRef pipeTo sender
      }
    case Delete(name, env) =>
      HttpClientManager(system).httpClientMap.get((name, env)) match {
        case Some(_) =>
          HttpClientManager(system).httpClientMap.remove((name, env))
          sender ! DeleteSuccess
        case None    =>
          sender ! HttpClientNotExistException(name, env)
      }
    case DeleteAll =>
      HttpClientManager(system).httpClientMap.clear()
      sender ! DeleteAllSuccess
    case GetAll =>
      sender ! HttpClientManager(system).httpClientMap
  }

}