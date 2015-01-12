package org.squbs.proxy

import akka.actor._
import scala.concurrent.Future
import com.typesafe.config.Config
import spray.http._
import spray.http.HttpRequest
import spray.http.ChunkedRequestStart
import scala.Some
import spray.http.HttpResponse
import scala.util.Try
import org.squbs.unicomplex.Initialized

/**
 * Created by lma on 15-1-7.
 */

case class RequestContext(
                           request: HttpRequest,
                           isChunkRequest: Boolean = false,
                           response: ProxyResponse = ResponseNotReady,
                           attributes: Map[String, Any] = Map.empty //Store any other data
                           ) {
  def getAttribute[T](key: String): Option[T] = {
    attributes.get(key) match {
      case None => None
      case Some(null) => None
      case Some(value) => Some(value.asInstanceOf[T])
    }
  }
}

trait ProxyResponse

object ResponseNotReady extends ProxyResponse

case class ExceptionalResponse(
                                response: HttpResponse = ExceptionalResponse.defaultErrorResponse,
                                cause: Option[Throwable] = None,
                                original: Option[NormalProxyResponse] = None) extends ProxyResponse

object ExceptionalResponse {

  val defaultErrorResponse = HttpResponse(status = StatusCodes.InternalServerError, entity = "Service Error!")

  def apply(t: Throwable): ExceptionalResponse = apply(t, None)

  def apply(t: Throwable, originalResp: Option[NormalProxyResponse]): ExceptionalResponse = {
    val message = t.getMessage match {
      case null | "" => "Service Error!"
      case other => other
    }

    ExceptionalResponse(HttpResponse(status = StatusCodes.InternalServerError, entity = message), cause = Option(t), original = originalResp)
  }

}

case class AckInfo(rawAck: Any, receiver: ActorRef)

case class NormalProxyResponse(
                                source: ActorRef = ActorRef.noSender,
                                confirmAck: Option[Any] = None,
                                isChunkStart: Boolean = false,
                                data: HttpResponsePart) extends ProxyResponse {

  def buildRealResponse: Try[HttpMessagePartWrapper] =
    Try {
      this match {
        case NormalProxyResponse(_, None, false, r@(_: HttpResponse)) => r
        case NormalProxyResponse(_, None, true, r@(_: HttpResponse)) => ChunkedResponseStart(r)
        case NormalProxyResponse(_, None, _, r@(_: MessageChunk | _: ChunkedMessageEnd)) => r
        case NormalProxyResponse(_, Some(ack), true, r@(_: HttpResponse)) => Confirmed(ChunkedResponseStart(r), AckInfo(ack, source))
        case NormalProxyResponse(_, Some(ack), _, r@(_: MessageChunk | _: ChunkedMessageEnd)) => Confirmed(r, AckInfo(ack, source))
        case other => throw new IllegalArgumentException("Illegal ProxyResponse: " + this)
      }
    }

  def getHttpResponse: Option[HttpResponse] = {
    if (data.isInstanceOf[HttpResponse]) {
      Some(data.asInstanceOf[HttpResponse])
    } else {
      None
    }
  }

}

object NormalProxyResponse {

  def apply(resp: HttpResponse): NormalProxyResponse = new NormalProxyResponse(data = resp)

  def apply(chunkStart: ChunkedResponseStart): NormalProxyResponse = new NormalProxyResponse(isChunkStart = true, data = chunkStart.response)

  def apply(chunkMsg: MessageChunk): NormalProxyResponse = new NormalProxyResponse(data = chunkMsg)

  def apply(chunkEnd: ChunkedMessageEnd): NormalProxyResponse = new NormalProxyResponse(data = chunkEnd)

  def apply(confirm: Confirmed, from: ActorRef): NormalProxyResponse = confirm match {
    case Confirmed(ChunkedResponseStart(resp), ack) => new NormalProxyResponse(source = from, confirmAck = Some(ack), isChunkStart = true, data = resp)
    case Confirmed(r@(_: HttpResponsePart), ack) => new NormalProxyResponse(source = from, confirmAck = Some(ack), isChunkStart = false, data = r)
    case other => throw new IllegalArgumentException("Unsupported confirmed message: " + confirm.messagePart)
  }

}


abstract class ServiceProxy(settings: Option[Config], hostActorProps: Props) extends Actor with ActorLogging {

  import context.dispatcher

  val hostActor = context.actorOf(hostActorProps)


  def handleRequest(requestCtx: RequestContext, responder: ActorRef)(implicit actorContext: ActorContext): Unit

  def receive: Actor.Receive = {


    //forward msg from route definition
    case Initialized(report) =>
      context.parent ! Initialized(report)

    case req: HttpRequest =>
      val client = sender()
      Future {
        handleRequest(RequestContext(req), client)
      }


    case crs: ChunkedRequestStart =>
      val client = sender()
      Future {
        handleRequest(RequestContext(crs.request, true), client)
      }


    //let underlying actor handle it
    case other =>
      hostActor forward other

  }


}
