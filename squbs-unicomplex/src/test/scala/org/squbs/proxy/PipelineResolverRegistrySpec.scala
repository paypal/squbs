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

package org.squbs.proxy

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpecLike, Matchers}
import org.squbs.pipeline.Processor

class PipelineResolverRegistrySpec extends TestKit(ActorSystem("PipelineResolverRegistrySpec", ConfigFactory.parseString(
  """
    |
  """.stripMargin))) with FlatSpecLike with Matchers {


  val registry = PipelineResolverRegistry(system)


  "PipelineResolverRegistry" should "work" in {
    registry.default should be(SimplePipelineResolver)

    registry.getResolver("abc") should be(None)

    registry.register("abc", new PipelineResolver {
      override def resolve(config: SimplePipelineConfig): Option[Processor] = ???
    })

    Thread.sleep(500)

    registry.getResolver("abc") should not be (None)
  }

}
