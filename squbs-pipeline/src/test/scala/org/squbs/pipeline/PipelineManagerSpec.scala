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

package org.squbs.pipeline

import akka.actor.{ActorContext, ActorRefFactory, ActorSystem}
import akka.testkit.TestKit
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{FlatSpecLike, Matchers}
import Timeouts._

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

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
    |    factory = org.squbs.pipeline.TestProcessorFactory2
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
    |     pipe1{
    |        type = squbs.proxy
    |        aliases = [111,222]
    |        factory = org.squbs.pipeline.TestResolver1
    |        settings = {
    |          inbound = [handler1, handler2]
    |          outbound = [handler3, handler4]
    |        }
    |      }
    |
    |      pipe2{
    |        type = squbs.proxy
    |        aliases = [333,444]
    |        factory = org.squbs.pipeline.TestResolver2
    |      }
    |
    |      pipe3{
    |        type = squbs.proxy
    |        factory = org.squbs.pipeline.TestResolver3
    |        settings = {
    |        }
    |      }
    |
    |      pipe4{
    |        type = squbs.proxy
    |        factory = org.squbs.pipeline.TestResolver4
    |      }
    |
    |      handler1{
    |        type = pipeline.handler
    |        factory = org.squbs.pipeline.TestHandlerFactory
    |      }
    |
    |      handler2{
    |        type = pipeline.handler
    |        factory = org.squbs.pipeline.TestHandlerFactory
    |      }
    |
    |      handler3{
    |        type = pipeline.handler
    |        factory = org.squbs.pipeline.TestHandlerFactory
    |      }
    |
    |      handler4{
    |        type = pipeline.handler
    |        factory = org.squbs.pipeline.TestHandlerFactory
    |      }
    |
  """.stripMargin))) with FlatSpecLike with Matchers {

  val manager = PipelineManager(system)

  "PipelineManager getProcessor" should "work" in {

    manager.getProcessor("proxy1") should not be None
    manager.getProcessor("proxy2") should be (None)
    awaitAssert({
      manager.getProcessor("proxy1") should not be None
      manager.getProcessor("aaa") should not be None
      manager.getProcessor("bbb") should not be None
      manager.getProcessor("proxy2") should be(None)
      manager.getProcessor("ccc") should be(None)
      manager.getProcessor("ddd") should be(None)
    }, awaitMax)

    import org.squbs.pipeline.Tracking._

    buffer.size should be(4)

    the[ClassNotFoundException] thrownBy {
      manager.getProcessor("proxy3")
    } should have message "org.squbs.pipeline.TestProcessorFactory3"


    the[IllegalArgumentException] thrownBy {
      manager.getProcessor("proxy4")
    } should have message "Unsupported processor factory: org.squbs.pipeline.TestProcessorFactory4"

    the[IllegalArgumentException] thrownBy {
      manager.getProcessor("proxy5")
    } should have message "No registered squbs.proxy found with name: proxy5"

  }

  "PipelineManager getPipelineSetting" should "work" in {

    val pipe = manager.getPipelineSetting("pipe1")
    pipe should not be None
    pipe.get.factory shouldBe a [TestResolver1]
    pipe.get.config.get.reqPipe.size should be (2)
    pipe.get.config.get.reqPipe(1) shouldBe a [TestHandlerFactory]
    pipe.get.config.get.respPipe.size should be (2)

    manager.getPipelineSetting("pipe2") should not be None
    awaitAssert({
      manager.getPipelineSetting("pipe1") should not be None
      manager.getPipelineSetting("111") should not be None
      manager.getPipelineSetting("222") should not be None
      manager.getPipelineSetting("333") should not be None
      manager.getPipelineSetting("444") should not be None
    }, awaitMax)


    the[ClassNotFoundException] thrownBy {
      manager.getPipelineSetting("pipe3")
    } should have message "org.squbs.pipeline.TestResolver3"


    the[IllegalArgumentException] thrownBy {
      manager.getPipelineSetting("pipe4")
    } should have message "Unsupported processor factory: org.squbs.pipeline.TestResolver4"


    the[IllegalArgumentException] thrownBy {
      manager.getPipelineSetting("pipe5")
    } should have message "No registered pipeline found with name: pipe5"

    the[IllegalArgumentException] thrownBy {
      manager.getPipelineSetting("proxy1")
    } should have message "squbs.proxy found with name of proxy1, but the factory should implement PipelineProcessorFactory"

  }

}

object Tracking {
  val buffer = ListBuffer[String]()

}

object TestProcessor extends Processor {
  //inbound processing
  def inbound(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext):
      Future[RequestContext] = Future.successful(reqCtx)

  //outbound processing
  def outbound(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext):
      Future[RequestContext] = Future.successful(reqCtx)
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

  import org.squbs.pipeline.Tracking._

  buffer += "TestProcessorFactory2.init"

  override def create(settings: Option[Config])(implicit actorRefFactory: ActorRefFactory): Option[Processor] = {
    buffer += "TestProcessorFactory2.create"
    None
  }
}

class TestProcessorFactory4

class TestResolver1 extends PipelineProcessorFactory {
  def create(config: SimplePipelineConfig, setting: Option[Config])(implicit actorRefFactory: ActorRefFactory):
  Option[Processor] = None
}

class TestResolver2 extends PipelineProcessorFactory {
  def create(config: SimplePipelineConfig, setting: Option[Config])(implicit actorRefFactory: ActorRefFactory):
  Option[Processor] = None
}

class TestResolver4

class TestHandlerFactory extends HandlerFactory with Handler {
  override def create(config: Option[Config])(implicit actorRefFactory: ActorRefFactory): Option[Handler] = Some(this)

  override def process(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext):
  Future[RequestContext] = Future.successful(reqCtx)
}





