/*
 *  Copyright 2017 PayPal
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
import java.util.Optional
import javax.net.ssl.SSLContext

import akka.http.impl.model.JavaUri
import akka.http.javadsl
import akka.http.scaladsl.model.Uri
import com.typesafe.config.Config

import scala.compat.java8.OptionConverters._

/**
  * End point type for HTTP/HTTPS.
  * @param uri The HTTP request URI.
  * @param sslContext The SSL context, if any.
  */
case class HttpEndpoint(uri: Uri, sslContext: Option[SSLContext] = None, config: Option[Config] = None)

/**
  * Creators for HttpEndpoint.
  */
object HttpEndpoint {
  def apply(s: String): HttpEndpoint = HttpEndpoint(Uri(s))

  def apply(s: String, sslContext: Option[SSLContext], config: Option[Config]): HttpEndpoint =
    HttpEndpoint(Uri(s), sslContext, config)

  /**
    * Java API
    */
  def create(s: String): HttpEndpoint = HttpEndpoint(Uri(s))

  /**
    * Java API
    */
  def create(s: String, sslContext: Optional[SSLContext], config: Optional[Config]): HttpEndpoint =
    HttpEndpoint(Uri(s), sslContext.asScala, config.asScala)

  /**
   * Java API
   */
  def create(uri: javadsl.model.Uri, sslContext: Optional[SSLContext], config: Optional[Config]): HttpEndpoint = {
    uri match {
      case JavaUri(sUri) => HttpEndpoint(sUri, sslContext.asScala, config.asScala)
      case _ => throw new IllegalArgumentException("Illegitimately created uri.")
    }
  }
}
