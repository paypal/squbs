package org.squbs.testkit

import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{Suite, BeforeAndAfterAll}
import org.squbs.unicomplex.{Unicomplex, UnicomplexBoot}
import akka.actor.ActorSystem
import org.squbs.lifecycle.GracefulStop

/**
 * Copyright (c) 2013 eBay, Inc.
 * All rights reserved.
 *
 * Contributors:
 * asucharitakul
 *
 * this is 0.3.0-SNAPSHOT
 */
object CustomTestKit {

  val actorSystems = collection.concurrent.TrieMap.empty[String, ActorSystem]

  private def checkInit(actorSystem: ActorSystem) {
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
