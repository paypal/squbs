package org.squbs.httpclient

import akka.actor._
import spray.http._
import spray.can.Http
import akka.io.IO
import akka.pattern._
import akka.util.{Timeout}
import spray.httpx.unmarshalling._
import spray.httpx.{BaseJson4sSupport, PipelineException, UnsuccessfulResponseException}
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

/**
 * Created by hakuang on 6/23/2014.
 */

class HttpClientManagerExtension(system: ExtendedActorSystem) extends Extension {

  val httpClientManager = system.actorOf(Props[HttpClientManager], "httpClientManager")
}

object HttpClientManager extends ExtensionId[HttpClientManagerExtension] with ExtensionIdProvider {

  val httpClientMap: TrieMap[(String, Environment), (Client, ActorRef)] = TrieMap[(String, Environment), (Client, ActorRef)]()

  override def createExtension(system: ExtendedActorSystem): HttpClientManagerExtension = new HttpClientManagerExtension(system)

  override def lookup(): ExtensionId[_ <: Extension] = HttpClientManager

//  def unmarshalWithWrapper[T: FromResponseUnmarshaller](response: HttpResponse): HttpResponseEntityWrapper[T] = {
//    if (response.status.isSuccess)
//      response.as[T] match {
//        case Right(value) ⇒ HttpResponseEntityWrapper[T](response.status, Right(value), Some(response))
//        case Left(error) ⇒ HttpResponseEntityWrapper[T](response.status, Left(throw new PipelineException(error.toString)), Some(response))
//      }
//    else HttpResponseEntityWrapper[T](response.status, Left(new UnsuccessfulResponseException(response)), Some(response))
//  }
//
//  def unmarshal[T: FromResponseUnmarshaller](response: HttpResponse): Either[Throwable, T] = {
//    if (response.status.isSuccess)
//      response.as[T] match {
//        case Right(value) ⇒ Right(value)
//        case Left(error) ⇒ Left(throw new PipelineException(error.toString))
//      }
//    else Left(new UnsuccessfulResponseException(response))
//  }

  implicit class HttpResponseUnmarshal(val response: HttpResponse) extends AnyVal {

    def unmarshalTo[T: FromResponseUnmarshaller]: Either[Throwable, T] = {
      if (response.status.isSuccess)
        response.as[T] match {
          case Right(value) ⇒ Right(value)
          case Left(error) ⇒ Left(throw new PipelineException(error.toString))
        }
      else Left(new UnsuccessfulResponseException(response))
    }
  }
}


/**
 * Without setup HttpConnection
 */
trait HttpCallActorSupport extends RetrySupport with ConfigurationSupport with PipelineManager {

  import ExecutionContext.Implicits.global
  import spray.httpx.RequestBuilding._

  def handle(client: Client,
             pipeline: Try[HttpRequest => Future[HttpResponseWrapper]],
             httpRequest: HttpRequest)(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    val maxRetries = hostSettings(client).maxRetries
    val requestTimeout = hostSettings(client).connectionSettings.requestTimeout
    pipeline match {
      case Success(res) => retry(res, httpRequest, maxRetries, requestTimeout)
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
    handle(client, invokeToHttpResponseWithoutSetup(client, actorRef), Get(client.endpoint + uri))
  }

  def post[T: Marshaller](client: Client, actorRef: ActorRef, uri: String, content: Some[T])
                         (implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(client, invokeToHttpResponseWithoutSetup(client, actorRef), Post(client.endpoint + uri, content))
  }

  def put[T: Marshaller](client: Client, actorRef: ActorRef, uri: String, content: Some[T])
                        (implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(client, invokeToHttpResponseWithoutSetup(client, actorRef), Put(client.endpoint + uri, content))
  }

  def head(client: Client, actorRef: ActorRef, uri: String)
          (implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(client, invokeToHttpResponseWithoutSetup(client, actorRef), Head(client.endpoint + uri))
  }

  def delete(client: Client, actorRef: ActorRef, uri: String)
            (implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(client, invokeToHttpResponseWithoutSetup(client, actorRef), Delete(client.endpoint + uri))
  }

  def options(client: Client, actorRef: ActorRef, uri: String)
             (implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(client, invokeToHttpResponseWithoutSetup(client, actorRef), Options(client.endpoint + uri))
  }
}

class HttpClientCallerActor(client: Client) extends Actor with HttpCallActorSupport with ActorLogging {

  implicit val system = context.system

  import ExecutionContext.Implicits.global

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
    case HttpClientActorMessage.Put(uri, content, json4sSupport) =>
      IO(Http) ! hostConnectorSetup(client)
      context.become(receivePostConnection(HttpMethods.PUT, uri, content, client, sender(), json4sSupport))
    case HttpClientActorMessage.Post(uri, content, json4sSupport) =>
      IO(Http) ! hostConnectorSetup(client)
      context.become(receivePostConnection(HttpMethods.POST, uri, content, client, sender(), json4sSupport))

  }

  def receiveGetConnection(httpMethod: HttpMethod, uri: String, client: Client, actorRef: ActorRef): Actor.Receive = {
    case Http.HostConnectorInfo(connector, _) =>
      implicit val timeout: Timeout = hostSettings(client).connectionSettings.connectingTimeout.toMillis
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

  def receivePostConnection[T <: AnyRef](httpMethod: HttpMethod, 
                                         uri: String, 
                                         content: Some[T], 
                                         client: Client, 
                                         actorRef: ActorRef, 
                                         json4sSupport: BaseJson4sSupport): Actor.Receive = {
    case Http.HostConnectorInfo(connector, _) =>
      implicit val marshaller = json4sSupport.json4sMarshaller[T]
      implicit val timeout: Timeout = hostSettings(client).connectionSettings.connectingTimeout.toMillis
      httpMethod match {
        case HttpMethods.POST =>
          post[T](client, connector, uri, content).pipeTo(actorRef)
        case HttpMethods.PUT =>
          put[T](client, connector, uri, content).pipeTo(actorRef)
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
          client.endpoint = client.endpoint match {
            case Some(endpoint) => Some(Endpoint(endpoint.uri, conf))
            case None => None
          }
          HttpClientManager.httpClientMap.put((client.name, client.env), (client, self))
          sender ! UpdateSuccess
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
    case client @ Create(name, env, pipeline) =>
      httpClientMap.get((name, env)) match {
        case Some(_) =>
          sender ! HttpClientExistException(name, env)
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
    case Get(name, env) =>
      httpClientMap.get((name, env)) match {
        case Some((c, r)) =>
          sender ! r
        case None     =>
          sender ! HttpClientNotExistException(name, env)
      }
    case GetAll =>
      sender ! httpClientMap
  }

}