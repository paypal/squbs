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

package org.squbs.httpclient.japi


import org.scalatest.{FlatSpecLike, Matchers}
import org.squbs.httpclient.Configuration
import org.squbs.httpclient.endpoint.Endpoint


class SimpleProcessorSpec extends FlatSpecLike with Matchers {

  "ConfigurationFactory" should "work" in {
    ConfigurationFactory.create() should be(Configuration())
  }

  "EndpointFactory" should "work" in {
    EndpointFactory.create("/test") should be(Endpoint("/test"))
  }
}
