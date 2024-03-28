/*
 * Copyright 2017 PayPal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.squbs.resolver

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.TestKit
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import java.lang.management.ManagementFactory
import javax.management.openmbean.CompositeData
import javax.management.{InstanceNotFoundException, ObjectName}
import scala.language.postfixOps

class ResolverRegistryJMXSpec extends TestKit(ActorSystem("ResolverRegistryJMXSpec"))
with AnyFlatSpecLike with Matchers {

  private val oName = ObjectName.getInstance(s"org.squbs.configuration.${system.name}:type=ResolverRegistry")

  it should "not be registered at all when not accessed" in {
    an [InstanceNotFoundException] should be thrownBy {
      ManagementFactory.getPlatformMBeanServer.getAttribute(oName, "ResolverInfo").
        asInstanceOf[Array[CompositeData]]
    }
  }

  it should "not list any endpoint resolvers when not registered" in {
    ResolverRegistry(system)
    val resolvers = ManagementFactory.getPlatformMBeanServer.getAttribute(oName, "ResolverInfo").
      asInstanceOf[Array[CompositeData]]

    resolvers should have length 0
  }

  it should "list all resolvers with position" in {

    val dummyLocalhostResolver = new DummyLocalhostResolver
    val dummyServiceEndpointResolver = new DummyServiceResolver

    ResolverRegistry(system).register(dummyLocalhostResolver)
    ResolverRegistry(system).register(dummyServiceEndpointResolver)

    val resolvers = ManagementFactory.getPlatformMBeanServer.getAttribute(oName, "ResolverInfo").
      asInstanceOf[Array[CompositeData]]

    resolvers should have length 2
    resolvers(0).get("position") shouldBe 0
    resolvers(0).get("name") shouldEqual dummyServiceEndpointResolver.name
    resolvers(0).get("className") shouldEqual dummyServiceEndpointResolver.getClass.getName

    resolvers(1).get("position") shouldBe 1
    resolvers(1).get("name") shouldEqual dummyLocalhostResolver.name
    resolvers(1).get("className") shouldEqual dummyLocalhostResolver.getClass.getName
  }
}
