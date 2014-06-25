package org.squbs.hc.actor

import akka.actor._
import spray.http._
import spray.can.Http
import akka.io.IO
import org.squbs.hc.pipeline.PipelineManager
import org.squbs.hc._
import akka.pattern._
import org.squbs.hc.actor.HttpClientMessage.{HttpClientGetMsg, HttpClientPostMsg}
import org.squbs.hc.config.{HostConfiguration, ServiceConfiguration, Configuration}
import akka.util.Timeout
import spray.httpx.unmarshalling._
import spray.httpx.{PipelineException, UnsuccessfulResponseException}
import spray.httpx.marshalling.{MarshallingContext, Marshaller}

/**
 * Created by hakuang on 6/23/2014.
 */

class HttpClientManagerExtension(system: ExtendedActorSystem) extends Extension {

  val httpClientActor = system.actorOf(Props[HttpClientManager], "httpClientActor")
}

object HttpClientManager extends ExtensionId[HttpClientManagerExtension] with ExtensionIdProvider {

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
    case msg @ HttpClientGetMsg(name, uri, httpMethod, env, config, pipeline) =>
      IO(Http) ! PipelineManager.hostConnectorSetup(msg)
      context.become(receiveGetConnection(msg, sender()))
    case msg @ HttpClientPostMsg(name, uri, httpMethod, content, env, config, pipeline) =>
      IO(Http) ! PipelineManager.hostConnectorSetup(msg)
      context.become(receivePostConnection(msg, sender()))
  }

  def receiveGetConnection(msg: HttpClientGetMsg, actorRef: ActorRef): Actor.Receive = {
    case Http.HostConnectorInfo(connector, _) =>
      implicit val timeout: Timeout = msg.config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration())).svcConfig.connectionTimeout.toMillis
      msg.httpMethod match {
        case HttpMethods.GET =>
          get(msg, connector, msg.uri).pipeTo(actorRef)
          context.stop(self)
        case HttpMethods.DELETE =>
          delete(msg, connector, msg.uri).pipeTo(actorRef)
          context.stop(self)
        case HttpMethods.HEAD =>
          head(msg, connector, msg.uri).pipeTo(actorRef)
          context.stop(self)
        case HttpMethods.OPTIONS =>
          options(msg, connector, msg.uri).pipeTo(actorRef)
          context.stop(self)
        case httpMethod =>
          log.error(s"HttpClientCallActor->HttpClientGetMsg, it only could handle GET/DELETE/HEAD/OPTIONS methods, cannot handle $httpMethod")
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

  def receivePostConnection[T](msg: HttpClientPostMsg[T], actorRef: ActorRef): Actor.Receive = {
    case Http.HostConnectorInfo(connector, _) =>
      implicit val timeout: Timeout = msg.config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration())).svcConfig.connectionTimeout.toMillis
      msg.httpMethod match {
        case HttpMethods.POST =>
          post[T](msg, connector, msg.uri, msg.content).pipeTo(actorRef)
          context.stop(self)
        case HttpMethods.PUT =>
          put[T](msg, connector, msg.uri, msg.content).pipeTo(actorRef)
          context.stop(self)
        case httpMethod =>
          log.error(s"HttpClientCallActor->HttpClientPostMsg, it only could handle POST/PUT methods, cannot handle $httpMethod")
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
  override def receive: Receive = {
    case msg @ HttpClientPostMsg(name, uri, httpMethod, content, env, config, pipeline) =>
      context.actorOf(Props(classOf[HttpClientCallActor])).forward(msg)
    case msg @ HttpClientGetMsg(name, uri, httpMethod, env, config, pipeline) =>
      context.actorOf(Props(classOf[HttpClientCallActor])).forward(msg)
//    case msg @ HttpClientEntityMsg(name, uri, httpMethod, content, env, config, pipeline) =>
//      context.actorOf(Props(new HttpClientEntityCallActor(sender))) ! msg
  }
}