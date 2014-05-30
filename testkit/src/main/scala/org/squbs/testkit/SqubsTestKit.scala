package org.squbs.testkit

import scala.concurrent.duration._
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{Suite, BeforeAndAfterAll}
import org.squbs.unicomplex.{Unicomplex, UnicomplexBoot, Bootstrap}
import akka.actor.ActorSystem
import java.io.File
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
object SqubsTestKit {

  val debugMode = java.lang.management.ManagementFactory.getRuntimeMXBean.
    getInputArguments.toString.indexOf("jdwp") >= 0

  val debugTimeout = 10000.seconds

//  Bootstrap.main(Array.empty[String])

  val actorSystems = collection.concurrent.TrieMap.empty[String, ActorSystem]


  if (debugMode) println(
    "\n##################\n" +
      s"IMPORTANT: Detected system running in debug mode. Test timeouts overridden to $debugTimeout.\n" +
      "##################\n\n")

  private def checkInit(actorSystem: ActorSystem) {
    if (actorSystems.putIfAbsent(actorSystem.name, actorSystem) == None)
      sys.addShutdownHook {
        actorSystem.shutdown()
      }
  }
}

abstract class SqubsTestKit(actorSystem: ActorSystem) extends TestKit(actorSystem)
with ImplicitSender with Suite with BeforeAndAfterAll {

  import SqubsTestKit._

  override protected def beforeAll() {
    SqubsTestKit.checkInit(actorSystem)
    UnicomplexBoot { () => ActorSystem("squbs") }
      .scanComponents(System.getProperty("java.class.path").split(File.pathSeparator))
      .initExtensions
      .start()
  }

  override protected def afterAll() {
    Unicomplex(system).uniActor ! GracefulStop
  }

  override def receiveOne(max: Duration) =
    if (debugMode) super.receiveOne(debugTimeout)
    else super.receiveOne(max)

}
