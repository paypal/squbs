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

package org.squbs.httpclient.dummy

import org.squbs.httpclient.pipeline.impl.{RequestAddHeaderHandler, ResponseAddHeaderHandler}
import org.squbs.proxy.SimplePipelineConfig
import spray.http.HttpHeaders.RawHeader

object DummyRequestPipeline extends SimplePipelineConfig(
	Seq(new RequestAddHeaderHandler(RawHeader("req1-name", "req1-value"))), Seq.empty)

object DummyResponsePipeline extends SimplePipelineConfig(
	Seq.empty, Seq(new ResponseAddHeaderHandler(RawHeader("res1-name", "res1-value"))))

object DummyRequestResponsePipeline extends SimplePipelineConfig(
				Seq(new RequestAddHeaderHandler(RawHeader("req2-name", "req2-value"))),
				Seq(new ResponseAddHeaderHandler(RawHeader("res2-name", "res2-value")))
)
