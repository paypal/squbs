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
import org.squbs.httpclient.dummy.{DummyNotResolveEnvironmentResolver, DummyProdEnvironmentResolver, DummyQAEnvironmentResolver}
import org.scalatest._

class EnvironmentSpec extends TestKit(ActorSystem("EnvironmentSpec")) with FlatSpecLike
with Matchers with BeforeAndAfterEach{

  override def beforeEach() = {
    EnvironmentRegistry(system).register(DummyProdEnvironmentResolver)
  }

  override def afterEach() = {
    EnvironmentRegistry(system).environmentResolvers = List[EnvironmentResolver]()
  }

  "EnvironmentResolverRegistry" should "contain DummyProdEnvironmentResolver" in {
    EnvironmentRegistry(system).environmentResolvers should have size 1
    EnvironmentRegistry(system).environmentResolvers.head should be (DummyProdEnvironmentResolver)
  }

  it should "resolve the environment" in {
    EnvironmentRegistry(system).resolve should be (PROD)
  }

  it should "give priority to resolvers in the reverse order of registration" in {
    EnvironmentRegistry(system).register(DummyQAEnvironmentResolver)
    EnvironmentRegistry(system).resolve should be (QA)
  }

  it should "try the chain of resolvers till the environment can be resolved" in {
    EnvironmentRegistry(system).register(DummyNotResolveEnvironmentResolver)
    EnvironmentRegistry(system).resolve should be (PROD)
  }

  it should "unregister a resolver" in {
    EnvironmentRegistry(system).register(DummyQAEnvironmentResolver)
    EnvironmentRegistry(system).resolve should be (QA)
    EnvironmentRegistry(system).unregister("DummyQAEnvironmentResolver")
    EnvironmentRegistry(system).resolve should be (PROD)
  }

}