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
package org.squbs.stream

import akka.actor.ActorSystem
import akka.pattern._
import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues._
import org.scalatest.{FlatSpec, Matchers}
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex._

import scala.concurrent.{Await, Future}

class PerpetualStreamSpec extends FlatSpec with Matchers {

  val dummyJarsDir = getClass.getClassLoader.getResource("classpaths").getPath

  it should "throw an IllegalStateException when accessing matValue before stream starts" in {

    val classPaths = Array("IllegalStateStream") map (dummyJarsDir + "/" + _)

    val config = ConfigFactory.parseString(
      s"""
         |squbs {
         |  actorsystem-name = IllegalStateStream
         |  ${JMX.prefixConfig} = true
         |}
      """.stripMargin
    )

    val boot = UnicomplexBoot(config)
      .createUsing {
        (name, config) => ActorSystem(name, config)
      }
      .scanComponents(classPaths)
      .start()

    import Timeouts._

    val reportF = (Unicomplex(boot.actorSystem).uniActor ? ReportStatus).mapTo[StatusReport]
    val StatusReport(state, cubes, _) = Await.result(reportF, awaitMax)
    state shouldBe Failed
    cubes.values should have size 1
    val InitReports(cubeState, actorReports) = cubes.values.head._2.value
    cubeState shouldBe Failed
    the [IllegalStateException] thrownBy actorReports.values.head.value.get should have message
      "Materialized value not available before streamGraph is started!"
    Unicomplex(boot.actorSystem).uniActor ! GracefulStop
  }

  it should "recover from upstream failure" in {
    val classPaths = Array("ThrowExceptionStream") map (dummyJarsDir + "/" + _)

    val config = ConfigFactory.parseString(
      s"""
         |squbs {
         |  actorsystem-name = ThrowExceptionStream
         |  ${JMX.prefixConfig} = true
         |}
      """.stripMargin
    )

    val boot = UnicomplexBoot(config)
      .createUsing {
        (name, config) => ActorSystem(name, config)
      }
      .scanComponents(classPaths)
      .start()

    import ThrowExceptionStream._
    import Timeouts._
    import boot.actorSystem

    val countF = (actorSystem.actorSelection("/user/ThrowExceptionStream/ThrowExceptionStream") ? NotifyWhenDone)
      .mapTo[Int]
    val count = Await.result(countF, awaitMax)
    count shouldBe (limit - 1)
    recordCount.get shouldBe (limit - 1)

    Unicomplex(actorSystem).uniActor ! GracefulStop
  }

  it should "properly drain the stream on shutdown" in {
    val classPaths = Array("ProperShutdownStream") map (dummyJarsDir + "/" + _)

    val config = ConfigFactory.parseString(
      s"""
         |squbs {
         |  actorsystem-name = ProperShutdownStream
         |  ${JMX.prefixConfig} = true
         |}
      """.stripMargin
    )

    val boot = UnicomplexBoot(config)
      .createUsing {
        (name, config) => ActorSystem(name, config)
      }
      .scanComponents(classPaths)
      .start()

    import ProperShutdownStream._
    import Timeouts._
    import boot.actorSystem

    // To avoid map at shutdown so the NotifyWhenDone obtains a Future[Long] right away.
    // Combined with "ask", we now have a Future[Future[Long]] in countFF. Then we have to do the very short await
    // to obtain the Future[Long] that will complete at or after shutdown.
    val countFF = (actorSystem.actorSelection("/user/ProperShutdownStream/ProperShutdownStream") ? NotifyWhenDone)
      .mapTo[Future[Long]]
    val countF = Await.result(countFF, awaitMax)
    Thread.sleep(500) // Let the stream run a bit.
    Unicomplex(actorSystem).uniActor ! GracefulStop
    val count = Await.result(countF, awaitMax)
    println(s"Counts -> src: ${genCount.get} dest: $count")
    count shouldBe genCount.get
  }

  it should "properly drain the stream with KillSwitch shutdown" in {
    val classPaths = Array("KillSwitchStream") map (dummyJarsDir + "/" + _)

    val config = ConfigFactory.parseString(
      s"""
         |squbs {
         |  actorsystem-name = KillSwitchStream
         |  ${JMX.prefixConfig} = true
         |}
      """.stripMargin
    )

    val boot = UnicomplexBoot(config)
      .createUsing {
        (name, config) => ActorSystem(name, config)
      }
      .scanComponents(classPaths)
      .start()

    import KillSwitchStream._
    import Timeouts._
    import boot.actorSystem

    // To avoid map at shutdown so the NotifyWhenDone obtains a Future[Long] right away.
    // Combined with "ask", we now have a Future[Future[Long]] in countFF. Then we have to do the very short await
    // to obtain the Future[Long] that will complete at or after shutdown.
    val countFF = (actorSystem.actorSelection("/user/KillSwitchStream/KillSwitchStream") ? NotifyWhenDone)
      .mapTo[Future[Long]]
    val countF = Await.result(countFF, awaitMax)
    Thread.sleep(500) // Let the stream run a bit.
    Unicomplex(actorSystem).uniActor ! GracefulStop
    val count = Await.result(countF, awaitMax)
    println(s"Counts -> src: ${genCount.get} dest: $count")
    count shouldBe genCount.get
  }

  it should "properly drain the stream with KillSwitch shutdown having other child actor" in {
    val classPaths = Array("KillSwitchWithChildActorStream") map (dummyJarsDir + "/" + _)

    val config = ConfigFactory.parseString(
      s"""
         |squbs {
         |  actorsystem-name = KillSwitchWithChildActorStream
         |  ${JMX.prefixConfig} = true
         |}
      """.stripMargin
    )

    val boot = UnicomplexBoot(config)
      .createUsing {
        (name, config) => ActorSystem(name, config)
      }
      .scanComponents(classPaths)
      .start()

    import KillSwitchWithChildActorStream._
    import Timeouts._
    import boot.actorSystem

    // To avoid map at shutdown so the NotifyWhenDone obtains a Future[Long] right away.
    // Combined with "ask", we now have a Future[Future[Long]] in countFF. Then we have to do the very short await
    // to obtain the Future[Long] that will complete at or after shutdown.
    val countFF = (actorSystem.actorSelection("/user/KillSwitchWithChildActorStream/KillSwitchWithChildActorStream") ?
      NotifyWhenDone)
      .mapTo[Future[Long]]
    val countF = Await.result(countFF, awaitMax)
    Thread.sleep(500) // Let the stream run a bit.
    Unicomplex(actorSystem).uniActor ! GracefulStop
    val count = Await.result(countF, awaitMax)
    println(s"Counts -> src: ${genCount.get} dest: $count")
    count shouldBe genCount.get
  }
}
