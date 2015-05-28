/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the AUTHORS file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
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




