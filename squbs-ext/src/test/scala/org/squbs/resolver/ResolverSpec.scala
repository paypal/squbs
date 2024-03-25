/*
 *  Copyright 2017 PayPal
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

package org.squbs.resolver

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.TestKit
import org.scalatest.BeforeAndAfterEach
import org.scalatest.OptionValues._
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.env._

import java.net.{URI, URL}
import scala.language.{existentials, postfixOps}

class ResolverSpec extends TestKit(ActorSystem("ResolverSpec"))
    with AnyFlatSpecLike with Matchers with BeforeAndAfterEach {

  override def afterEach(): Unit = {
    ResolverTestHelper.clearRegistries(system)
    EnvTestHelper.clearRegistries(system)
  }

  "ResolverRegistry" should "register a resolver" in {
    val resolver = new DummyLocalhostResolver
    ResolverRegistry(system).register(resolver)
    ResolverRegistry(system).resolvers should have size 1
    ResolverRegistry(system).resolvers.head._2 shouldBe resolver
  }

  "ResolverRegistry" should "register a resolver lambda" in {
    ResolverRegistry(system).register[URI]("RogueResolver")
      { (_, _) => Some(URI.create("http://myrogueservice.com")) }
    ResolverRegistry(system).resolvers should have size 1
    val (tpe, resolver) = ResolverRegistry(system).resolvers.head
    tpe shouldBe classOf[URI]
    resolver.name shouldBe "RogueResolver"
  }

  it should "not register a resolver twice" in {
    val resolver = new DummyLocalhostResolver()
    ResolverRegistry(system).register[URI](resolver)
    ResolverRegistry(system).register[URI](resolver)
    ResolverRegistry(system).resolvers should have size 1
    ResolverRegistry(system).resolvers.head._2 shouldBe resolver
  }

  it should "skip unregistering a non-existing resolver" in {
    val resolver = new DummyLocalhostResolver()
    ResolverRegistry(system).register[URI](resolver)
    ResolverRegistry(system).unregister("NotExistingResolver")
    ResolverRegistry(system).resolvers should have size 1
    ResolverRegistry(system).resolvers.head._2 shouldBe resolver
  }

  it should "resolve the endpoint" in {
    ResolverRegistry(system).register[URI](new DummyLocalhostResolver)
    val resolverOption = ResolverRegistry(system).findResolver[URI]("abcService")
    resolverOption shouldBe defined
    resolverOption.value.name shouldBe "DummyLocalhostResolver"

    val endpointOption = resolverOption flatMap { _.resolve("abcService") }

    endpointOption shouldBe Some(URI.create("http://localhost:8080"))
  }

  it should "resolve the endpoint with resolver lambda" in {
    ResolverRegistry(system).register[URI]("RogueResolver")
      { (_, _) => Some(URI.create("http://myrogueservice.com")) }
    val resolverOption = ResolverRegistry(system).findResolver[URI]("abcService")
    resolverOption shouldBe defined
    resolverOption.value.name shouldBe "RogueResolver"

    val endpointOption = resolverOption flatMap { _.resolve("abcService") }

    endpointOption shouldBe Some(URI.create("http://myrogueservice.com"))
  }

  it should "propagate exceptions from EndpointResolvers" in {
    a[RuntimeException] shouldBe thrownBy {
      ResolverRegistry(system).register[URI](new DummyLocalhostResolver)
      ResolverRegistry(system).findResolver[URI]("abcService", QA)
    }
  }

  it should "return a None if service is not resolvable" in {
    ResolverRegistry(system).register[URI]("AlwaysDenyResolver") { (_, _) => None }
    ResolverRegistry(system).resolve[URI]("abcService") shouldBe None
  }

  it should "return a None if resolve type is incompatible" in {
    ResolverRegistry(system).register[URI]("URIResolver") { (_, _) => Some(URI.create("https://github.com/")) }
    ResolverRegistry(system).resolve[URI]("github") shouldBe Some(URI.create("https://github.com/"))
    ResolverRegistry(system).resolve[URL]("github") shouldBe None
    ResolverRegistry(system).findResolver[URL]("URIResolver") shouldBe None
  }

  it should "resolve the endpoint when Environment is provided" in {
    ResolverRegistry(system).register[URI](new DummyLocalhostResolver)
    ResolverRegistry(system).findResolver[URI]("abcService", DEV) shouldBe defined
    ResolverRegistry(system).findResolver[URI]("abcService", DEV).value.name shouldBe "DummyLocalhostResolver"
    ResolverRegistry(system).resolve[URI]("abcService", DEV) shouldBe Some(URI.create("http://localhost:8080"))
  }

  it should "should give priority to resolvers in reverse order of registration" in {
    ResolverRegistry(system).register[URI](new DummyLocalhostResolver)
    ResolverRegistry(system).register[URI](new Resolver[URI] {
      override def resolve(svcName: String, env: Environment = Default): Option[URI] =
          Some(URI.create("http://localhost:9090"))

      override def name: String = "override"
    })
    ResolverRegistry(system).resolvers should have size 2
    val (_, resolver) = ResolverRegistry(system).resolvers.head
    resolver should not be a [DummyLocalhostResolver]
    resolver.name shouldBe "override"
    ResolverRegistry(system).findResolver[URI]("abcService") shouldBe defined
    ResolverRegistry(system).findResolver[URI]("abcService").value.name shouldBe "override"
    ResolverRegistry(system).resolve[URI]("abcService") shouldBe Some(URI.create("http://localhost:9090"))
  }

  it should "fallback to the previous EndpointResolver if latter one cannot be resolve" in {
    ResolverRegistry(system).register[URI](new DummyLocalhostResolver)
    ResolverRegistry(system).register[URI](new Resolver[URI] {
      override def resolve(svcName: String, env: Environment = Default): Option[URI] = {
        svcName match {
          case "unique" => Some(URI.create("http://www.ebay.com"))
          case _ => None
        }
      }

      override def name: String = "unique"
    })
    ResolverRegistry(system).resolvers should have size 2
    ResolverRegistry(system).findResolver[URI]("abcService") shouldBe defined
    ResolverRegistry(system).findResolver[URI]("abcService").value.name shouldBe "DummyLocalhostResolver"
    ResolverRegistry(system).findResolver[URI]("unique") shouldBe defined
    ResolverRegistry(system).findResolver[URI]("unique").value.name shouldBe "unique"
    ResolverRegistry(system).resolve[URI]("abcService") shouldBe Some(URI.create("http://localhost:8080"))
    ResolverRegistry(system).resolve[URI]("unique") shouldBe Some(URI.create("http://www.ebay.com"))
  }

  it should "unregister a resolver" in {
    ResolverRegistry(system).register[URI](new Resolver[URI] {
      override def resolve(svcName: String, env: Environment = Default): Option[URI] = {
        svcName match {
          case "unique" => Some(URI.create("http://www.ebay.com"))
          case _ => None
        }
      }

      override def name: String = "unique"
    })
    ResolverRegistry(system).register[URI](new DummyLocalhostResolver)

    ResolverRegistry(system).resolvers should have size 2
    ResolverRegistry(system).resolvers.head._2 shouldBe a [DummyLocalhostResolver]
    ResolverRegistry(system).resolve[URI]("unique") shouldBe Some(URI.create("http://localhost:8080"))
    ResolverRegistry(system).unregister("DummyLocalhostResolver")
    ResolverRegistry(system).resolvers should have size 1
    ResolverRegistry(system).resolve[URI]("unique") shouldBe Some(URI.create("http://www.ebay.com"))
  }
}
