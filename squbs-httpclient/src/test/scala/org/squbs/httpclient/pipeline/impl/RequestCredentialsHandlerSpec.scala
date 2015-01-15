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
package org.squbs.httpclient.pipeline.impl

import org.scalatest.{Matchers, FlatSpec}
import spray.http.{BasicHttpCredentials, HttpRequest, OAuth2BearerToken}

class RequestCredentialsHandlerSpec extends FlatSpec with Matchers {

  "RequestCredentialsHandler" should "support OAuth2BearerToken" in {
    val httpRequest = HttpRequest()
    val handler = RequestCredentialsHandler(OAuth2BearerToken("test123"))
    val updateHttpRequest = handler.processRequest(httpRequest)
    updateHttpRequest.headers.length should be (1)
    updateHttpRequest.headers.head.name should be ("Authorization")
    updateHttpRequest.headers.head.value should be ("Bearer test123")
  }

  "RequestCredentialsHandler" should "support BasicHttpCredentials" in {
    val httpRequest = HttpRequest()
    val handler = RequestCredentialsHandler(BasicHttpCredentials("username", "password"))
    val updateHttpRequest = handler.processRequest(httpRequest)
    updateHttpRequest.headers.length should be (1)
    updateHttpRequest.headers.head.name should be ("Authorization")
    updateHttpRequest.headers.head.value should be ("Basic dXNlcm5hbWU6cGFzc3dvcmQ=")
  }
}
