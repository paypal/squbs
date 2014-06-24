package org.squbs.hc.actor

import akka.actor._
import spray.http._
import spray.can.Http
import akka.io.IO
import spray.client.pipelining
import org.squbs.hc.pipeline.{PipelineManager}
import org.squbs.hc._
import akka.pattern._
import org.squbs.hc.actor.HttpClientMessage.HttpClientEntityMsg
import org.squbs.hc.actor.HttpClientMessage.HttpClientMsg
import scala.Some
import org.squbs.hc.config.{HostConfiguration, ServiceConfiguration, Configuration}
import akka.util.Timeout

/**
 * Created by hakuang on 6/23/2014.
 */

class HttpClientManagerExtension(system: ExtendedActorSystem) extends Extension {

  val httpClientActor = system.actorOf(Props[HttpClientManager], "httpClientActor")
}

object HttpClientManager extends ExtensionId[HttpClientManagerExtension] with ExtensionIdProvider {

  override def createExtension(system: ExtendedActorSystem): HttpClientManagerExtension = new HttpClientManagerExtension(system)

  override def lookup(): ExtensionId[_ <: Extension] = HttpClientManager
}

class HttpClientCallActor(client: ActorRef) extends Actor with HttpCallActorSupport {

  implicit val system = context.system
  import scala.concurrent.ExecutionContext.Implicits.global

  override def receive: Actor.Receive = {
    case msg @ HttpClientMsg(name, uri, httpMethod, content, env, config, pipeline) =>
      IO(Http) ! PipelineManager.hostConnectorSetup(msg)
      context.become(receiveConnection(msg))
  }

  def receiveConnection[T](msg: HttpClientMsg[T]): Actor.Receive = {
    case Http.HostConnectorInfo(connector, _) =>
      implicit val timeout: Timeout = Timeout(msg.config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration())).svcConfig.connectionTimeout.toMillis)
      msg.httpMethod match {
        case HttpMethods.GET =>
          val result = get(msg, connector, msg.uri)
          result.pipeTo(client)
//        case HttpMethods.POST => post[T](msg, connector, msg.uri, Some(msg.content.get)).pipeTo(client)
        case HttpMethods.DELETE => delete(msg, connector, msg.uri).pipeTo(client)
        case HttpMethods.HEAD => head(msg, connector, msg.uri).pipeTo(client)
//        case HttpMethods.PUT => put[T](msg, connector, msg.uri, Some(msg.content.get)).pipeTo(client)
        case HttpMethods.OPTIONS => options(msg, connector, msg.uri).pipeTo(client)
        case httpMethod => //TODO not support
      }
  }
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
    case msg @ HttpClientMsg(name, uri, httpMethod, content, env, config, pipeline) =>
      context.actorOf(Props(new HttpClientCallActor(sender))) ! msg
//    case msg @ HttpClientEntityMsg(name, uri, httpMethod, content, env, config, pipeline) =>
//      context.actorOf(Props(new HttpClientEntityCallActor(sender))) ! msg
  }
}