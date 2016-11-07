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

package org.squbs.testkit

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Suite}
import org.squbs.unicomplex.UnicomplexBoot

import scala.language.postfixOps

object SimpleTestKit {

  val testConfFile = Option(getClass.getResource("/test.conf")) orElse Option(getClass.getResource("/default-test.conf"))
  val testConfig = (testConfFile map ConfigFactory.parseURL) orNull

  val boot = UnicomplexBoot(testConfig)
              .createUsing { (name, config) => ActorSystem(name, testConfig) } // Use the test config instead.
              .scanResources()
              .initExtensions
  boot.start()

  private[testkit] def checkInit(actorSystem: ActorSystem) {
      sys.addShutdownHook {
        actorSystem.shutdown()
      }
  }
}

/**
 * The most convenient way to start a single squbs container for testing. Just extend the SimpleTestKit and all will
 * be taken care of, similar to the squbs runtime.<br/>
 *
 * Limitations to this model are the single Unicomplex instance and
 * the component scanning from the classpath (as in the regular squbs runtime). The test process must be forked and
 * no parallel tests with different squbs configurations are allowed.
 */
@deprecated("use org.squbs.testkit.CustomTestKit instead")
abstract class SimpleTestKit extends TestKit(SimpleTestKit.boot.actorSystem)
  with DebugTiming with ImplicitSender with Suite with BeforeAndAfterAll {

  override protected def beforeAll() {
    SimpleTestKit.checkInit(system)
  }
}
