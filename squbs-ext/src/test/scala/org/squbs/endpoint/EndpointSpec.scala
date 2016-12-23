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

package org.squbs.endpoint

import javax.net.ssl.SSLContext

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest.OptionValues._
import org.scalatest.{BeforeAndAfterEach, FlatSpecLike, Matchers}
import org.squbs.env._

import scala.language.postfixOps

class EndpointSpec extends TestKit(ActorSystem("EndpointSpec")) with FlatSpecLike with Matchers with BeforeAndAfterEach {

  override def afterEach(): Unit = {
    EndpointTestHelper.clearRegistries(system)
    EnvTestHelper.clearRegistries(system)
  }

  "EndpointResolverRegistry" should "register a resolver" in {
    val resolver = new DummyLocalhostResolver
    EndpointResolverRegistry(system).register(resolver)
    EndpointResolverRegistry(system).endpointResolvers should have size 1
    EndpointResolverRegistry(system).endpointResolvers.head should be (resolver)
  }

  "EndpointResolverRegistry" should "register a resolver lambda" in {
    EndpointResolverRegistry(system).register("RogueResolver", (_, _) => Some(Endpoint("http://myrogueservice.com")))
    EndpointResolverRegistry(system).endpointResolvers should have size 1
    EndpointResolverRegistry(system).endpointResolvers.head.name shouldBe "RogueResolver"
  }

  "EndpointResolverRegistry" should "register and resolve with a SSL resolver" in {
    EndpointResolverRegistry(system).register("RogueResolver", (_, _) =>
      Some(Endpoint("https://myrogueservice.com", Some(SSLContext.getDefault))))
    EndpointResolverRegistry(system).endpointResolvers should have size 1
    EndpointResolverRegistry(system).endpointResolvers.head.name shouldBe "RogueResolver"
    val resolver = EndpointResolverRegistry(system).findResolver("abcService")
    resolver shouldBe 'defined
    resolver.value.name shouldBe "RogueResolver"
    val endpointOption = resolver.value.resolve("abcService")
    endpointOption map { _.uri.toString } shouldBe Some("https://myrogueservice.com")
    endpointOption flatMap { _.sslContext } shouldBe Some(SSLContext.getDefault)
  }
  it should "not register a resolver twice" in {
    val resolver = new DummyLocalhostResolver()
    EndpointResolverRegistry(system).register(resolver)
    EndpointResolverRegistry(system).register(resolver)
    EndpointResolverRegistry(system).endpointResolvers should have size 1
    EndpointResolverRegistry(system).endpointResolvers.head should be (resolver)
  }

  it should "skip unregistering a non-existing resolver" in {
    val resolver = new DummyLocalhostResolver()
    EndpointResolverRegistry(system).register(resolver)
    EndpointResolverRegistry(system).unregister("NotExistingResolver")
    EndpointResolverRegistry(system).endpointResolvers should have size 1
    EndpointResolverRegistry(system).endpointResolvers.head should be (resolver)
  }

  it should "resolve the endpoint" in {
    EndpointResolverRegistry(system).register(new DummyLocalhostResolver)
    val resolver = EndpointResolverRegistry(system).findResolver("abcService")
    resolver should be ('defined)
    resolver.value.name should be ("DummyLocalhostResolver")
    resolver.value.resolve("abcService") should be (Some(Endpoint("http://localhost:8080")))
  }

  it should "resolve the endpoint with resolver lambda" in {
    EndpointResolverRegistry(system).register("RogueResolver", (_, _) => Some(Endpoint("http://myrogueservice.com")))
    val resolver = EndpointResolverRegistry(system).findResolver("abcService")
    resolver should be ('defined)
    resolver.value.name should be ("RogueResolver")
    resolver.value.resolve("abcService") should be (Some(Endpoint("http://myrogueservice.com")))
  }

  it should "propagate exceptions from EndpointResolvers" in {
    a[RuntimeException] should be thrownBy {
      EndpointResolverRegistry(system).register(new DummyLocalhostResolver)
      EndpointResolverRegistry(system).findResolver("abcService", QA)
    }
  }

  it should "return a None if service is not resolvable" in {
    EndpointResolverRegistry(system).register("AlwaysDenyResolver", (_, _) => None)
    EndpointResolverRegistry(system).resolve("abcService") shouldBe None
  }

  it should "resolve the endpoint when Environment is provided" in {
    EndpointResolverRegistry(system).register(new DummyLocalhostResolver)
    EndpointResolverRegistry(system).findResolver("abcService", DEV) should be ('defined)
    EndpointResolverRegistry(system).findResolver("abcService", DEV).value.name should be ("DummyLocalhostResolver")
    EndpointResolverRegistry(system).resolve("abcService", DEV) should be (Some(Endpoint("http://localhost:8080")))
  }

  it should "should give priority to resolvers in reverse order of registration" in {
    EndpointResolverRegistry(system).register(new DummyLocalhostResolver)
    EndpointResolverRegistry(system).register(new EndpointResolver {
      override def resolve(svcName: String, env: Environment = Default): Option[Endpoint] =
        Some(Endpoint("http://localhost:9090"))

      override def name: String = "override"
    })
    EndpointResolverRegistry(system).endpointResolvers should have size 2
    EndpointResolverRegistry(system).endpointResolvers.head should not be a [DummyLocalhostResolver]
    EndpointResolverRegistry(system).endpointResolvers.head.name should be ("override")
    EndpointResolverRegistry(system).findResolver("abcService") should be ('defined)
    EndpointResolverRegistry(system).findResolver("abcService").value.name should be ("override")
    EndpointResolverRegistry(system).resolve("abcService") should be (Some(Endpoint("http://localhost:9090")))
  }

  it should "fallback to the previous EndpointResolver if latter one cannot be resolve" in {
    EndpointResolverRegistry(system).register(new DummyLocalhostResolver)
    EndpointResolverRegistry(system).register(new EndpointResolver {
      override def resolve(svcName: String, env: Environment = Default): Option[Endpoint] = {
        svcName match {
          case "unique" => Some(Endpoint("http://www.ebay.com"))
          case _ => None
        }
      }

      override def name: String = "unique"
    })
    EndpointResolverRegistry(system).endpointResolvers should have size 2
    EndpointResolverRegistry(system).findResolver("abcService") should be ('defined)
    EndpointResolverRegistry(system).findResolver("abcService").value.name should be ("DummyLocalhostResolver")
    EndpointResolverRegistry(system).findResolver("unique") should be ('defined)
    EndpointResolverRegistry(system).findResolver("unique").value.name should be ("unique")
    EndpointResolverRegistry(system).resolve("abcService") should be (Some(Endpoint("http://localhost:8080")))
    EndpointResolverRegistry(system).resolve("unique") should be (Some(Endpoint("http://www.ebay.com")))
  }

  it should "unregister a resolver" in {
    EndpointResolverRegistry(system).register(new EndpointResolver {
      override def resolve(svcName: String, env: Environment = Default): Option[Endpoint] = {
        svcName match {
          case "unique" => Some(Endpoint("http://www.ebay.com"))
          case _ => None
        }
      }

      override def name: String = "unique"
    })
    EndpointResolverRegistry(system).register(new DummyLocalhostResolver)

    EndpointResolverRegistry(system).endpointResolvers should have size 2
    EndpointResolverRegistry(system).endpointResolvers.head shouldBe a [DummyLocalhostResolver]
    EndpointResolverRegistry(system).resolve("unique") should be (Some(Endpoint("http://localhost:8080")))
    EndpointResolverRegistry(system).unregister("DummyLocalhostResolver")
    EndpointResolverRegistry(system).endpointResolvers should have size 1
    EndpointResolverRegistry(system).resolve("unique") should be (Some(Endpoint("http://www.ebay.com")))
  }
}
