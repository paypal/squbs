package org.squbs.proxy

import akka.actor.{ActorContext, ActorRefFactory, ActorSystem}
import akka.testkit.TestKit
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{FlatSpecLike, Matchers}
import org.squbs.pipeline.{Handler, HandlerFactory, Processor, RequestContext}

import scala.concurrent.{ExecutionContext, Future}

/**
 * Created by lma on 7/30/2015.
 */
class PipelineRegistrySpec extends TestKit(ActorSystem("PipelineRegistrySpec", ConfigFactory.parseString(
  """
    |  pipe1{
    |    type = squbs.pipe
    |    aliases = [aaa,bbb]
    |    resolver = org.squbs.proxy.TestResolver1
    |    pipeline = {
    |      inbound = [handler1, handler2]
    |      outbound = [handler3, handler4]
    |    }
    |  }
    |
    |  pipe2{
    |    type = squbs.pipe
    |    aliases = [ccc,ddd]
    |    resolver = org.squbs.proxy.TestResolver2
    |  }
    |
    |  pipe3{
    |    type = squbs.pipe
    |    resolver = org.squbs.proxy.TestResolver3
    |    settings = {
    |    }
    |    pipeline = {
    |
    |    }
    |  }
    |
    |  pipe4{
    |    type = squbs.pipe
    |    resolver = org.squbs.proxy.TestResolver4
    |  }
    |
    |  handler1{
    |    type = pipeline.handler
    |    factory = org.squbs.proxy.TestHandlerFactory
    |  }
    |
    |  handler2{
    |    type = pipeline.handler
    |    factory = org.squbs.proxy.TestHandlerFactory
    |  }
    |
    |  handler3{
    |    type = pipeline.handler
    |    factory = org.squbs.proxy.TestHandlerFactory
    |  }
    |
    |  handler4{
    |    type = pipeline.handler
    |    factory = org.squbs.proxy.TestHandlerFactory
    |  }
    |
    |
  """.stripMargin))) with FlatSpecLike with Matchers {

  val manager = PipelineRegistry(system)

  "PipelineManager" should "work" in {

    val pipe = manager.get("pipe1")
    pipe should not be (None)
    pipe.get.resolver.get.isInstanceOf[TestResolver1] should be(true)
    pipe.get.config.get.reqPipe.size should be(2)
    pipe.get.config.get.reqPipe(1).isInstanceOf[TestHandlerFactory] should be(true)
    pipe.get.config.get.respPipe.size should be(2)

    manager.get("pipe2") should not be (None)
    Thread.sleep(500)
    manager.get("pipe1") should not be (None)
    manager.get("aaa") should not be (None)
    manager.get("bbb") should not be (None)
    manager.get("ccc") should not be (None)
    manager.get("ddd") should not be (None)


    the[ClassNotFoundException] thrownBy {
      manager.get("pipe3")
    } should have message "org.squbs.proxy.TestResolver3"


    the[ClassCastException] thrownBy {
      manager.get("pipe4")
    } should have message "org.squbs.proxy.TestResolver4 cannot be cast to org.squbs.proxy.PipelineResolver"

    //    the[IllegalArgumentException] thrownBy {
    //      manager.get("pipe5")
    //    } should have message "No registered squbs.pipe found with name of pipe5"

    manager.get("pipe5") should be(None)

  }

}


class TestResolver1 extends PipelineResolver {
  override def resolve(config: SimplePipelineConfig, setting: Option[Config]): Option[Processor] = ???
}

class TestResolver2 extends PipelineResolver {
  override def resolve(config: SimplePipelineConfig, setting: Option[Config]): Option[Processor] = ???
}

class TestResolver4

class TestHandlerFactory extends HandlerFactory with Handler {
  override def create(config: Option[Config])(implicit actorRefFactory: ActorRefFactory): Option[Handler] = Some(this)

  override def process(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext): Future[RequestContext] = ???
}
