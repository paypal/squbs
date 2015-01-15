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
import spray.http.{HttpHeader, HttpRequest}
import spray.http.HttpHeaders.RawHeader

class RequestHeaderHandlerSpec extends FlatSpec with Matchers{

  "RequestAddHeaderHandler" should "support to add HttpHeader in HttpRequest" in {
    val httpRequest = HttpRequest()
    val httpHeader = RawHeader("test-name", "test-value")
    val handler = RequestAddHeaderHandler(httpHeader)
    val updateHttpRequest = handler.processRequest(httpRequest)
    updateHttpRequest.headers.length should be (1)
    updateHttpRequest.headers.head should be (httpHeader)
  }

  "RequestRemoveHeaderHandler" should "support to remove HttpHeader in HttpRequest" in {
    val httpHeader1 = RawHeader("name1", "value1")
    val httpHeader2 = RawHeader("name2", "value2")
    val httpRequest = HttpRequest(headers = List[HttpHeader](httpHeader1, httpHeader2))
    val handler = RequestRemoveHeaderHandler(httpHeader1)
    val updateHttpRequest = handler.processRequest(httpRequest)
    updateHttpRequest.headers.length should be (1)
    updateHttpRequest.headers.head should be (httpHeader2)
  }

  "RequestUpdateHeaderHandler" should "support to update HttpHeader in HttpRequest" in {
    val httpHeader1 = RawHeader("name1", "value1")
    val httpHeader2 = RawHeader("name1", "value2")
    val httpRequest = HttpRequest(headers = List[HttpHeader](httpHeader1))
    val handler = RequestUpdateHeaderHandler(httpHeader2)
    val updateHttpRequest = handler.processRequest(httpRequest)
    updateHttpRequest.headers.length should be (1)
    updateHttpRequest.headers.head should be (httpHeader2)
  }
}
