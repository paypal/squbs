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
package org.squbs.httpclient

import java.net.URI
import java.util.Optional
import javax.net.ssl.SSLContext

import scala.compat.java8.OptionConverters._

/**
  * End point type for HTTP/HTTPS.
  * @param uri The HTTP request URI.
  * @param sslContext The SSL context, if any.
  */
case class HttpEndpoint(uri: URI, sslContext: Option[SSLContext] = None)

/**
  * Creators for HttpEndpoint.
  */
object HttpEndpoint {
  def apply(s: String): HttpEndpoint = HttpEndpoint(new URI(s))

  def apply(s: String, sslContext: Option[SSLContext]): HttpEndpoint = HttpEndpoint(new URI(s), sslContext)

  /**
    * Java API
    */
  def create(s: String): HttpEndpoint = HttpEndpoint(new URI(s))

  /**
    * Java API
    */
  def create(s: String, sslContext: Optional[SSLContext]): HttpEndpoint =
    HttpEndpoint(new URI(s), sslContext.asScala)
}