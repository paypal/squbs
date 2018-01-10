/*
 *  Copyright 2017 PayPal
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
package org.squbs.testkit.japi

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.javadsl.model.HttpRequest
import akka.http.javadsl.server.RouteResult
import akka.http.javadsl.testkit.{RouteTest, TestRouteResult}
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.testng.TestNGSuiteLike
import org.testng.Assert
import org.testng.annotations.{AfterClass, BeforeClass}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.control.NonFatal

/**
  * A RouteTest that uses JUnit assertions. ActorSystem and Materializer are provided as an [[org.junit.rules.ExternalResource]]
  * and their lifetime is automatically managed.
  */
trait TestNGRouteTestBase extends RouteTest with RouteDefinitionTest with TestNGSuiteLike  {
  protected def systemResource: SystemResource
  implicit def system: ActorSystem = systemResource.system
  implicit def materializer: Materializer = systemResource.materializer

  override protected def createTestRouteResultAsync(request: HttpRequest, result: Future[RouteResult]): TestRouteResult =
    new TestRouteResult(result, awaitDuration)(system.dispatcher, materializer) {
      protected def assertEquals(expected: AnyRef, actual: AnyRef, message: String): Unit =
        reportDetails {
          Assert.assertEquals(actual, expected, message)
        }

      protected def assertEquals(expected: Int, actual: Int, message: String): Unit =
        Assert.assertEquals(actual, expected, message)

      protected def assertTrue(predicate: Boolean, message: String): Unit =
        Assert.assertTrue(predicate, message)

      protected def fail(message: String): Unit = {
        Assert.fail(message)
        throw new IllegalStateException("Assertion should have failed")
      }

      def reportDetails[T](block: ⇒ T): T = {
        try block catch {
          case NonFatal(t) ⇒ throw new AssertionError(t.getMessage + "\n" +
            "  Request was:      " + request + "\n" +
            "  Route result was: " + result + "\n", t)
        }
      }
    }
}

abstract class TestNGRouteTest extends TestNGRouteTestBase {

  protected def additionalConfig: Config = ConfigFactory.empty()

  private[this] val _systemResource = new SystemResource(Logging.simpleName(getClass), additionalConfig)

  protected def systemResource: SystemResource = _systemResource

  @BeforeClass(alwaysRun=true)
  def setup(): Unit = {
    systemResource.before()
  }

  @AfterClass(alwaysRun=true)
  def teardown(): Unit = {
    systemResource.after()
  }
}

class SystemResource(name: String, additionalConfig: Config) {
  protected def config = additionalConfig.withFallback(ConfigFactory.load())
  protected def createSystem(): ActorSystem = ActorSystem(name, config)
  protected def createMaterializer(system: ActorSystem): ActorMaterializer = ActorMaterializer()(system)

  implicit def system: ActorSystem = _system
  implicit def materializer: ActorMaterializer = _materializer

  private[this] var _system: ActorSystem = null
  private[this] var _materializer: ActorMaterializer = null

  def before(): Unit = {
    require((_system eq null) && (_materializer eq null))
    _system = createSystem()
    _materializer = createMaterializer(_system)
  }
  def after(): Unit = {
    Await.result(_system.terminate(), 5.seconds)
    _system = null
    _materializer = null
  }
}

