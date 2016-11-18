/*
 * Copyright 2015 PayPal
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

package org.squbs.endpoint

import java.lang.management.ManagementFactory
import javax.management.{InstanceNotFoundException, ObjectName}
import javax.management.openmbean.CompositeData

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest.{FlatSpecLike, Matchers}

import scala.language.postfixOps

class EndpointResolverRegistryJMXSpec extends TestKit(ActorSystem("EndpointResolverRegistryJMXSpec"))
with FlatSpecLike with Matchers {

  val oName = ObjectName.getInstance(s"org.squbs.configuration.${system.name}:type=EndpointResolverRegistry")

  it should "not be registered at all when not accessed" in {
    an [InstanceNotFoundException] should be thrownBy {
      ManagementFactory.getPlatformMBeanServer.getAttribute(oName, "EndpointResolverInfo").
        asInstanceOf[Array[CompositeData]]
    }
  }

  it should "not list any endpoint resolvers when not registered" in {
    EndpointResolverRegistry(system)
    val resolvers = ManagementFactory.getPlatformMBeanServer.getAttribute(oName, "EndpointResolverInfo").
      asInstanceOf[Array[CompositeData]]

    resolvers should have length 0
  }

  it should "list all endpoint resolvers with position" in {

    val dummyLocalhostResolver = new DummyLocalhostResolver
    val dummyServiceEndpointResolver = new DummyServiceEndpointResolver

    EndpointResolverRegistry(system).register(dummyLocalhostResolver)
    EndpointResolverRegistry(system).register(dummyServiceEndpointResolver)

    val resolvers = ManagementFactory.getPlatformMBeanServer.getAttribute(oName, "EndpointResolverInfo").
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
