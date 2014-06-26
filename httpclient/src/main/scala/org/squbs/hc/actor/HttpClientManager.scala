package org.squbs.hc.actor

import akka.actor._
import spray.http._
import spray.can.Http
import akka.io.IO
import org.squbs.hc.pipeline.PipelineManager
import org.squbs.hc._
import akka.pattern._
import org.squbs.hc.actor.HttpClientMessage._
import org.squbs.hc.config.{HostConfiguration, ServiceConfiguration, Configuration}
import akka.util.Timeout
import spray.httpx.unmarshalling._
import spray.httpx.{PipelineException, UnsuccessfulResponseException}
import spray.httpx.marshalling.{MarshallingContext, Marshaller}
import scala.collection.concurrent.TrieMap
import org.squbs.hc.config.ServiceConfiguration
import org.squbs.hc.actor.HttpClientMessage.CreateHttpClientMsg
import scala.Some
import org.squbs.hc.actor.HttpClientMessage.HttpClientPostMsg
import spray.http.HttpResponse
import org.squbs.hc.HttpResponseEntityWrapper
import org.squbs.hc.config.Configuration
import org.squbs.hc.actor.HttpClientMessage.UpdateHttpClientMsg
import org.squbs.hc.config.HostConfiguration
import org.squbs.hc.actor.HttpClientMessage.HttpClientGetMsg

/**
 * Created by hakuang on 6/23/2014.
 */

class HttpClientManagerExtension(system: ExtendedActorSystem) extends Extension {

  val httpClientManager = system.actorOf(Props[HttpClientManager], "httpClientManager")
}

object HttpClientManager extends ExtensionId[HttpClientManagerExtension] with ExtensionIdProvider {

//  private[HttpClientManager] val httpClientMap: TrieMap[String, IHttpClient] = TrieMap[String, IHttpClient]()

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

class HttpClientCallActor extends Actor with HttpCallActorSupport with ActorLogging{

  implicit val system = context.system
  import scala.concurrent.ExecutionContext.Implicits.global

  override def receive: Actor.Receive = {
    case (msg @ HttpClientGetMsg(name, httpMethod, uri), hc: IHttpClient) =>
      IO(Http) ! PipelineManager.hostConnectorSetup(hc)
      context.become(receiveGetConnection(msg, hc, sender()))
    case (msg @ HttpClientPostMsg(name, httpMethod, uri, content), hc: IHttpClient) =>
      IO(Http) ! PipelineManager.hostConnectorSetup(hc)
      context.become(receivePostConnection(msg, hc, sender()))
  }

  def receiveGetConnection(msg: HttpClientGetMsg, hc: IHttpClient, actorRef: ActorRef): Actor.Receive = {
    case Http.HostConnectorInfo(connector, _) =>
      implicit val timeout: Timeout = hc.config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration())).svcConfig.connectionTimeout.toMillis
      msg.httpMethod match {
        case HttpMethods.GET =>
          get(hc, connector, msg.uri).pipeTo(actorRef)
          context.stop(self)
        case HttpMethods.DELETE =>
          delete(hc, connector, msg.uri).pipeTo(actorRef)
          context.stop(self)
        case HttpMethods.HEAD =>
          head(hc, connector, msg.uri).pipeTo(actorRef)
          context.stop(self)
        case HttpMethods.OPTIONS =>
          options(hc, connector, msg.uri).pipeTo(actorRef)
          context.stop(self)
        case httpMethod =>
          log.error(HttpClientNotSupportMethodException(msg.name).getMessage)
          context.stop(self)
      }
  }

  implicit val TMarshaller = tMarshaller(ContentTypes.`application/octet-stream`)
  def tMarshaller(contentType: ContentType): Marshaller[Any] =
    Marshaller.of[Any](contentType) { (value, _, ctx) ⇒
    // we marshal to the ContentType given as argument to the method, not the one established by content-negotiation,
    // since the former is the one belonging to the byte array
      ctx.marshalTo(HttpEntity(contentType, value.asInstanceOf[String]))
    }

  def receivePostConnection[T](msg: HttpClientPostMsg[T], hc: IHttpClient, actorRef: ActorRef): Actor.Receive = {
    case Http.HostConnectorInfo(connector, _) =>
      implicit val timeout: Timeout = hc.config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration())).svcConfig.connectionTimeout.toMillis
      msg.httpMethod match {
        case HttpMethods.POST =>
          post[T](hc, connector, msg.uri, msg.content).pipeTo(actorRef)
          context.stop(self)
        case HttpMethods.PUT =>
          put[T](hc, connector, msg.uri, msg.content).pipeTo(actorRef)
          context.stop(self)
        case httpMethod =>
          log.error(HttpClientNotSupportMethodException(msg.name).getMessage)
          context.stop(self)
      }
  }

//  def receivePostConnection[T](msg: HttpClientPostMsg[T], actorRef: ActorRef): Actor.Receive = {
//    case Http.HostConnectorInfo(connector, _) =>
//      implicit val timeout: Timeout = msg.config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration())).svcConfig.connectionTimeout.toMillis
//      msg.httpMethod match {
//        case HttpMethods.POST => post[T](msg, connector, msg.uri, msg.content).pipeTo(actorRef)
//        case HttpMethods.PUT => put[T](msg, connector, msg.uri, msg.content).pipeTo(actorRef)
//        case httpMethod => //TODO not support
//      }
//  }
}
/*
class HttpClientEntityCallActor(client: ActorRef) extends Actor with HttpEntityCallActorSupport {

  implicit val system = context.system
  import scala.concurrent.ExecutionContext.Implicits.global

  override def receive: Actor.Receive = {
    case msg @ HttpClientEntityMsg(name, uri, httpMethod, content, env, config, pipeline) =>
      IO(Http) ! PipelineManager.hostConnectorSetup(msg)
      context.become(receiveConnection(msg))
  }

  def receiveConnection[T, R](msg: HttpClientEntityMsg[T, R]): Actor.Receive = {
    case Http.HostConnectorInfo(connector, _) =>
      implicit val timeout: Timeout = Timeout(msg.config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration())).svcConfig.connectionTimeout.toMillis)
      msg.httpMethod match {
        case HttpMethods.GET => getEntity[R](msg, connector, msg.uri).pipeTo(client)
        case HttpMethods.POST => postEntity[T, R](msg, connector, msg.uri, Some(msg.content.get)).pipeTo(client)
        case HttpMethods.DELETE => deleteEntity[R](msg, connector, msg.uri).pipeTo(client)
        case HttpMethods.HEAD => headEntity[R](msg, connector, msg.uri).pipeTo(client)
        case HttpMethods.PUT => putEntity[T, R](msg, connector, msg.uri, Some(msg.content.get)).pipeTo(client)
        case HttpMethods.OPTIONS => optionsEntity[R](msg, connector, msg.uri).pipeTo(client)
        case httpMethod => //TODO not support
      }
  }
}
*/
class HttpClientManager extends Actor {

  private[HttpClientManager] val httpClientMap: TrieMap[String, IHttpClient] = TrieMap[String, IHttpClient]()

  override def receive: Receive = {
    case msg @ CreateHttpClientMsg(name, env, config, pipeline) =>
      httpClientMap.get(name) match {
        case Some(hc) => sender ! HttpClientExistException(name)
        case None     => sender ! httpClientMap.getOrElseUpdate(name, msg)
      }
    case msg @ UpdateHttpClientMsg(name, env, config, pipeline) =>
      httpClientMap.get(name) match {
        case Some(hc) => sender ! httpClientMap.getOrElseUpdate(name, msg)
        case None     => sender ! HttpClientNotExistException(name)
      }
    case msg @ GetHttpClientMsg(name) =>
      httpClientMap.get(name) match {
        case Some(hc) => sender ! hc
        case None     => sender ! HttpClientNotExistException(name)
      }
    case msg @ DeleteHttpClientMsg(name) =>
      httpClientMap.get(name) match {
        case Some(hc) =>
          httpClientMap.remove(name)
          sender ! hc
        case None     => sender ! HttpClientNotExistException(name)
      }
    case msg @ MarkDownHttpClientMsg(name) =>
      httpClientMap.get(name) match {
        case Some(hc) =>
          hc.status = HttpClientStatus.DOWN
          sender ! httpClientMap.getOrElseUpdate(name, hc)
        case None     => sender ! HttpClientNotExistException(name)
      }
    case msg @ MarkUpHttpClientMsg(name) =>
      httpClientMap.get(name) match {
        case Some(hc) =>
          hc.status = HttpClientStatus.UP
          sender ! httpClientMap.getOrElseUpdate(name, hc)
        case None     => sender ! HttpClientNotExistException(name)
      }
    case msg @ HttpClientPostMsg(name, httpMethod, uri, content) =>
      httpClientMap.get(name) match {
        case Some(hc) => context.actorOf(Props(classOf[HttpClientCallActor])).forward((msg, hc))
        case None     => sender ! HttpClientNotExistException(name)
      }

    case msg @ HttpClientGetMsg(name, httpMethod, uri) =>
      httpClientMap.get(name) match {
        case Some(hc) => context.actorOf(Props(classOf[HttpClientCallActor])).forward((msg, hc))
        case None => sender ! HttpClientNotExistException(name)
      }
//    case msg @ HttpClientEntityMsg(name, uri, httpMethod, content, env, config, pipeline) =>
//      context.actorOf(Props(new HttpClientEntityCallActor(sender))) ! msg
  }

}