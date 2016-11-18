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

package org.squbs.env

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest._

class EnvironmentSpec extends TestKit(ActorSystem("EnvironmentSpec")) with FlatSpecLike
with Matchers with BeforeAndAfterEach{

  override def beforeEach() = {
    EnvironmentResolverRegistry(system).register(DummyProdEnvironmentResolver)
  }

  override def afterEach() = {
    EnvironmentResolverRegistry(system).environmentResolvers = List[EnvironmentResolver]()
  }

  "EnvironmentResolverRegistry" should "contain DummyProdEnvironmentResolver" in {
    EnvironmentResolverRegistry(system).environmentResolvers should have size 1
    EnvironmentResolverRegistry(system).environmentResolvers.head should be (DummyProdEnvironmentResolver)
  }

  it should "resolve the environment" in {
    EnvironmentResolverRegistry(system).resolve should be (PROD)
  }

  it should "give priority to resolvers in the reverse order of registration" in {
    EnvironmentResolverRegistry(system).register(DummyQAEnvironmentResolver)
    EnvironmentResolverRegistry(system).resolve should be (QA)
  }

  it should "try the chain of resolvers till the environment can be resolved" in {
    EnvironmentResolverRegistry(system).register(DummyNotResolveEnvironmentResolver)
    EnvironmentResolverRegistry(system).resolve should be (PROD)
  }

  it should "unregister a resolver" in {
    EnvironmentResolverRegistry(system).register(DummyQAEnvironmentResolver)
    EnvironmentResolverRegistry(system).resolve should be (QA)
    EnvironmentResolverRegistry(system).unregister("DummyQAEnvironmentResolver")
    EnvironmentResolverRegistry(system).resolve should be (PROD)
  }

}