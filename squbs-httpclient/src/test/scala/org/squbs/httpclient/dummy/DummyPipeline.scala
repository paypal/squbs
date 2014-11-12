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
package org.squbs.httpclient.dummy

import org.squbs.httpclient.pipeline.Pipeline
import spray.client.pipelining._
import org.squbs.httpclient.pipeline.impl.{ResponseAddHeaderHandler, RequestAddHeaderHandler}
import spray.http.HttpHeaders.RawHeader

object DummyRequestPipeline extends Pipeline {
  override def requestPipelines: Seq[RequestTransformer] = Seq[RequestTransformer](RequestAddHeaderHandler(RawHeader("req1-name", "req1-value")).processRequest)
  override def responsePipelines: Seq[ResponseTransformer] = Seq.empty[ResponseTransformer]
}

object DummyResponsePipeline extends Pipeline {
  override def requestPipelines: Seq[RequestTransformer] = Seq.empty[RequestTransformer]
  override def responsePipelines: Seq[ResponseTransformer] = Seq[ResponseTransformer](ResponseAddHeaderHandler(RawHeader("res1-name", "res1-value")).processResponse)
}

object DummyRequestResponsePipeline extends Pipeline {
  override def requestPipelines: Seq[RequestTransformer] = Seq[RequestTransformer](RequestAddHeaderHandler(RawHeader("req2-name", "req2-value")).processRequest)
  override def responsePipelines: Seq[ResponseTransformer] = Seq[ResponseTransformer](ResponseAddHeaderHandler(RawHeader("res2-name", "res2-value")).processResponse)
}
