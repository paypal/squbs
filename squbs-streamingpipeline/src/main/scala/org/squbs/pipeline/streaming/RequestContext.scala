/*
 * Copyright 2015 PayPal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.squbs.pipeline.streaming

import akka.http.scaladsl.model.{HttpHeader, HttpResponse, HttpRequest}

import scala.collection.JavaConversions._
import scala.util.{Success, Try}

case class RequestContext(request: HttpRequest,
                          httpPipeliningOrder: Int, // TODO Come up with a better val name
                          response: Option[Try[HttpResponse]] = None,
                          attributes: Map[String, Any] = Map.empty) {

  def ++(attributes: (String, Any)*): RequestContext = {
    this.copy(attributes = this.attributes ++ attributes)
  }

  /*
  Java API
   */
  def withAttributes(attributes: java.util.List[(String, Any)]): RequestContext = {
    ++(attributes: _*)
  }

  def --(attributeKeys: String*): RequestContext = {
    this.copy(attributes = this.attributes -- attributeKeys)
  }

  /*
    Java API
  */
  def removeAttributes(attributeKeys: java.util.List[String]): RequestContext = {
    --(attributeKeys: _*)
  }

  def attribute[T](key: String): Option[T] = {
    attributes.get(key) flatMap (v => Option(v).asInstanceOf[Option[T]])
  }

  def addRequestHeader(header: HttpHeader): RequestContext = {
    copy(request = request.copy(headers = request.headers :+ header))
  }

  def addRequestHeaders(headers: HttpHeader*): RequestContext = {
    copy(request = request.copy(headers = request.headers ++ headers))
  }

  def addResponseHeader(header: HttpHeader): RequestContext = {
    addResponseHeaders(header)
  }

  def addResponseHeaders(headers: HttpHeader*): RequestContext = {
    response.fold(this) {
      case Success(resp) => copy(response = Option(Try(resp.copy(headers = resp.headers ++ headers))))
      case _ => this
    }
  }

  def abortWith(httpResponse: HttpResponse): RequestContext = copy(response = Option(Try(httpResponse)))
}