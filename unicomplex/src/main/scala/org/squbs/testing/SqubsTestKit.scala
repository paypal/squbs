package org.squbs.testing

import akka.testkit.{ImplicitSender, TestKit}
import org.squbs.unicomplex.{Bootstrap, Unicomplex}
import org.scalatest.{Suite, BeforeAndAfterAll}

/**
 * Copyright (c) 2013 eBay, Inc.
 * All rights reserved.
 *
 * Contributors:
 * asucharitakul
 */
abstract class SqubsTestKit extends TestKit(Unicomplex.actorSystem) with ImplicitSender with Suite with BeforeAndAfterAll {

  // TODO: Move squbs.testing to its own project

  override protected def beforeAll() {
    Bootstrap.main(Array.empty[String])
  }

  override protected def afterAll() {
    Unicomplex.actorSystem.shutdown()
  }
}
