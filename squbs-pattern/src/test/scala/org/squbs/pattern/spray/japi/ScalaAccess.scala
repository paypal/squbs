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
package org.squbs.pattern.spray.japi

import spray.http._
import spray.httpx.marshalling.{BasicMarshallers, ToResponseMarshaller}
import spray.httpx.unmarshalling.{BasicUnmarshallers, UnmarshallerLifting}


object ScalaAccess {
  val basicMarshallers = BasicMarshallers
  val basicUnmarshallers = BasicUnmarshallers
  val toResponseMarshaller = ToResponseMarshaller
  val unmarshallerLifting = UnmarshallerLifting

  val httpEntity = HttpEntity
  val chunkedMessageEnd = ChunkedMessageEnd
  val contentType = ContentType

  def list[T](element: T) = List(element)

  val applicationJson = ContentTypes.`application/json`
  val applicationJavascript = MediaTypes.`application/javascript`

  val contentTypeHeader = HttpHeaders.`Content-Type`
  val charsetUTF8 = HttpCharsets.`UTF-8`


  val http_1_1 = HttpProtocols.`HTTP/1.1`
  val http_1_0 = HttpProtocols.`HTTP/1.0`

  val contentTypeClass = classOf[HttpHeaders.`Content-Type`]

}
