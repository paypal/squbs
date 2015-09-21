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
import akka.io.IO
import akka.pattern._
import akka.util.Timeout
import org.squbs.httpclient.HttpClientActorMessage.HttpClientMessage
import org.squbs.httpclient.env.Environment
import org.squbs.httpclient.pipeline.PipelineManager
import spray.can.Http
import spray.http.{HttpRequest, HttpResponse, _}

import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap
import scala.concurrent._
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.{Failure, Success, Try}

class HttpClientManagerExtension(system: ExtendedActorSystem) extends Extension {

  val httpClientManager = system.actorOf(Props[HttpClientManager], "httpClientManager")

  val httpClientMap: TrieMap[(String, Environment), HttpClient] = TrieMap[(String, Environment), HttpClient]()
}

object HttpClientManager extends ExtensionId[HttpClientManagerExtension] with ExtensionIdProvider {

  override def createExtension(system: ExtendedActorSystem): HttpClientManagerExtension =
    new HttpClientManagerExtension(system)

  override def lookup(): ExtensionId[_ <: Extension] = HttpClientManager

  implicit class RichPath(val path: Uri.Path) extends AnyVal {

    import Uri.Path._

    @tailrec
    private def last(path: Uri.Path): SlashOrEmpty = {
      path match {
        case s@Slash(Empty) => s
        case Slash(p) => last(p)
        case Segment(_, p) => last(p)
        case Empty => Empty
      }
    }

    def endsWithSlash: Boolean = last(path).isInstanceOf[Slash]
  }

}


/**
 * Without setup HttpConnection
 */
trait HttpCallActorSupport extends PipelineManager with CircuitBreakerSupport {

  def handle(client: HttpClient,
             pipeline: Try[HttpRequest => Future[HttpResponse]],
             httpRequest: HttpRequest)(implicit system: ActorSystem): Future[HttpResponse] = {
    implicit val ec = system.dispatcher
    httpClientLogger.debug("HttpRequest headers: " +
      httpRequest.headers.map { h => h.name + '=' + h.value}.mkString(", "))
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

  def call(client: HttpClient,
           actorRef: ActorRef,
           path: String,
           reqSettings: RequestSettings,
           requestBuilder: Uri => HttpRequest)
          (implicit system: ActorSystem): Future[HttpResponse] = {
    val uri = makeFullUri(client, path)
    httpClientLogger.debug("Service call url is:" + (client.endpoint + uri))
    handle(client, invokeToHttpResponseWithoutSetup(client, reqSettings, actorRef), requestBuilder(uri))
  }

  private def makeFullUri(client: HttpClient, path: String): Uri = {
    import HttpClientManager._
    val basePath = client.endpoint.uri.path
    val fullPath = (basePath.endsWithSlash, path.charAt(0) == '/') match {
      case (false, false) => basePath + ('/' + path)
      case (true, false) => basePath + path
      case (false, true) => basePath + path
      case (true, true) => basePath + path.substring(1)
    }
    Uri(path = fullPath)
  }
}

class HttpClientActor(svcName: String, env: Environment) extends Actor with HttpCallActorSupport with ActorLogging {

  import HttpClientActorMessage._
  implicit val ec = context.dispatcher

  implicit val system = context.system

  val clientMap = HttpClientManager(system).httpClientMap

  def client = clientMap(svcName, env)

  implicit def toTimeout(d: Duration): Timeout = Timeout(d match {
    case f: FiniteDuration => f
    case Duration.Inf => Duration.fromNanos(Long.MaxValue)
    case _ => Duration.Zero
  })

  override def receive: Actor.Receive = {
    case msg: HttpClientMessage =>
      val currentClient = client
      implicit val timeout: Timeout =
        currentClient.endpoint.config.settings.hostSettings.connectionSettings.connectingTimeout
      (IO(Http) ? hostConnectorSetup(currentClient, msg.requestSettings)).flatMap {
        case Http.HostConnectorInfo(connector, _) => call(currentClient, connector, msg.uri, msg.requestSettings, msg.requestBuilder)
      }
        .pipeTo(sender())

    case MarkDown =>
      clientMap.put((client.name, client.env),
        client.copy(status = Status.DOWN)(actorCreator = (_) => Future.successful(self))
      )
      sender ! MarkDownSuccess
    case MarkUp =>
      clientMap.put((client.name, client.env),
        client.copy(status = Status.UP)(actorCreator = (_) => Future.successful(self))
      )
      sender ! MarkUpSuccess
    case UpdateConfig(conf) =>
      clientMap.put((client.name, client.env),
        client.copy(config = Some(conf))(actorCreator = (_) => Future.successful(self))
      )
      sender ! self
    case UpdateSettings(settings) =>
      val conf = client.config.getOrElse(Configuration()).copy(settings = settings)
      clientMap.put((client.name, client.env),
        client.copy(config = Some(conf))(actorCreator = (_) => Future.successful(self))
      )
      sender ! self
    case UpdatePipeline(pipeline) =>
      val conf = client.config.getOrElse(Configuration()).copy(pipeline = pipeline)
      clientMap.put((client.name, client.env),
        client.copy(config = Some(conf))(actorCreator = (_) => Future.successful(self))
      )
      sender ! self
    case Close =>
      context.stop(self)
      clientMap.remove((client.name, client.env))
      sender ! CloseSuccess
  }
}

class HttpClientManager extends Actor {

  import org.squbs.httpclient.HttpClientManagerMessage._

  implicit val ec = context.dispatcher

  implicit val system = context.system

  override def receive: Receive = {
    case client@Get(name, env) =>
      HttpClientManager(system).httpClientMap.get((name, env)) match {
        case Some(httpClient) =>
          httpClient.fActorRef pipeTo sender()
        case None =>
          val httpClient = HttpClient(name, env)()
          HttpClientManager(system).httpClientMap.put((name, env), httpClient)
          httpClient.fActorRef pipeTo sender()
      }
    case Delete(name, env) =>
      HttpClientManager(system).httpClientMap.get((name, env)) match {
        case Some(_) =>
          HttpClientManager(system).httpClientMap.remove((name, env))
          sender ! DeleteSuccess
        case None =>
          sender ! HttpClientNotExistException(name, env)
      }
    case DeleteAll =>
      HttpClientManager(system).httpClientMap.clear()
      sender ! DeleteAllSuccess
    case GetAll =>
      sender ! HttpClientManager(system).httpClientMap
  }

}