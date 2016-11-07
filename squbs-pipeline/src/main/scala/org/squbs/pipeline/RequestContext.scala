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

package org.squbs.pipeline

import akka.actor._
import spray.http._

import scala.collection.JavaConversions._

case class RequestContext(request: HttpRequest,
                          isChunkedRequest: Boolean = false,
                          response: ProxyResponse = ResponseNotReady,
                          attributes: Map[String, Any] = Map.empty) {
  //Store any other data
  def attribute[T](key: String): Option[T] = {
    attributes.get(key) flatMap (v => Option(v).asInstanceOf[Option[T]])
  }

  def payload = {
    isChunkedRequest match {
      case true => ChunkedRequestStart(request)
      case false => request
    }
  }

  //add some context attributes
  def +>(attributes: (String, Any)*): RequestContext = {
    this.copy(attributes = this.attributes ++ attributes)
  }

  /*
  Java API
   */
  def withAttributes(attributes: java.util.List[(String, Any)]): RequestContext = {
    +>(attributes: _*)
  }

  //remove some context attributes
  def ->(attributeKeys: String*): RequestContext = {
    this.copy(attributes = this.attributes -- attributeKeys)
  }

    /*
  Java API
   */
  def removeAttributes(attributeKeys: java.util.List[String]): RequestContext = {
    ->(attributeKeys: _*)
  }

  def responseReady: Boolean = response match {
    case nr: NormalResponse => true
    case er: ExceptionalResponse => true
    case _ => false
  }

  def addResponseHeaders(headers: HttpHeader*): RequestContext = {
    response match {
      case nr@NormalResponse(r) =>
        copy(response = nr.update(r.copy(headers = r.headers ++ headers)))

      case er@ExceptionalResponse(r, _, _) =>
        copy(response = er.copy(response = r.copy(headers = r.headers ++ headers)))

      case other => this //do nothing
    }
  }

  def addResponseHeader(header: HttpHeader): RequestContext = {
    addResponseHeaders(header)
  }

  def addRequestHeaders(headers: HttpHeader*): RequestContext = {
    copy(request = request.copy(headers = request.headers ++ headers))
  }

  def addRequestHeader(header: HttpHeader): RequestContext = {
    copy(request = request.copy(headers = request.headers :+ header))
  }

}

// $COVERAGE-OFF$
sealed trait ProxyResponse

object ProxyResponse {

  implicit class PipeCopyHelper(val response: ProxyResponse) extends AnyVal {
    def +(header: HttpHeader): ProxyResponse = {
      response match {
        case n@NormalResponse(resp) =>
          n.update(resp.copy(headers = header :: resp.headers))
        case e: ExceptionalResponse =>
          e.copy(response = e.response.copy(headers = header :: e.response.headers))
        case other => other
      }
    }

    def -(header: HttpHeader): ProxyResponse = {
      response match {
        case n@NormalResponse(resp) =>
          val originHeaders = resp.headers.groupBy[Boolean](_.name == header.name)
          n.update(resp.copy(headers = originHeaders.getOrElse(false, List.empty)))
        case e: ExceptionalResponse =>
          val originHeaders = e.response.headers.groupBy[Boolean](_.name == header.name)
          e.copy(response = e.response.copy(headers = originHeaders.getOrElse(false, List.empty)))
        case other => other
      }
    }
  }

}

object ResponseNotReady extends ProxyResponse

// $COVERAGE-ON$

case class ExceptionalResponse(response: HttpResponse = ExceptionalResponse.defaultErrorResponse,
                               cause: Option[Throwable] = None,
                               original: Option[NormalResponse] = None) extends ProxyResponse

object ExceptionalResponse {

  val defaultErrorResponse = HttpResponse(status = StatusCodes.InternalServerError, entity = "Service Error!")

  def apply(t: Throwable): ExceptionalResponse = apply(t, None)

  def apply(t: Throwable, originalResp: Option[NormalResponse]): ExceptionalResponse = {
    val message = Option(t.getMessage) flatMap { m => if (m == "") None else Some(m) } getOrElse "Service Error!"
    ExceptionalResponse(HttpResponse(status = StatusCodes.InternalServerError, entity = message),
                        cause = Option(t), original = originalResp)
  }
}

// $COVERAGE-OFF$
case class AckInfo(rawAck: Any, receiver: ActorRef)

sealed trait NormalResponse extends ProxyResponse {
  def responseMessage: HttpMessagePartWrapper

  def data: HttpResponsePart

  def update(newData: HttpResponsePart): NormalResponse
}

// $COVERAGE-ON$

sealed abstract class BaseNormalResponse(data: HttpResponsePart) extends NormalResponse {

  def validateUpdate(newData: HttpResponsePart): HttpResponsePart = {
    data match {
      case r: ChunkedResponseStart if newData.isInstanceOf[HttpResponse] => ChunkedResponseStart(newData.asInstanceOf[HttpResponse])
      case other if newData.getClass == data.getClass => newData
      case other => throw new IllegalArgumentException(s"The updated data has type:${newData.getClass}, but the original data has type:${data.getClass}")
    }
  }
}

private case class DirectResponse(data: HttpResponsePart) extends BaseNormalResponse(data) {
  def responseMessage: HttpMessagePartWrapper = data

  def update(newData: HttpResponsePart): NormalResponse = copy(validateUpdate(newData))
}

private case class ConfirmedResponse(data: HttpResponsePart,
                                     ack: Any,
                                     source: ActorRef) extends BaseNormalResponse(data) {
  override def responseMessage: HttpMessagePartWrapper = Confirmed(data, AckInfo(ack, source))

  def update(newData: HttpResponsePart): NormalResponse = copy(validateUpdate(newData))
}

object NormalResponse {
  def apply(resp: HttpResponse): NormalResponse = DirectResponse(resp)

  def apply(chunkStart: ChunkedResponseStart): NormalResponse = DirectResponse(chunkStart)

  def apply(chunkMsg: MessageChunk): NormalResponse = DirectResponse(chunkMsg)

  def apply(chunkEnd: ChunkedMessageEnd): NormalResponse = DirectResponse(chunkEnd)

  def apply(confirm: Confirmed, from: ActorRef): NormalResponse = confirm match {
    case Confirmed(r@(_: HttpResponsePart), ack) => ConfirmedResponse(r, ack, from)
    case other => throw new IllegalArgumentException("Unsupported confirmed message: " + confirm.messagePart)
  }

  def unapply(resp: NormalResponse): Option[HttpResponse] = {
    resp.data match {
      case r: HttpResponse => Some(r)
      case ChunkedResponseStart(r) => Some(r)
      case other => None
    }
  }
}

