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

package org.squbs.env

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.TestKit
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import java.lang.management.ManagementFactory
import javax.management.openmbean.CompositeData
import javax.management.{InstanceNotFoundException, ObjectName}
import scala.language.postfixOps

class EnvironmentResolverRegistryJMXSpec extends TestKit(ActorSystem("EnvironmentResolverRegistryJMXSpec"))
with AnyFlatSpecLike with Matchers {

  val oName = ObjectName.getInstance(s"org.squbs.configuration.${system.name}:type=EnvironmentResolverRegistry")

  it should "not be registered if not accessed at all" in {
    an [InstanceNotFoundException] should be thrownBy {
      ManagementFactory.getPlatformMBeanServer.getAttribute(oName, "EnvironmentResolverInfo").
        asInstanceOf[Array[CompositeData]]
    }
  }

  it should "not list any environment resolvers when not registered but accessed" in {
    EnvironmentResolverRegistry(system).resolve
    val resolvers = ManagementFactory.getPlatformMBeanServer.getAttribute(oName, "EnvironmentResolverInfo").
      asInstanceOf[Array[CompositeData]]

    resolvers should have length 0
  }

  it should "list all environment resolvers with position" in {
    EnvironmentResolverRegistry(system).register(DummyProdEnvironmentResolver)
    EnvironmentResolverRegistry(system).register(DummyQAEnvironmentResolver)

    val resolvers = ManagementFactory.getPlatformMBeanServer.getAttribute(oName, "EnvironmentResolverInfo").
      asInstanceOf[Array[CompositeData]]

    resolvers should have length 2
    resolvers(0).get("position") shouldBe 0
    resolvers(0).get("name") shouldEqual DummyQAEnvironmentResolver.name
    resolvers(0).get("className") shouldEqual DummyQAEnvironmentResolver.getClass.getName

    resolvers(1).get("position") shouldBe 1
    resolvers(1).get("name") shouldEqual DummyProdEnvironmentResolver.name
    resolvers(1).get("className") shouldEqual DummyProdEnvironmentResolver.getClass.getName
  }
}
