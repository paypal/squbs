
/*
 * Copyright 2017 PayPal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.squbs.unicomplex

import org.apache.pekko.actor.{ActorSystem, Cancellable, Scheduler}
import org.apache.pekko.event.LoggingAdapter
import org.apache.pekko.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.lifecycle.GracefulStop

import java.util.concurrent.ThreadFactory
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class ShutdownSpec extends TestKit(ShutdownSpec.boot.actorSystem) with AnyFlatSpecLike with Matchers with ImplicitSender {

  it should "shut the system down immediately" in {
    Unicomplex("ShutdownSpec") ! ObtainLifecycleEvents(Stopping)
    val t1 = System.nanoTime
    import system.dispatcher
    Future { Shutdown.main(Array("ShutdownSpec")) }
    // Unicomplex stop should immediately be triggered.
    expectMsg(1.second, Stopping)
    val t2 = System.nanoTime
    Duration(t2 - t1, NANOSECONDS) should be < (1.second)
  }
}

object ShutdownSpec {

  val testConfig = ConfigFactory.parseString(
    s"""
       |squbs {
       | actorsystem-name = ShutdownSpec
       | ${JMX.prefixConfig} = true
       |}
    """.stripMargin)

  val boot = UnicomplexBoot(testConfig).createUsing((name, config) => ActorSystem(name, config)).start()
}

class ShutdownSpec2 extends TestKit(ShutdownSpec2.boot.actorSystem)
  with AnyFlatSpecLike with Matchers with ImplicitSender {

  it should "keep the system active for the duration of squbs.shutdown-delay" in {
    Unicomplex("ShutdownSpec2") ! ObtainLifecycleEvents(Stopping)
    val t1 = System.nanoTime
    import system.dispatcher
    Future { Shutdown.main(Array("ShutdownSpec2")) }
    // Unicomplex stop should not be triggered at least for 6 seconds
    expectMsg(10.seconds, Stopping)
    val t2 = System.nanoTime
    Duration(t2 - t1, NANOSECONDS) should be > (6.seconds)
  }
}

object ShutdownSpec2 {

  val testConfig = ConfigFactory.parseString(
    s"""
       |squbs {
       | actorsystem-name = ShutdownSpec2
       | ${JMX.prefixConfig} = true
       | shutdown-delay = 6 seconds
       |}
    """.stripMargin)

  val boot = UnicomplexBoot(testConfig).createUsing((name, config) => ActorSystem(name, config)).start()
}

class ShutdownSpec3 extends TestKit(ShutdownSpec3.boot.actorSystem)
  with AnyFlatSpecLike with Matchers with ImplicitSender {

  it should "keep the system active for parametrized duration" in {
    Unicomplex("ShutdownSpec3") ! ObtainLifecycleEvents(Stopping)
    val t1 = System.nanoTime
    import system.dispatcher
    Future { Shutdown.shutdown(Some(5.seconds), Some("ShutdownSpec3")) }
    // Unicomplex stop should not be triggered at least for 5 seconds
    expectMsg(10.seconds, Stopping)
    val t2 = System.nanoTime
    Duration(t2 - t1, NANOSECONDS) should be > (5.seconds)
  }
}

object ShutdownSpec3 {

  val testConfig = ConfigFactory.parseString(
    s"""
       |squbs {
       | actorsystem-name = ShutdownSpec3
       | ${JMX.prefixConfig} = true
       |}
    """.stripMargin)

  val boot = UnicomplexBoot(testConfig).createUsing((name, config) => ActorSystem(name, config)).start()
}

class ShutdownSpec4 extends TestKit(ShutdownSpec4.boot.actorSystem)
  with AnyFlatSpecLike with Matchers with ImplicitSender {

  it should "not trigger shutdown if the shutdown has already been started" in {
    Unicomplex("ShutdownSpec4") ! ObtainLifecycleEvents(Stopping)
    Unicomplex("ShutdownSpec4") ! GracefulStop
    expectMsg(1.second, Stopping)
    Shutdown.shutdown(Some(5432.milliseconds), Some("ShutdownSpec4")) // Blocking to make sure we wait for it complete
    expectNoMessage(1.second)
    ShutdownSpec4.counter should be (0)
  }
}

object ShutdownSpec4 {

  val testConfig = ConfigFactory.parseString(
    s"""
       |squbs {
       | actorsystem-name = ShutdownSpec4
       | ${JMX.prefixConfig} = true
       |}
       |
       |pekko.scheduler.implementation = org.squbs.unicomplex.MockScheduler
    """.stripMargin)

  val boot = UnicomplexBoot(testConfig).createUsing((name, config) => ActorSystem(name, config)).start()

  var counter = 0
}

class MockScheduler(config: Config,
                    log: LoggingAdapter,
                    threadFactory: ThreadFactory) extends Scheduler {

  override def schedule(initialDelay: FiniteDuration, interval: FiniteDuration, runnable: Runnable)(implicit executor: ExecutionContext): Cancellable = {
    new DummyCancellable
  }

  override def maxFrequency: Double = 100

  override def scheduleOnce(delay: FiniteDuration, runnable: Runnable)(implicit executor: ExecutionContext): Cancellable = {
    if(delay == 5432.milliseconds) ShutdownSpec4.counter += 1
    new DummyCancellable
  }

  class DummyCancellable extends Cancellable {
    override def cancel(): Boolean = false
    override def isCancelled: Boolean = false
  }
}
