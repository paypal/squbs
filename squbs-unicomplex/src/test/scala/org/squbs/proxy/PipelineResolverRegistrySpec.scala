package org.squbs.proxy

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpecLike, Matchers}
import org.squbs.pipeline.Processor

/**
 * Created by lma on 2015/5/4.
 */
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
