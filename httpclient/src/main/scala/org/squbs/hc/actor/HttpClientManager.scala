package org.squbs.hc.actor

import akka.actor._
import spray.http._
import spray.can.Http
import akka.io.IO
import org.squbs.hc.pipeline.PipelineManager
import org.squbs.hc._
import akka.pattern._
import org.squbs.hc.actor.HttpClientMessage._
import akka.util.Timeout
import spray.httpx.unmarshalling._
import spray.httpx.{PipelineException, UnsuccessfulResponseException}
import spray.httpx.marshalling.{Marshaller}
import scala.collection.concurrent.TrieMap
import org.squbs.hc.config.ServiceConfiguration
import org.squbs.hc.actor.HttpClientMessage.MarkUpHttpClientMsg
import org.squbs.hc.actor.HttpClientMessage.CreateHttpClientSuccessMsg
import org.squbs.hc.actor.HttpClientMessage.MarkDownHttpClientSuccessMsg
import org.squbs.hc.actor.HttpClientMessage.CreateHttpClientFailureMsg
import org.squbs.hc.actor.HttpClientMessage.MarkDownHttpClientFailureMsg
import org.squbs.hc.actor.HttpClientMessage.DeleteHttpClientMsg
import org.squbs.hc.actor.HttpClientMessage.MarkDownHttpClientMsg
import org.squbs.hc.actor.HttpClientMessage.HttpClientPostCallMsg
import org.squbs.hc.actor.HttpClientMessage.GetHttpClientMsg
import scala.Some
import org.squbs.hc.actor.HttpClientMessage.MarkUpHttpClientSuccessMsg
import org.squbs.hc.actor.HttpClientMessage.UpdateHttpClientMsg
import org.squbs.hc.actor.HttpClientMessage.MarkUpHttpClientFailureMsg
import org.squbs.hc.actor.HttpClientMessage.HttpClientGetCallMsg
import spray.http.HttpResponse
import org.squbs.hc.HttpResponseEntityWrapper
import org.squbs.hc.config.Configuration
import org.squbs.hc.actor.HttpClientMessage.CreateHttpClientMsg
import org.squbs.hc.actor.HttpClientMessage.GetAllHttpClientSuccessMsg
import org.squbs.hc.actor.HttpClientMessage.DeleteHttpClientFailureMsg
import org.squbs.hc.actor.HttpClientMessage.DeleteHttpClientSuccessMsg
import org.squbs.hc.HttpClientNotExistException
import org.squbs.hc.actor.HttpClientMessage.GetHttpClientSuccessMsg
import org.squbs.hc.actor.HttpClientMessage.HttpClientGetCallFailureMsg
import org.squbs.hc.HttpClientExistException
import org.squbs.hc.config.HostConfiguration
import org.squbs.hc.actor.HttpClientMessage.UpdateHttpClientFailureMsg
import org.squbs.hc.HttpClientNotSupportMethodException
import org.squbs.hc.actor.HttpClientMessage.GetHttpClientFailureMsg
import org.squbs.hc.actor.HttpClientMessage.UpdateHttpClientSuccessMsg

/**
 * Created by hakuang on 6/23/2014.
 */

class HttpClientManagerExtension(system: ExtendedActorSystem) extends Extension {

  val httpClientManager = system.actorOf(Props[HttpClientManager], "httpClientManager")
}

object HttpClientManager extends ExtensionId[HttpClientManagerExtension] with ExtensionIdProvider {

  val httpClientMap: TrieMap[(String, Option[String]), IHttpClient] = TrieMap[(String, Option[String]), IHttpClient]()

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

class HttpClientCallActor extends Actor with HttpCallActorSupport with ActorLogging {

  implicit val system = context.system

  import scala.concurrent.ExecutionContext.Implicits.global

  override def receive: Actor.Receive = {
    case (msg@HttpClientGetCallMsg(name, env, httpMethod, uri), hc: IHttpClient) =>
      IO(Http) ! PipelineManager.hostConnectorSetup(hc)
      context.become(receiveGetConnection(msg, hc, sender()))
    case (msg@HttpClientPostCallMsg(name, env, httpMethod, uri, content), hc: IHttpClient) =>
      IO(Http) ! PipelineManager.hostConnectorSetup(hc)
      context.become(receivePostConnection(msg, hc, sender()))
  }

  def receiveGetConnection(msg: HttpClientGetCallMsg, hc: IHttpClient, actorRef: ActorRef): Actor.Receive = {
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
          actorRef ! HttpClientGetCallFailureMsg(HttpClientNotSupportMethodException(msg.name))
          log.error(HttpClientNotSupportMethodException(msg.name).getMessage)
          context.stop(self)
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

  def receivePostConnection[T](msg: HttpClientPostCallMsg[T], hc: IHttpClient, actorRef: ActorRef): Actor.Receive = {
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
          actorRef ! HttpClientPostCallFailureMsg(HttpClientNotSupportMethodException(msg.name))
          log.error(HttpClientNotSupportMethodException(msg.name).getMessage)
          context.stop(self)
      }
  }
}

class HttpClientManager extends Actor {

  import HttpClientManager.httpClientMap
  override def receive: Receive = {
    case msg @ CreateHttpClientMsg(name, env, config, pipeline) =>
      httpClientMap.get((name, env)) match {
        case Some(hc) => sender ! CreateHttpClientFailureMsg(HttpClientExistException(name, env))
        case None     => sender ! CreateHttpClientSuccessMsg(httpClientMap.getOrElseUpdate((name, env), msg))
      }
    case msg @ UpdateHttpClientMsg(name, env, config, pipeline) =>
      httpClientMap.get((name, env)) match {
        case Some(hc) =>
          httpClientMap.put((name, env), msg)
          sender ! UpdateHttpClientSuccessMsg(msg)
        case None     => sender ! UpdateHttpClientFailureMsg(HttpClientNotExistException(name, env))
      }
    case msg @ GetHttpClientMsg(name, env) =>
      httpClientMap.get((name, env)) match {
        case Some(hc) => sender ! GetHttpClientSuccessMsg(hc)
        case None     => sender ! GetHttpClientFailureMsg(HttpClientNotExistException(name, env))
      }
    case msg @ GetAllHttpClientMsg => sender ! GetAllHttpClientSuccessMsg(httpClientMap)
    case msg @ DeleteHttpClientMsg(name, env) =>
      httpClientMap.get((name, env)) match {
        case Some(hc) =>
          httpClientMap.remove((name, env))
          sender ! DeleteHttpClientSuccessMsg(hc)
        case None     => sender ! DeleteHttpClientFailureMsg(HttpClientNotExistException(name, env))
      }
    case msg @ DeleteAllHttpClientMsg =>
      httpClientMap.clear
      sender ! DeleteAllHttpClientSuccessMsg(httpClientMap)
    case msg @ MarkDownHttpClientMsg(name, env) =>
      httpClientMap.get((name, env)) match {
        case Some(hc) =>
          hc.status = HttpClientStatus.DOWN
          httpClientMap.put((name, env), hc)
          sender ! MarkDownHttpClientSuccessMsg(hc)
        case None     => sender ! MarkDownHttpClientFailureMsg(HttpClientNotExistException(name, env))
      }
    case msg @ MarkUpHttpClientMsg(name, env) =>
      httpClientMap.get((name, env)) match {
        case Some(hc) =>
          hc.status = HttpClientStatus.UP
          httpClientMap.put((name, env), hc)
          sender ! MarkUpHttpClientSuccessMsg(hc)
        case None     => sender ! MarkUpHttpClientFailureMsg(HttpClientNotExistException(name, env))
      }
    case msg @ HttpClientPostCallMsg(name, env, httpMethod, uri, content) =>
      httpClientMap.get((name, env)) match {
        case Some(hc) => context.actorOf(Props(classOf[HttpClientCallActor])).forward((msg, hc))
        case None     => sender ! HttpClientNotExistException(name, env)
      }

    case msg @ HttpClientGetCallMsg(name, env, httpMethod, uri) =>
      httpClientMap.get((name, env)) match {
        case Some(hc) => context.actorOf(Props(classOf[HttpClientCallActor])).forward((msg, hc))
        case None => sender ! HttpClientNotExistException(name, env)
      }
  }

}