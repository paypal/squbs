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
package org.squbs.httpclient.endpoint.impl

import org.scalatest.{BeforeAndAfterAll, Matchers, FlatSpec}
import org.squbs.httpclient.endpoint.{Endpoint, EndpointRegistry}
import org.squbs.httpclient.{HttpClientTestKit, Configuration}
import javax.net.ssl.SSLContext

class SimpleServiceEndpointResolverSpec extends FlatSpec with HttpClientTestKit with Matchers with BeforeAndAfterAll{

  override def afterAll = {
    clearHttpClient
  }

  "SimpleServiceEndpintResolver" should "have the correct behaviour" in {
    val simpleResolver = SimpleServiceEndpointResolver("simple", Map[String, Configuration](
      "http://localhost:8080" -> Configuration(),
      "https://localhost:8443" -> Configuration(sslContext = Some(SSLContext.getDefault))
    ))
    simpleResolver.name should be ("simple")
    EndpointRegistry.register(simpleResolver)
    EndpointRegistry.resolve("http://localhost:8080") should be (Some(Endpoint("http://localhost:8080")))
    EndpointRegistry.resolve("https://localhost:8443") should be (Some(Endpoint("https://localhost:8443", Configuration(sslContext = Some(SSLContext.getDefault)))))
    EndpointRegistry.resolve("notExisting") should be (None)
    EndpointRegistry.unregister(simpleResolver.name)
  }
}
