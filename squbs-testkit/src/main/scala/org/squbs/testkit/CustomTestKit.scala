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
package org.squbs.testkit

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, Suite}
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.{Unicomplex, UnicomplexBoot}

object CustomTestKit {

  val actorSystems = collection.concurrent.TrieMap.empty[String, ActorSystem]

  private[testkit] def checkInit(actorSystem: ActorSystem) {
    if (actorSystems.putIfAbsent(actorSystem.name, actorSystem) == None)
      sys.addShutdownHook {
        actorSystem.shutdown()
      }
  }
}

/**
 * The custom test kit allows custom configuration of the Unicomplex before boot. It also does not require the test
 * to run in a separate process and allow for parallel tests. The only difficulty is it needs a started
 * UnicomplexBoot instance as the input. Usage:
 * <pre>
 *   class MyActorSpec extends CustomTestKit(UnicomplexBoot {name => ActorSystem(name)} .start()) {
 *     // Do the testing here.
 *   }
 * </pre>
 *
 * @param boot The pre-configured, not started UnicomplexBoot object
 */
abstract class CustomTestKit(boot: UnicomplexBoot) extends TestKit(boot.actorSystem)
    with DebugTiming with ImplicitSender with Suite with BeforeAndAfterAll {

  override protected def beforeAll() {
    CustomTestKit.checkInit(system)
  }

  override protected def afterAll() {
    Unicomplex(system).uniActor ! GracefulStop
  }
}
