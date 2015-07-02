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

import org.squbs.httpclient.env._

class HttpClientException(message: String) extends RuntimeException(message) with Serializable

case class HttpClientMarkDownException(svcName: String, env: Environment = Default)
  extends HttpClientException(s"HttpClient:($svcName,$env) has been marked down!")

case class HttpClientExistException(svcName: String, env: Environment = Default)
  extends HttpClientException(s"HttpClient:($svcName,$env) has been registered!")

case class HttpClientNotExistException(svcName: String, env: Environment = Default)
  extends HttpClientException(s"HttpClient:($svcName,$env) has not been registered!")

case class HttpClientEndpointNotExistException(svcName: String, env: Environment = Default)
  extends HttpClientException(s"HttpClient:($svcName,$env) endpoint cannot be resolved!")