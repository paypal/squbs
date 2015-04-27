package org.squbs.pipeline

import akka.actor.{ActorContext, ActorRefFactory, ActorSystem}
import akka.testkit.TestKit
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{FlatSpecLike, Matchers}

import scala.concurrent.{ExecutionContext, Future}

class PipelineHandlerManagerSpec extends TestKit(ActorSystem("PipelineHandlerManagerSpec", ConfigFactory.parseString(
  """
    |handler1{
    |    type = pipeline.handler
    |    factory = org.squbs.pipeline.TestHandlerFactory1
    |    settings = {
    |    }
    |  }
    |
    |  handler2{
    |    type = pipeline.handler
    |    factory = org.squbs.pipeline.TestHandlerFactory2
    |  }
    |
    |  handler3{
    |    type = pipeline.handler
    |    factory = org.squbs.pipeline.TestHandlerFactory3
    |    settings = {
    |    }
    |  }
    |
    |  handler4{
    |    type = pipeline.handler
    |    factory = org.squbs.pipeline.TestHandlerFactory4
    |  }
  """.stripMargin))) with FlatSpecLike with Matchers {

  val manager = PipelineHandlerManager(system)

  "PipelineHandlerManager" should "work" in {

    manager.get("handler1") should not be (None)
    manager.get("handler2") should be(None)
    manager.get("handler2") should be(None)

    the[ClassNotFoundException] thrownBy {
      manager.get("handler3")
    } should have message "org.squbs.pipeline.TestHandlerFactory3"


    the[ClassCastException] thrownBy {
      manager.get("handler4")
    } should have message "org.squbs.pipeline.TestHandlerFactory4 cannot be cast to org.squbs.pipeline.HandlerFactory"

    the[IllegalArgumentException] thrownBy {
      manager.get("handler5")
    } should have message "No registered handler found with name of handler5"

  }

}

class TestHandlerFactory1 extends HandlerFactory with Handler {
  println("init TestHandlerFactory1")

  override def create(config: Option[Config])(implicit actorRefFactory: ActorRefFactory): Option[Handler] = {
    return Some(this)
  }

  override def process(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext): Future[RequestContext] = ???
}

class TestHandlerFactory2 extends HandlerFactory with Handler {
  println("init TestHandlerFactory2")

  override def create(config: Option[Config])(implicit actorRefFactory: ActorRefFactory): Option[Handler] = {
    return None
  }

  override def process(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext): Future[RequestContext] = ???
}

class TestHandlerFactory4




