/*
 * Copyright 2017 PayPal
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

package org.squbs.pipeline

import java.util.Optional

import akka.http.javadsl.model.{HttpRequest => jmHttpRequest, HttpResponse => jmHttpResponse}
import akka.http.scaladsl.model.{HttpHeader, HttpRequest, HttpResponse}

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import scala.util.{Success, Try}

object RequestContext {

  /**
    * Java API. For creating instance of RequestContext
    */
  def create(request: jmHttpRequest, httpPipeliningOrder: Long): RequestContext =
    RequestContext(request.asInstanceOf[HttpRequest], httpPipeliningOrder)

}

case class RequestContext(request: HttpRequest,
                          httpPipeliningOrder: Long, // TODO Come up with a better val name
                          response: Option[Try[HttpResponse]] = None,
                          attributes: Map[String, Any] = Map.empty) {

  def withResponse(response: Option[Try[HttpResponse]]): RequestContext = {
    copy(response = response)
  }

  /*
  Java API
   */
  def withResponse(response: Optional[Try[jmHttpResponse]]): RequestContext = {
    withResponse(response.asScala.asInstanceOf[Option[Try[HttpResponse]]])
  }

  def withAttributes(attributes: (String, Any)*): RequestContext = {
    this.copy(attributes = this.attributes ++ attributes)
  }

  /*
  Java API
   */
  def withAttributes(attributes: java.util.List[(String, Any)]): RequestContext = {
    withAttributes(attributes = attributes.asScala: _*)
  }

  def removeAttributes(attributeKeys: String*): RequestContext = {
    this.copy(attributes = this.attributes -- attributeKeys)
  }

  /*
    Java API
  */
  def removeAttributes(attributeKeys: java.util.List[String]): RequestContext = {
    removeAttributes(attributeKeys.asScala: _*)
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

  /*
    Java API
   */
  def getRequest: jmHttpRequest = request.asInstanceOf[jmHttpRequest]

  def getResponse: Optional[Try[jmHttpResponse]] =
    Optional.ofNullable(response.orNull.asInstanceOf[Try[jmHttpResponse]])

  def getAttribute[T](key: String): Optional[T] = attribute(key).asJava

  def abortWith(httpResponse: jmHttpResponse): RequestContext =
    copy(response = Option(Try(httpResponse.asInstanceOf[HttpResponse])))

}
