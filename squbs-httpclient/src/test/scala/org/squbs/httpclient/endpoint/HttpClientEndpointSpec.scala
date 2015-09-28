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

package org.squbs.httpclient.endpoint

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest.{BeforeAndAfterEach, FlatSpecLike, Matchers}
import org.squbs.httpclient.dummy.DummyLocalhostResolver
import org.squbs.httpclient.env._
import org.squbs.httpclient.{HttpClientException, HttpClientTestKit}

class HttpClientEndpointSpec extends TestKit(ActorSystem("HttpClientEndpointSpec")) with FlatSpecLike
    with HttpClientTestKit with Matchers with BeforeAndAfterEach {

  implicit val _system = system
  override def afterEach() = {
    clearHttpClient()
  }

  "EndpointRegistry" should "contain DummyLocalhostResolver" in {
    val resolver = new DummyLocalhostResolver
    EndpointRegistry(system).register(resolver)
    EndpointRegistry(system).endpointResolvers should have size 1
    EndpointRegistry(system).endpointResolvers.head should be (resolver)
  }

  "EndpointRegistry register twice of the same resolver" should "contain once" in {
    val resolver = new DummyLocalhostResolver()
    EndpointRegistry(system).register(resolver)
    EndpointRegistry(system).register(resolver)
    EndpointRegistry(system).endpointResolvers should have size 1
    EndpointRegistry(system).endpointResolvers.head should be (resolver)
  }

  "EndpointRegistry unregister not existing resolver" should "be ignored" in {
    val resolver = new DummyLocalhostResolver()
    EndpointRegistry(system).register(resolver)
    EndpointRegistry(system).unregister("NotExistingResolver")
    EndpointRegistry(system).endpointResolvers should have size 1
    EndpointRegistry(system).endpointResolvers.head should be (resolver)
  }

  "DummyLocalhostResolver" should "be return to the correct value" in {
    EndpointRegistry(system).register(new DummyLocalhostResolver)
    val resolver = EndpointRegistry(system).route("abcService")
    resolver should not be None
    resolver.get.name should be ("DummyLocalhostResolver")
    resolver.get.resolve("abcService") should be (Some(Endpoint("http://localhost:8080")))
  }

  "DummyLocalhostResolver" should "be throw out HttpClientException if env isn't Dev" in {
    a[HttpClientException] should be thrownBy {
      EndpointRegistry(system).register(new DummyLocalhostResolver)
      EndpointRegistry(system).route("abcService", QA)
    }
  }

  "DummyLocalhostResolver" should "be return to the correct value if env is Dev" in {
    EndpointRegistry(system).register(new DummyLocalhostResolver)
    EndpointRegistry(system).route("abcService", DEV) should not be None
    EndpointRegistry(system).route("abcService", DEV).get.name should be ("DummyLocalhostResolver")
    EndpointRegistry(system).resolve("abcService", DEV) should be (Some(Endpoint("http://localhost:8080")))
  }

  "Latter registry EndpointResolver" should "have high priority" in {
    EndpointRegistry(system).register(new DummyLocalhostResolver)
    EndpointRegistry(system).register(new EndpointResolver {
      override def resolve(svcName: String, env: Environment = Default): Option[Endpoint] =
        Some(Endpoint("http://localhost:9090"))

      override def name: String = "override"
    })
    EndpointRegistry(system).endpointResolvers should have size 2
    EndpointRegistry(system).endpointResolvers.head should not be a [DummyLocalhostResolver]
    EndpointRegistry(system).endpointResolvers.head.name should be ("override")
    EndpointRegistry(system).route("abcService") should not be None
    EndpointRegistry(system).route("abcService").get.name should be ("override")
    EndpointRegistry(system).resolve("abcService") should be (Some(Endpoint("http://localhost:9090")))
  }

  "It" should "fallback to the previous EndpointResolver if latter one cannot be resolve" in {
    EndpointRegistry(system).register(new DummyLocalhostResolver)
    EndpointRegistry(system).register(new EndpointResolver {
      override def resolve(svcName: String, env: Environment = Default): Option[Endpoint] = {
        svcName match {
          case "unique" => Some(Endpoint("http://www.ebay.com"))
          case _ => None
        }
      }

      override def name: String = "unique"
    })
    EndpointRegistry(system).endpointResolvers should have size 2
    EndpointRegistry(system).route("abcService") should not be None
    EndpointRegistry(system).route("abcService").get.name should be ("DummyLocalhostResolver")
    EndpointRegistry(system).route("unique") should not be None
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
    EndpointRegistry(system).register(new DummyLocalhostResolver)

    EndpointRegistry(system).endpointResolvers should have size 2
    EndpointRegistry(system).endpointResolvers.head shouldBe a [DummyLocalhostResolver]
    EndpointRegistry(system).resolve("unique") should be (Some(Endpoint("http://localhost:8080")))
    EndpointRegistry(system).unregister("DummyLocalhostResolver")
    EndpointRegistry(system).endpointResolvers should have size 1
    EndpointRegistry(system).resolve("unique") should be (Some(Endpoint("http://www.ebay.com")))
  }
}
