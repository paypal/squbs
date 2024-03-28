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
import org.apache.pekko.http.javadsl.{model => jm}
import org.apache.pekko.http.scaladsl.{model => sm}

import scala.annotation.varargs
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters.RichOption
import scala.util.Try

object RequestContext {

  def apply(request: sm.HttpRequest, httpPipeliningOrder: Long): RequestContext =
    new RequestContext(request, httpPipeliningOrder)

  /**
    * Java API
    */
  def create(request: jm.HttpRequest, httpPipeliningOrder: Long): RequestContext =
    apply(request.asInstanceOf[sm.HttpRequest], httpPipeliningOrder)

}

case class RequestContext private (request: sm.HttpRequest,
                                   httpPipeliningOrder: Long,
                                   response: Option[Try[sm.HttpResponse]] = None,
                                   attributes: Map[String, Any] = Map.empty) {

  def withResponse(response: Try[jm.HttpResponse]): RequestContext =
    copy(response = Option(response.map(_.asInstanceOf[sm.HttpResponse])))

  def withAttribute(name: String, value: Any): RequestContext =
    this.copy(attributes = this.attributes + (name -> value))

  def withAttributes(attributes: (String, Any)*): RequestContext = withAttributes(attributes.toMap)

  /**
    * Java API
    */
  def withAttributes(attributes: java.util.Map[String, Any]): RequestContext = withAttributes(attributes.asScala.toMap)

  def removeAttribute(name: String): RequestContext = removeAttributes(name)

  @varargs
  def removeAttributes(names: String*): RequestContext = this.copy(attributes = this.attributes -- names)

  def withRequestHeader(header: jm.HttpHeader): RequestContext = withRequestHeaders(header)

  @varargs
  def withRequestHeaders(headers: jm.HttpHeader*): RequestContext = copy(request = request.addHeaders(headers.asJava))

  def withResponseHeader(header: jm.HttpHeader): RequestContext = withResponseHeaders(header)

  @varargs
  def withResponseHeaders(headers: jm.HttpHeader*): RequestContext =
    copy(response = response.map(_.map(_.addHeaders(headers.asJava))))

  def abortWith(httpResponse: jm.HttpResponse): RequestContext =
    copy(response = Option(Try(httpResponse.asInstanceOf[sm.HttpResponse])))

  def attribute[T](key: String): Option[T] = attributes.get(key) flatMap (v => Option(v).asInstanceOf[Option[T]])

  /**
    * Java API
    */
  def getAttribute[T](key: String): Optional[T] = attribute(key).asJava

  /**
    Java API
   */
  def getRequest: jm.HttpRequest = request

  /**
    * Java API
    */
  def getResponse: Optional[Try[jm.HttpResponse]] = response.map(_.map(_.asInstanceOf[jm.HttpResponse])).asJava

  private def withAttributes(attributes: Map[String, Any]): RequestContext =
    this.copy(attributes = this.attributes ++ attributes)
}
