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
package org.squbs.httpclient.env

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.squbs.httpclient.HttpClientTestKit
import org.squbs.httpclient.dummy.{DummyProdEnvironmentResolver, DummyPriorityEnvironmentResolver}
import org.scalatest._

class HttpClientEnvironmentSpec extends TestKit(ActorSystem("HttpClientEnvironmentSpec"))
  with FlatSpecLike with HttpClientTestKit with Matchers with BeforeAndAfterEach{

  override def beforeEach = {
    EnvironmentRegistry(system).register(DummyProdEnvironmentResolver)
  }

  override def afterEach = {
    clearHttpClient
  }

  "EnvironmentResolverRegistry" should "contain DummyProdEnvironmentResolver" in {
    EnvironmentRegistry(system).environmentResolvers.length should be (1)
    EnvironmentRegistry(system).environmentResolvers.head should be (DummyProdEnvironmentResolver)
  }

  "DummyProdEnvironmentResolver" should "return to the correct value" in {
    EnvironmentRegistry(system).resolve("abc") should be (PROD)
  }

  "Latter registry EnvironmentResolver" should "have high priority" in {
    EnvironmentRegistry(system).register(DummyPriorityEnvironmentResolver)
    EnvironmentRegistry(system).resolve("abc") should be (QA)
    EnvironmentRegistry(system).unregister("DummyPriorityEnvironmentResolver")
  }

  it should "fallback to the previous EnvironmentResolver" in {
    EnvironmentRegistry(system).register(DummyPriorityEnvironmentResolver)
    EnvironmentRegistry(system).resolve("test") should be (PROD)
    EnvironmentRegistry(system).unregister("DummyPriorityEnvironmentResolver")
  }

  "unregister EnvironmentResolver" should "have the correct behaviour" in {
    EnvironmentRegistry(system).register(DummyPriorityEnvironmentResolver)
    EnvironmentRegistry(system).resolve("abc") should be (QA)
    EnvironmentRegistry(system).unregister("DummyPriorityEnvironmentResolver")
    EnvironmentRegistry(system).resolve("abc") should be (PROD)
  }

}