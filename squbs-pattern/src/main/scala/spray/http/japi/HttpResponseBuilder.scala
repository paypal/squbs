/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the AUTHORS file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package spray.http.japi

import com.typesafe.scalalogging.LazyLogging
import spray.http.HttpHeaders.RawHeader
import spray.http._
import spray.http.parser.HttpParser
import collection.JavaConversions._
import scala.collection.mutable.ListBuffer

class HttpResponseBuilder {
  private var status: StatusCode = StatusCodes.OK
  private var entity: HttpEntity = HttpEntity.Empty
  private val headers = new ListBuffer[HttpHeader]
  private var protocol: HttpProtocol = HttpProtocols.`HTTP/1.1`

  def status(status: StatusCode): HttpResponseBuilder = {
    require(status != null, "status can't be null")
    this.status = status
    this
  }

  def entity(entity: HttpEntity): HttpResponseBuilder = {
    require(entity != null, "entity can't be null")
    this.entity = entity
    this
  }

  def entity(contentType: ContentType, data: String): HttpResponseBuilder = this.entity(HttpEntity(contentType, data))

  def entity(contentType: ContentType, data: Array[Byte]): HttpResponseBuilder = this
    .entity(HttpEntity(contentType, data))

  def entity(data: String): HttpResponseBuilder = this.entity(HttpEntity(data))

  def entity(data: Array[Byte]): HttpResponseBuilder = this.entity(HttpEntity(data))

  def headers(headers: java.util.List[HttpHeader]): HttpResponseBuilder = {
    if (headers != null) this.headers ++= headers
    this
  }

  def header(header: HttpHeader): HttpResponseBuilder = {
    assert(header != null, "header can't be null")
    this.headers += header
    this
  }

  def protocol(protocol: String): HttpResponseBuilder = {
    require(protocol != null, "protocol can't be null")
    this.protocol = HttpProtocols.getForKey(protocol).get
    this
  }

  def build() = HttpResponse(status, entity, headers.toList, protocol)
}

object ContentTypeFactory {
  /**
   *
   * @param contentType value like <code>application/json</code> or <code>application/json; charset=utf-8</code>
   * @return
   */
  def create(contentType: String) = {
    HttpParser.parse(HttpParser.ContentTypeHeaderValue, contentType) match {
      case Right(c) => c
      case Left(error) =>
        throw new IllegalArgumentException(
          error.withSummaryPrepended(s"Invalid content-type value:$contentType").summary)
    }
  }
}


object HttpHeaderFactory extends LazyLogging {
  def create(name: String, value: String) = {
    HttpParser.parserRules.get(name.toLowerCase) match {
      case Some(rule) => HttpParser.parse(rule, value) match {
        // try ModeledHeader first
        case Right(h) => h
        case Left(e) =>
          // fallback to RawHeader if parsing failed
          // following the logic in spray.can.parsing.HttpHeaderParser.modelledHeaderValueParser
          logger.warn(e.withSummaryPrepended(s"Illegal '$name' header").summary)
          RawHeader(name, value)
      }
      case _ => RawHeader(name, value)
    }
  }
}