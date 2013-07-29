package org.squbs.testkit

import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{Suite, BeforeAndAfterAll}
import org.squbs.unicomplex.{Bootstrap, Unicomplex}

/**
 * Copyright (c) 2013 eBay, Inc.
 * All rights reserved.
 *
 * Contributors:
 * asucharitakul
 */
object SqubsTestKit {
  sys.addShutdownHook {
    Unicomplex.actorSystem.shutdown()
  }
  Bootstrap.main(Array.empty[String])

  private def checkInit(instance: SqubsTestKit) {
    // No op. Just need to ensure SqubsTestKit object is initialized.
  }
}

abstract class SqubsTestKit extends TestKit(Unicomplex.actorSystem)
with ImplicitSender with Suite with BeforeAndAfterAll {

  override protected def beforeAll() {
    SqubsTestKit.checkInit(this)
  }
}
