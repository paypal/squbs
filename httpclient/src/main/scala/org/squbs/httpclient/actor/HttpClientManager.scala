package org.squbs.httpclient.actor

import akka.actor._
import spray.http._
import spray.can.Http
import akka.io.IO
import org.squbs.httpclient.pipeline.{Pipeline, PipelineManager}
import org.squbs.httpclient._
import akka.pattern._
import akka.util.Timeout
import spray.httpx.unmarshalling._
import spray.httpx.{PipelineException, UnsuccessfulResponseException}
import spray.httpx.marshalling.Marshaller
import scala.collection.concurrent.TrieMap
import scala.concurrent._
import scala.util.Try
import spray.httpx.RequestBuilding.Options
import spray.httpx.RequestBuilding.Put
import spray.httpx.RequestBuilding.Post
import spray.httpx.RequestBuilding.Delete
import spray.httpx.RequestBuilding.Head
import spray.httpx.RequestBuilding.Get
import org.squbs.httpclient.actor.HttpClientActorMessage._
import org.squbs.httpclient.actor.HttpClientManagerMessage._
import org.squbs.httpclient.actor.HttpClientActorMessage.Update
import org.squbs.httpclient.actor.HttpClientManagerMessage.DeleteHttpClient
import scala.util.Failure
import scala.Some
import org.squbs.httpclient.HttpClientMarkDownException
import spray.http.HttpResponse
import org.squbs.httpclient.HttpResponseEntityWrapper
import scala.util.Success
import org.squbs.httpclient.HttpClientNotExistException
import org.squbs.httpclient.HttpResponseWrapper
import spray.http.HttpRequest
import org.squbs.httpclient.config.ServiceConfiguration
import org.squbs.httpclient.actor.HttpClientManagerMessage.CreateHttpClient
import org.squbs.httpclient.actor.HttpClientManagerMessage.GetHttpClient
import org.squbs.httpclient.config.Configuration
import org.squbs.httpclient.HttpClientExistException
import org.squbs.httpclient.config.HostConfiguration

/**
 * Created by hakuang on 6/23/2014.
 */

class HttpClientManagerExtension(system: ExtendedActorSystem) extends Extension {

  val httpClientManager = system.actorOf(Props[HttpClientManager], "httpClientManager")
}

object HttpClientManager extends ExtensionId[HttpClientManagerExtension] with ExtensionIdProvider {

  val httpClientMap: TrieMap[(String, Option[String]), (Client, ActorRef)] = TrieMap[(String, Option[String]), (Client, ActorRef)]()

  override def createExtension(system: ExtendedActorSystem): HttpClientManagerExtension = new HttpClientManagerExtension(system)

  override def lookup(): ExtensionId[_ <: Extension] = HttpClientManager

  def unmarshalWithWrapper[T: FromResponseUnmarshaller](response: HttpResponse): HttpResponseEntityWrapper[T] = {
    if (response.status.isSuccess)
      response.as[T] match {
        case Right(value) ⇒ HttpResponseEntityWrapper[T](response.status, Right(value), Some(response))
        case Left(error) ⇒ HttpResponseEntityWrapper[T](response.status, Left(throw new PipelineException(error.toString)), Some(response))
      }
    else HttpResponseEntityWrapper[T](response.status, Left(new UnsuccessfulResponseException(response)), Some(response))
  }

  def unmarshal[T: FromResponseUnmarshaller](response: HttpResponse): Either[Throwable, T] = {
    if (response.status.isSuccess)
      response.as[T] match {
        case Right(value) ⇒ Right(value)
        case Left(error) ⇒ Left(throw new PipelineException(error.toString))
      }
    else Left(new UnsuccessfulResponseException(response))
  }
}

/**
 * Without setup HttpConnection
 */
trait HttpCallActorSupport extends RetrySupport {

  import ExecutionContext.Implicits.global

  def handle(client: Client,
             pipeline: Try[HttpRequest => Future[HttpResponseWrapper]],
             httpRequest: HttpRequest): Future[HttpResponseWrapper] = {
    val config = client.config
    val maxRetryCount = config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration())).svcConfig.maxRetryCount
    val serviceTimeout = config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration())).svcConfig.serviceTimeout
    pipeline match {
      case Success(res) => retry(res, httpRequest, maxRetryCount, serviceTimeout)
      case Failure(t@HttpClientMarkDownException(_, _)) => future {
        HttpResponseWrapper(HttpClientException.httpClientMarkDownError, Left(t))
      }
      case Failure(t) => future {
        HttpResponseWrapper(999, Left(t))
      }
    }
  }

  def get(client: Client, actorRef: ActorRef, uri: String)
         (implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(client, PipelineManager.invokeToHttpResponseWithoutSetup(client, actorRef), Get(client.endpoint + uri))
  }

  def post[T: Marshaller](client: Client, actorRef: ActorRef, uri: String, content: Some[T])
                         (implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(client, PipelineManager.invokeToHttpResponseWithoutSetup(client, actorRef), Post(client.endpoint + uri, content))
  }

  def put[T: Marshaller](client: Client, actorRef: ActorRef, uri: String, content: Some[T])
                        (implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(client, PipelineManager.invokeToHttpResponseWithoutSetup(client, actorRef), Put(client.endpoint + uri, content))
  }

  def head(client: Client, actorRef: ActorRef, uri: String)
          (implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(client, PipelineManager.invokeToHttpResponseWithoutSetup(client, actorRef), Head(client.endpoint + uri))
  }

  def delete(client: Client, actorRef: ActorRef, uri: String)
            (implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(client, PipelineManager.invokeToHttpResponseWithoutSetup(client, actorRef), Delete(client.endpoint + uri))
  }

  def options(client: Client, actorRef: ActorRef, uri: String)
             (implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(client, PipelineManager.invokeToHttpResponseWithoutSetup(client, actorRef), Options(client.endpoint + uri))
  }
}

class HttpClientCallerActor(client: Client) extends Actor with HttpCallActorSupport with ActorLogging {

  implicit val system = context.system

  import scala.concurrent.ExecutionContext.Implicits.global

  override def receive: Actor.Receive = {
    case HttpClientActorMessage.Get(uri) =>
      IO(Http) ! PipelineManager.hostConnectorSetup(client)
      context.become(receiveGetConnection(HttpMethods.GET, uri, client, sender()))
    case HttpClientActorMessage.Delete(uri) =>
      IO(Http) ! PipelineManager.hostConnectorSetup(client)
      context.become(receiveGetConnection(HttpMethods.GET, uri, client, sender()))
    case HttpClientActorMessage.Head(uri) =>
      IO(Http) ! PipelineManager.hostConnectorSetup(client)
      context.become(receiveGetConnection(HttpMethods.GET, uri, client, sender()))
    case HttpClientActorMessage.Options(uri) =>
      IO(Http) ! PipelineManager.hostConnectorSetup(client)
      context.become(receiveGetConnection(HttpMethods.GET, uri, client, sender()))
    case HttpClientActorMessage.Put(uri, content) =>
      IO(Http) ! PipelineManager.hostConnectorSetup(client)
      context.become(receivePostConnection(HttpMethods.GET, uri, content, client, sender()))
    case HttpClientActorMessage.Post(uri, content) =>
      IO(Http) ! PipelineManager.hostConnectorSetup(client)
      context.become(receivePostConnection(HttpMethods.GET, uri, content, client, sender()))

  }

  def receiveGetConnection(httpMethod: HttpMethod, uri: String, client: Client, actorRef: ActorRef): Actor.Receive = {
    case Http.HostConnectorInfo(connector, _) =>
      implicit val timeout: Timeout = client.config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration())).svcConfig.connectionTimeout.toMillis
      httpMethod match {
        case HttpMethods.GET =>
          get(client, connector, uri).pipeTo(actorRef)
        case HttpMethods.DELETE =>
          delete(client, connector, uri).pipeTo(actorRef)
        case HttpMethods.HEAD =>
          head(client, connector, uri).pipeTo(actorRef)
        case HttpMethods.OPTIONS =>
          options(client, connector, uri).pipeTo(actorRef)
      }
  }

  implicit val TMarshaller = tMarshaller(ContentTypes.`application/octet-stream`)

  def tMarshaller(contentType: ContentType): Marshaller[Any] =
    Marshaller.of[Any](contentType) {
      (value, _, ctx) ⇒
      // we marshal to the ContentType given as argument to the method, not the one established by content-negotiation,
      // since the former is the one belonging to the byte array
        ctx.marshalTo(HttpEntity(contentType, value.asInstanceOf[String]))
    }

  def receivePostConnection[T](httpMethod: HttpMethod, uri: String, content: Some[T], client: Client, actorRef: ActorRef): Actor.Receive = {
    case Http.HostConnectorInfo(connector, _) =>
      implicit val timeout: Timeout = client.config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration())).svcConfig.connectionTimeout.toMillis
      httpMethod match {
        case HttpMethods.POST =>
          post[T](client, connector, uri, content).pipeTo(actorRef)
          context.stop(self)
        case HttpMethods.PUT =>
          put[T](client, connector, uri, content).pipeTo(actorRef)
          context.stop(self)
      }
  }
}

class HttpClientActor(client: Client) extends Actor with HttpCallActorSupport with ActorLogging {

  private[HttpClientActor] val httpClientCallerActor = context.actorOf(Props(new HttpClientCallerActor(client)))

  override def receive: Actor.Receive = {
    case msg @ HttpClientActorMessage.Get(uri) =>
      httpClientCallerActor.forward(msg)
    case msg @ HttpClientActorMessage.Delete(uri) =>
      httpClientCallerActor.forward(msg)
    case msg @ HttpClientActorMessage.Head(uri) =>
      httpClientCallerActor.forward(msg)
    case msg @ HttpClientActorMessage.Options(uri) =>
      httpClientCallerActor.forward(msg)
    case msg @ HttpClientActorMessage.Put(uri, content) =>
      httpClientCallerActor.forward(msg)
    case msg @ HttpClientActorMessage.Post(uri, content) =>
      httpClientCallerActor.forward(msg)

    case MarkDown =>
      client.markDown
      HttpClientManager.httpClientMap.put((client.name, client.env), (client, self))
      sender ! MarkDownHttpClientSuccess
    case MarkUp =>
      client.markUP
      HttpClientManager.httpClientMap.put((client.name, client.env), (client, self))
      sender ! MarkUpHttpClientSuccess
    case Update(conf, p) =>
      val updatedClient = new Client {
        override val env: Option[String] = client.env
        override val pipeline: Option[Pipeline] = p
        override val name: String = client.name
        override val config: Option[Configuration] = conf
      }
      HttpClientManager.httpClientMap.put((client.name, client.env), (updatedClient, self))
      sender ! UpdateHttpClientSuccess
    case Close =>
      context.stop(httpClientCallerActor)
      context.stop(self)
      HttpClientManager.httpClientMap.remove((client.name, client.env))
      sender ! CloseHttpClientSuccess
  }
}

class HttpClientManager extends Actor {

  import HttpClientManager.httpClientMap
  override def receive: Receive = {
    case msg @ CreateHttpClient(name, env, config, pipeline) =>
      httpClientMap.get((name, env)) match {
        case Some(value) =>
          sender ! HttpClientExistException(name, env)
        case None     =>
          val httpClientActor = context.actorOf(Props(new HttpClientActor(msg)))
          httpClientMap.put((name, env), (msg, httpClientActor))
          sender ! httpClientActor
      }
    case msg @ DeleteHttpClient(name, env) =>
      httpClientMap.get((name, env)) match {
        case Some(value) =>
          httpClientMap.remove((name, env))
          sender ! DeleteHttpClientSuccess
        case None     =>
          sender ! HttpClientNotExistException(name, env)
      }
    case msg @ DeleteAllHttpClient =>
      httpClientMap.clear
      sender ! DeleteAllHttpClientSuccess
    case msg @ GetHttpClient(name, env) =>
      httpClientMap.get((name, env)) match {
        case Some(value) =>
          sender ! value._2
        case None     =>
          sender ! HttpClientNotExistException(name, env)
      }
    case msg @ GetAllHttpClient =>
      sender ! httpClientMap
  }

}