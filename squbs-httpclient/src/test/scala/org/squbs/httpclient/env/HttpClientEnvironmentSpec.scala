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
package org.squbs.httpclient.env

import org.squbs.httpclient.endpoint.EndpointRegistry
import org.squbs.httpclient.HttpClientFactory
import org.squbs.httpclient.dummy.{DummyProdEnvironmentResolver, DummyPriorityEnvironmentResolver}
import org.scalatest._

class HttpClientEnvironmentSpec extends FlatSpec with Matchers with BeforeAndAfterEach{

  override def beforeEach = {
    EnvironmentRegistry.register(DummyProdEnvironmentResolver)
  }

  override def afterEach = {
    EndpointRegistry.endpointResolvers.clear
    EnvironmentRegistry.environmentResolvers.clear
    HttpClientFactory.httpClientMap.clear
  }

  "EnvironmentResolverRegistry" should "contain DummyProdEnvironmentResolver" in {
    EnvironmentRegistry.environmentResolvers.length should be (1)
    EnvironmentRegistry.environmentResolvers.head should be (DummyProdEnvironmentResolver)
  }

  "DummyProdEnvironmentResolver" should "return to the correct value" in {
    EnvironmentRegistry.resolve("abc") should be (PROD)
  }

  "Latter registry EnvironmentResolver" should "have high priority" in {
    EnvironmentRegistry.register(DummyPriorityEnvironmentResolver)
    EnvironmentRegistry.resolve("abc") should be (QA)
    EnvironmentRegistry.unregister("DummyPriorityEnvironmentResolver")
  }
  
  it should "fallback to the previous EnvironmentResolver" in {
    EnvironmentRegistry.register(DummyPriorityEnvironmentResolver)
    EnvironmentRegistry.resolve("test") should be (PROD)
    EnvironmentRegistry.unregister("DummyPriorityEnvironmentResolver")
  }

  "unregister EnvironmentResolver" should "have the correct behaviour" in {
    EnvironmentRegistry.register(DummyPriorityEnvironmentResolver)
    EnvironmentRegistry.resolve("abc") should be (QA)
    EnvironmentRegistry.unregister("DummyPriorityEnvironmentResolver")
    EnvironmentRegistry.resolve("abc") should be (PROD)
  }

}
