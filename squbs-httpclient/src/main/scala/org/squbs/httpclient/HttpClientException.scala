/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the CONTRIBUTING file distributed with this work for
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
package org.squbs.httpclient

import spray.http.StatusCodes
import org.squbs.httpclient.env._

class HttpClientException(code: Int, message: String) extends RuntimeException(message) with Serializable

case class HttpClientMarkDownException(svcName: String, env: Environment = Default)
  extends HttpClientException(900, s"HttpClient:($svcName,$env) has been markdown!")

case class HttpClientExistException(svcName: String, env: Environment = Default)
  extends HttpClientException(901, s"HttpClient:($svcName,$env) has been registry!")

case class HttpClientNotExistException(svcName: String, env: Environment = Default)
  extends HttpClientException(902, s"HttpClient:($svcName,$env) hasn't been registry!")

case class HttpClientEndpointNotExistException(svcName: String, env: Environment = Default)
  extends HttpClientException(903, s"HttpClient:($svcName,$env) endpoint cannot be resolved!")

case class HttpClientConfigurationTypeException(svcName: String, env: Environment = Default)
  extends HttpClientException(904, s"HttpClient:($svcName,$env) configuration type error, configuration type should be org.squbs.httpclient.Configuration")

object HttpClientException {
  val httpClientMarkDownError = StatusCodes.registerCustom(900, "HttpClient has been markdown!", "HttpClient has been markdown!", false, false)
  val httpClientExistingError = StatusCodes.registerCustom(901, "HttpClient has been registry!", "HttpClient has been registry!", false, false)
  val httpClientNotExistingError = StatusCodes.registerCustom(902, "HttpClient hasn't been registry!", "HttpClient hasn't been registry!", false, false)
}
