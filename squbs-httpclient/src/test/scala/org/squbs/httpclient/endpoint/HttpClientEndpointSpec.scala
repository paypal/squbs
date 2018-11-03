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
package org.squbs.httpclient.endpoint

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest.{FlatSpecLike, BeforeAndAfterEach, FlatSpec, Matchers}
import org.squbs.httpclient.dummy.DummyLocalhostResolver
import org.squbs.httpclient.env._
import org.squbs.httpclient.{HttpClientTestKit, HttpClientException}

class HttpClientEndpointSpec extends TestKit(ActorSystem("HttpClientEndpointSpec")) with FlatSpecLike with HttpClientTestKit with Matchers with BeforeAndAfterEach{

  override def afterEach = {
    clearHttpClient
  }

  "EndpointRegistry" should "contain DummyLocalhostResolver" in {
    EndpointRegistry(system).register(DummyLocalhostResolver)
    EndpointRegistry(system).endpointResolvers.length should be (1)
    EndpointRegistry(system).endpointResolvers.head should be (DummyLocalhostResolver)
  }

  "EndpointRegistry register twice of the same resolver" should "contain once" in {
    EndpointRegistry(system).register(DummyLocalhostResolver)
    EndpointRegistry(system).register(DummyLocalhostResolver)
    EndpointRegistry(system).endpointResolvers.length should be (1)
    EndpointRegistry(system).endpointResolvers.head should be (DummyLocalhostResolver)
  }

  "EndpointRegistry unregister not existing resolver" should "be ignored" in {
    EndpointRegistry(system).register(DummyLocalhostResolver)
    EndpointRegistry(system).unregister("NotExistingResolver")
    EndpointRegistry(system).endpointResolvers.length should be (1)
    EndpointRegistry(system).endpointResolvers.head should be (DummyLocalhostResolver)
  }

  "DummyLocalhostResolver" should "be return to the correct value" in {
    EndpointRegistry(system).register(DummyLocalhostResolver)
    EndpointRegistry(system).route("abcService") should not be (None)
    EndpointRegistry(system).route("abcService").get.name should be ("DummyLocalhostResolver")
    EndpointRegistry(system).route("abcService").get.resolve("abcService") should be (Some(Endpoint("http://localhost:8080")))
  }

  "DummyLocalhostResolver" should "be throw out HttpClientException if env isn't Dev" in {
    a[HttpClientException] should be thrownBy {
      EndpointRegistry(system).register(DummyLocalhostResolver)
      EndpointRegistry(system).route("abcService", QA)
    }
  }

  "DummyLocalhostResolver" should "be return to the correct value if env is Dev" in {
    EndpointRegistry(system).register(DummyLocalhostResolver)
    EndpointRegistry(system).route("abcService", DEV) should not be (None)
    EndpointRegistry(system).route("abcService", DEV).get.name should be ("DummyLocalhostResolver")
    EndpointRegistry(system).resolve("abcService", DEV) should be (Some(Endpoint("http://localhost:8080")))
  }

  "Latter registry EndpointResolver" should "have high priority" in {
    EndpointRegistry(system).register(DummyLocalhostResolver)
    EndpointRegistry(system).register(new EndpointResolver {
      override def resolve(svcName: String, env: Environment = Default): Option[Endpoint] = Some(Endpoint("http://localhost:9090"))

      override def name: String = "override"
    })
    EndpointRegistry(system).endpointResolvers.length should be (2)
    EndpointRegistry(system).endpointResolvers.head should not be (DummyLocalhostResolver)
    EndpointRegistry(system).endpointResolvers.head.name should be ("override")
    EndpointRegistry(system).route("abcService") should not be (None)
    EndpointRegistry(system).route("abcService").get.name should be ("override")
    EndpointRegistry(system).resolve("abcService") should be (Some(Endpoint("http://localhost:9090")))
  }

  "It" should "fallback to the previous EndpointResolver if latter one cannot be resolve" in {
    EndpointRegistry(system).register(DummyLocalhostResolver)
    EndpointRegistry(system).register(new EndpointResolver {
      override def resolve(svcName: String, env: Environment = Default): Option[Endpoint] = {
        svcName match {
          case "unique" => Some(Endpoint("http://www.ebay.com"))
          case _ => None
        }
      }

      override def name: String = "unique"
    })
    EndpointRegistry(system).endpointResolvers.length should be (2)
    EndpointRegistry(system).route("abcService") should not be (None)
    EndpointRegistry(system).route("abcService").get.name should be ("DummyLocalhostResolver")
    EndpointRegistry(system).route("unique") should not be (None)
    EndpointRegistry(system).route("unique").get.name should be ("unique")
    EndpointRegistry(system).resolve("abcService") should be (Some(Endpoint("http://localhost:8080")))
    EndpointRegistry(system).resolve("unique") should be (Some(Endpoint("http://www.ebay.com")))
  }

  "unregister EndpointResolver" should "have the correct behaviour" in {
    EndpointRegistry(system).register(new EndpointResolver {
      override def resolve(svcName: String, env: Environment = Default): Option[Endpoint] = {
        svcName match {
          case "unique" => Some(Endpoint("http://www.ebay.com"))
          case _ => None
        }
      }

      override def name: String = "unique"
    })
    EndpointRegistry(system).register(DummyLocalhostResolver)

    EndpointRegistry(system).endpointResolvers.length should be (2)
    EndpointRegistry(system).endpointResolvers.head should be (DummyLocalhostResolver)
    EndpointRegistry(system).resolve("unique") should be (Some(Endpoint("http://localhost:8080")))
    EndpointRegistry(system).unregister("DummyLocalhostResolver")
    EndpointRegistry(system).endpointResolvers.length should be (1)
    EndpointRegistry(system).resolve("unique") should be (Some(Endpoint("http://www.ebay.com")))
  }
}
