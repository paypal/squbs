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
import spray.http.{HttpHeader, HttpResponse}
import spray.http.HttpHeaders.RawHeader

class ResponseHeaderHandlerSpec extends FlatSpec with Matchers{

   "ResponseAddHeaderHandler" should "support to add HttpHeader in HttpRequest" in {
     val httpResponse = HttpResponse()
     val httpHeader = RawHeader("test-name", "test-value")
     val handler = ResponseAddHeaderHandler(httpHeader)
     val updateHttpResponse = handler.processResponse(httpResponse)
     updateHttpResponse.headers.length should be (1)
     updateHttpResponse.headers.head should be (httpHeader)
   }

   "ResponseRemoveHeaderHandler" should "support to remove HttpHeader in HttpRequest" in {
     val httpHeader1 = RawHeader("name1", "value1")
     val httpHeader2 = RawHeader("name2", "value2")
     val httpResponse = HttpResponse(headers = List[HttpHeader](httpHeader1, httpHeader2))
     val handler = ResponseRemoveHeaderHandler(httpHeader1)
     val updateHttpResponse = handler.processResponse(httpResponse)
     updateHttpResponse.headers.length should be (1)
     updateHttpResponse.headers.head should be (httpHeader2)
   }

   "ResponseUpdateHeaderHandler" should "support to update HttpHeader in HttpRequest" in {
     val httpHeader1 = RawHeader("name1", "value1")
     val httpHeader2 = RawHeader("name1", "value2")
     val httpResponse = HttpResponse(headers = List[HttpHeader](httpHeader1))
     val handler = ResponseUpdateHeaderHandler(httpHeader2)
     val updateHttpResponse = handler.processResponse(httpResponse)
     updateHttpResponse.headers.length should be (1)
     updateHttpResponse.headers.head should be (httpHeader2)
   }
 }
