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

import org.squbs.httpclient.pipeline.RequestPipelineHandler
import spray.client.pipelining
import spray.http.HttpHeader

case class RequestAddHeaderHandler(httpHeader: HttpHeader) extends RequestPipelineHandler {
  override def processRequest: pipelining.RequestTransformer = { httpRequest =>
      val originalHeaders = httpRequest.headers
      httpRequest.copy(headers = (originalHeaders :+ httpHeader))
  }
}

case class RequestRemoveHeaderHandler(httpHeader: HttpHeader) extends RequestPipelineHandler {
  override def processRequest: pipelining.RequestTransformer = { httpRequest =>
    val originalHeaders = httpRequest.headers.groupBy[Boolean](_.name == httpHeader.name)
    httpRequest.copy(headers = (originalHeaders.get(false).getOrElse(List.empty[HttpHeader])))
  }
}

case class RequestUpdateHeaderHandler(httpHeader: HttpHeader) extends RequestPipelineHandler {
  override def processRequest: pipelining.RequestTransformer = { httpRequest =>
    val originalHeaders = httpRequest.headers.groupBy[Boolean](_.name == httpHeader.name)
    httpRequest.copy(headers = (originalHeaders.get(false).getOrElse(List.empty[HttpHeader]) :+ httpHeader))
  }
}
