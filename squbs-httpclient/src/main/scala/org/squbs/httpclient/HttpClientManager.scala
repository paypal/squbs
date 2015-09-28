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
import org.squbs.httpclient.env.Environment
import org.squbs.httpclient.pipeline.PipelineManager
import spray.can.Http
import spray.http.{HttpRequest, HttpResponse, _}

import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap
import scala.concurrent._
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

class HttpClientManagerExtension(system: ExtendedActorSystem) extends Extension {

  val httpClientMap: TrieMap[(String, Environment), HttpClient] = TrieMap.empty[(String, Environment), HttpClient]

  val httpClientManager = system.actorOf(Props(classOf[HttpClientManager], httpClientMap), "httpClientManager")
}

object HttpClientManager extends ExtensionId[HttpClientManagerExtension] with ExtensionIdProvider {

  override def createExtension(system: ExtendedActorSystem): HttpClientManagerExtension =
    new HttpClientManagerExtension(system)

  override def lookup(): ExtensionId[_ <: Extension] = HttpClientManager

  def get(actorFactory: ActorRefFactory) = actorFactory match {
    case system: ActorSystem => super.apply(system)
    case context: ActorContext => super.apply(context.system)
    case other => throw new ClassCastException(other.getClass.toString + " is not an ActorSystem or an ActorContext")
  }

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
             httpRequest: HttpRequest)(implicit factory: ActorRefFactory): Future[HttpResponse] = {
    implicit val ec = factory.dispatcher
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
          (implicit actorFactory: ActorRefFactory, context : ActorContext): Future[HttpResponse] = {
    val uri = makeFullUri(client, path)
    httpClientLogger.debug("Service call url is:" + (client.endpoint + uri))
    handle(client, invokeToHttpResponseWithoutSetup(client, reqSettings, actorRef), requestBuilder(uri))
  }

  private def makeFullUri(client: HttpClient, path: String): Uri = {
    import HttpClientManager._
    val appendUri = Uri(path)
    val appendPath = appendUri.path
    val basePath = client.endpoint.uri.path
    val fullPath = (basePath.endsWithSlash, appendPath.startsWithSlash) match {
      case (false, false) => basePath ++ (Uri.Path / appendPath)
      case (true, false)  => basePath ++ appendPath
      case (false, true)  => basePath ++ appendPath
      case (true, true)   => basePath ++ appendPath.tail
    }
    appendUri withPath fullPath
  }
}

class HttpClientActor(svcName: String, env: Environment, clientMap: TrieMap[(String, Environment), HttpClient])
    extends Actor with HttpCallActorSupport with ActorLogging {

  import HttpClientActorMessage._
  implicit val ec = context.dispatcher

  def client = clientMap(svcName, env)

  implicit def toTimeout(d: Duration): Timeout = Timeout(d match {
    case f: FiniteDuration => f
    case Duration.Inf => Duration.fromNanos(Long.MaxValue)
    case _ => Duration.Zero
  })

  override def receive: Actor.Receive = {
    case msg: HttpClientMessage =>
      val currentClient = client
      implicit val system = context.system
      implicit val timeout: Timeout =
        currentClient.endpoint.config.settings.hostSettings.connectionSettings.connectingTimeout
      val requestSettings = msg.requestSettings getOrElse Configuration.defaultRequestSettings(system)
      (IO(Http) ? hostConnectorSetup(currentClient, requestSettings)).flatMap {
        case Http.HostConnectorInfo(connector, _) =>
          call(currentClient, connector, msg.uri, requestSettings, msg.requestBuilder)(system, context)
      } .pipeTo(sender()) // See whether we can even trim this further by not having to ask.
      // Is there always the same hostConnector for the same HttpClientActor?

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

class HttpClientManager(clientMap: TrieMap[(String, Environment), HttpClient]) extends Actor {

  import org.squbs.httpclient.HttpClientManagerMessage._

  implicit val ec = context.dispatcher

  implicit val system = context.system

  override def receive: Receive = {
    case Get(name, env) =>
      clientMap.get((name, env)) match {
        case Some(httpClient) =>
          httpClient.fActorRef pipeTo sender()
        case None =>
          val httpClient = HttpClient(name, env) { (arf) =>
            Future.successful(arf.actorOf(Props(classOf[HttpClientActor], name, env, clientMap)))
          }
          clientMap.put((name, env), httpClient)
          httpClient.fActorRef pipeTo sender()
      }
    case Delete(name, env) =>
      clientMap.get((name, env)) match {
        case Some(_) =>
          clientMap.remove((name, env))
          sender ! DeleteSuccess
        case None =>
          sender ! HttpClientNotExistException(name, env)
      }
    case DeleteAll =>
      clientMap.clear()
      sender ! DeleteAllSuccess
    case GetAll =>
      sender ! clientMap
  }
}