package org.squbs.testkit

import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{Suite, BeforeAndAfterAll}
import org.squbs.unicomplex.UnicomplexBoot
import akka.actor.ActorSystem
import java.io.File
import com.typesafe.config.ConfigFactory

/**
 * Copyright (c) 2013 eBay, Inc.
 * All rights reserved.
 *
 * Contributors:
 * asucharitakul
 *
 * this is 0.3.0-SNAPSHOT
 */
object SimpleTestKit {

  val testConfFile = Option(getClass.getResource("/test.conf")) orElse Option(getClass.getResource("/default-test.conf"))
  val testConfig = testConfFile map ConfigFactory.parseURL getOrElse null

  val boot = UnicomplexBoot(testConfig)
              .createUsing { (name, config) => ActorSystem(name, testConfig) } // Use the test config instead.
              .scanComponents(System.getProperty("java.class.path").split(File.pathSeparator))
              .initExtensions
  boot.start()

  private def checkInit(actorSystem: ActorSystem) {
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
abstract class SimpleTestKit extends TestKit(SimpleTestKit.boot.actorSystem)
  with DebugTiming with ImplicitSender with Suite with BeforeAndAfterAll {

  override protected def beforeAll() {
    SimpleTestKit.checkInit(system)
  }
}
