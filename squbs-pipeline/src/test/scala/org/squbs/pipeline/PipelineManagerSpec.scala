package org.squbs.pipeline

import akka.actor.{ActorContext, ActorRefFactory, ActorSystem}
import akka.testkit.TestKit
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{FlatSpecLike, Matchers}

import scala.collection.mutable.ListBuffer
import scala.concurrent.{Future, ExecutionContext}

class PipelineManagerSpec extends TestKit(ActorSystem("PipelineManagerSpec", ConfigFactory.parseString(
  """
    |  proxy1{
    |    type = squbs.proxy
    |    aliases = [aaa,bbb]
    |    processorFactory = org.squbs.pipeline.TestProcessorFactory1
    |    settings = {
    |    }
    |  }
    |
    |  proxy2{
    |    type = squbs.proxy
    |    aliases = [ccc,ddd]
    |    processorFactory = org.squbs.pipeline.TestProcessorFactory2
    |  }
    |
    |  proxy3{
    |    type = squbs.proxy
    |    processorFactory = org.squbs.pipeline.TestProcessorFactory3
    |    settings = {
    |    }
    |  }
    |
    |  proxy4{
    |    type = squbs.proxy
    |    processorFactory = org.squbs.pipeline.TestProcessorFactory4
    |  }
    |
    |
  """.stripMargin))) with FlatSpecLike with Matchers {

  val manager = PipelineManager(system)

  "PipelineManager" should "work" in {

    manager.get("proxy1") should not be (None)
    manager.get("proxy2") should be(None)
    Thread.sleep(500)
    manager.get("proxy1") should not be (None)
    manager.get("aaa") should not be (None)
    manager.get("bbb") should not be (None)
    manager.get("proxy2") should be(None)
    manager.get("ccc") should be(None)
    manager.get("ddd") should be(None)

    import Tracking._

    buffer.size should be(4)

    the[ClassNotFoundException] thrownBy {
      manager.get("proxy3")
    } should have message "org.squbs.pipeline.TestProcessorFactory3"


    the[ClassCastException] thrownBy {
      manager.get("proxy4")
    } should have message "org.squbs.pipeline.TestProcessorFactory4 cannot be cast to org.squbs.pipeline.ProcessorFactory"

    the[IllegalArgumentException] thrownBy {
      manager.get("proxy5")
    } should have message "No registered squbs.proxy found with name of proxy5"

  }

}

object Tracking {
  val buffer = ListBuffer[String]()

}

object TestProcessor extends Processor {
  //inbound processing
  override def inbound(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext): Future[RequestContext] = ???

  //outbound processing
  override def outbound(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext): Future[RequestContext] = ???
}

class TestProcessorFactory1 extends ProcessorFactory {

  import org.squbs.pipeline.Tracking._

  buffer += "TestProcessorFactory1.init"

  override def create(settings: Option[Config])(implicit actorRefFactory: ActorRefFactory): Option[Processor] = {
    buffer += "TestProcessorFactory1.create"
    Some(TestProcessor)
  }
}

class TestProcessorFactory2 extends ProcessorFactory {

  import Tracking._

  buffer += "TestProcessorFactory2.init"

  override def create(settings: Option[Config])(implicit actorRefFactory: ActorRefFactory): Option[Processor] = {
    buffer += "TestProcessorFactory2.create"
    None
  }
}

class TestProcessorFactory4





