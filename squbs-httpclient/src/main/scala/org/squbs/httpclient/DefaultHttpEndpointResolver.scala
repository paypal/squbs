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

package org.squbs.httpclient

import java.net.URI

import com.typesafe.scalalogging.LazyLogging
import org.squbs.env.Environment
import org.squbs.resolver.Resolver

import scala.util.Try

/**
  * A [[Resolver]] that resolves a valid HTTP [[URI]] [[String]] to an [[HttpEndpoint]].
  */
class DefaultHttpEndpointResolver extends Resolver[HttpEndpoint] with LazyLogging {

  override def name: String = getClass.getName

  override def resolve(name: String, env: Environment): Option[HttpEndpoint] = {
    Try(URI.create(name)).toOption match {
      case Some(uri) if uri.getScheme == "http" || uri.getScheme == "https" => Some(HttpEndpoint(uri))
      case _ =>
        logger.debug(s"Could not resolve to an HttpEndpoint.  Invalid http URI: $name")
        None
    }
  }
}
