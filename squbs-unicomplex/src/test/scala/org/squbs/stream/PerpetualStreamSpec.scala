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

import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}

import akka.NotUsed
import akka.actor.{Actor, ActorRef, ActorRefFactory, ActorSystem, Props}
import akka.pattern._
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{Flow, GraphDSL, MergeHub, RunnableGraph, Sink, Source}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues._
import org.scalatest.{FlatSpec, Matchers}
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

class PerpetualStreamSpec extends FlatSpec with Matchers {

  private val dummyJarsDir = getClass.getClassLoader.getResource("classpaths").getPath

  private def startUnicomplex(
    systemName: String
  ): UnicomplexBoot = {
    val classPaths: Set[String] = Set(systemName).map(dummyJarsDir + "/" + _)
    val config = ConfigFactory.parseString(
      s"""
         |squbs {
         |  actorsystem-name = $systemName
         |  ${JMX.prefixConfig} = true
         |}
      """.stripMargin
    )

    UnicomplexBoot(config)
      .createUsing {
        (name, config) => ActorSystem(name, config)
      }
      .scanComponents(classPaths.toSeq)
      .start()
  }

  it should "throw an IllegalStateException when accessing matValue before stream starts" in {

    val boot = startUnicomplex("IllegalStateStream")

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
    val boot = startUnicomplex("ThrowExceptionStream")

    import ThrowExceptionStream._
    import Timeouts._
    implicit def actorSystem = boot.actorSystem

    val countF = (SafeSelect("/user/ThrowExceptionStream/ThrowExceptionStream") ? NotifyWhenDone).mapTo[Int]
    val count = Await.result(countF, awaitMax)
    count shouldBe (limit - 1)
    recordCount.get shouldBe (limit - 1)

    Unicomplex(actorSystem).uniActor ! GracefulStop
  }

  it should "properly drain the stream on shutdown" in {
    val boot = startUnicomplex("ProperShutdownStream")

    import ProperShutdownStream._
    import Timeouts._
    implicit def actorSystem = boot.actorSystem

    // To avoid map at shutdown so the NotifyWhenDone obtains a Future[Long] right away.
    // Combined with "ask", we now have a Future[Future[Long]] in countFF. Then we have to do the very short await
    // to obtain the Future[Long] that will complete at or after shutdown.
    val countFF = (SafeSelect("/user/ProperShutdownStream/ProperShutdownStream") ? NotifyWhenDone).mapTo[Future[Long]]
    val countF = Await.result(countFF, awaitMax)
    Thread.sleep(500) // Let the stream run a bit.
    Unicomplex(actorSystem).uniActor ! GracefulStop
    val count = Await.result(countF, awaitMax)
    println(s"Counts -> src: ${genCount.get} dest: $count")
    count shouldBe genCount.get
  }

  it should "properly drain the stream with KillSwitch shutdown" in {
    val boot = startUnicomplex("KillSwitchStream")

    import KillSwitchStream._
    import Timeouts._
    implicit def actorSystem = boot.actorSystem

    // To avoid map at shutdown so the NotifyWhenDone obtains a Future[Long] right away.
    // Combined with "ask", we now have a Future[Future[Long]] in countFF. Then we have to do the very short await
    // to obtain the Future[Long] that will complete at or after shutdown.
    val countFF = (SafeSelect("/user/KillSwitchStream/KillSwitchStream") ? NotifyWhenDone).mapTo[Future[Long]]
    val countF = Await.result(countFF, awaitMax)
    Thread.sleep(500) // Let the stream run a bit.
    Unicomplex(actorSystem).uniActor ! GracefulStop
    val count = Await.result(countF, awaitMax)
    println(s"Counts -> src: ${genCount.get} dest: $count")
    count shouldBe genCount.get
  }

  it should "properly drain the stream materializing to KillSwitch at shutdown" in {
    val boot = startUnicomplex("KillSwitchMatStream")

    import KillSwitchMatStream._
    import Timeouts._
    implicit def actorSystem = boot.actorSystem

    // To avoid map at shutdown so the NotifyWhenDone obtains a Future[Long] right away.
    // Combined with "ask", we now have a Future[Future[Long]] in countFF. Then we have to do the very short await
    // to obtain the Future[Long] that will complete at or after shutdown.
    val countFF = (SafeSelect("/user/KillSwitchMatStream/KillSwitchMatStream") ? NotifyWhenDone).mapTo[Future[Long]]
    val countF = Await.result(countFF, awaitMax)
    Thread.sleep(500) // Let the stream run a bit.
    Unicomplex(actorSystem).uniActor ! GracefulStop
    val count = Await.result(countF, awaitMax)
    println(s"Counts -> src: ${genCount.get} dest: $count")
    count shouldBe genCount.get
  }

  it should "properly drain the stream with KillSwitch shutdown having other child actor" in {
    val boot = startUnicomplex("KillSwitchWithChildActorStream")

    import KillSwitchWithChildActorStream._
    import Timeouts._
    implicit def actorSystem = boot.actorSystem

    // To avoid map at shutdown so the NotifyWhenDone obtains a Future[Long] right away.
    // Combined with "ask", we now have a Future[Future[Long]] in countFF. Then we have to do the very short await
    // to obtain the Future[Long] that will complete at or after shutdown.
    val countFF = (SafeSelect("/user/KillSwitchWithChildActorStream/KillSwitchWithChildActorStream") ? NotifyWhenDone)
      .mapTo[Future[Long]]
    val countF = Await.result(countFF, awaitMax)
    Thread.sleep(500) // Let the stream run a bit.
    Unicomplex(actorSystem).uniActor ! GracefulStop
    val count = Await.result(countF, awaitMax)
    println(s"Counts -> src: ${genCount.get} dest: $count")
    count shouldBe genCount.get
  }

  "PerpetualStreamMatValue's matValue(String)" should "return a Sink[T, NotUsed] " +
    "when the referenced perpetualstream's mat value is a Sink[T, NotUsed]" in {
    import PerpetualStreamMatValueSpecHelper._
    val to = FiniteDuration(1, TimeUnit.SECONDS)
    useSystem(classOf[PerpStreamActors.SinkMaterializingStream]) { case Right(actor) =>
      implicit val timeout = Timeout(to)
      val queue = Await.result((actor ? Queue).mapTo[LinkedBlockingQueue[Long]], to)
      queue.poll(1, TimeUnit.SECONDS) should be(PerpetualStreamMatValueSpecHelper.payload)
    }
  }

  it should "return a Sink[T, NotUsed] when the referenced perpetualstream's " +
    "mat value is a product where the first 'element' is Sink[T, NotUsed]" in {
    import PerpetualStreamMatValueSpecHelper._
    val to = FiniteDuration(1, TimeUnit.SECONDS)
    useSystem(classOf[PerpStreamActors.GoodProductSinkMaterializingStream]) { case Right(actor) =>
      implicit val timeout = Timeout(to)
      val queue = Await.result((actor ? Queue).mapTo[LinkedBlockingQueue[Long]], to)
      queue.poll(1, TimeUnit.SECONDS) should be(PerpetualStreamMatValueSpecHelper.payload)
    }
  }

  it should "throw a ClassCastException when the referenced perpetualstream's " +
    "mat value is a product and the first 'element' is NOT a Sink[T, NotUsed]" in {
    import PerpetualStreamMatValueSpecHelper._
    useSystem(classOf[PerpStreamActors.BadProductSinkMaterializingStream]) { case Left(e) =>
      e shouldBe a[ClassCastException]
      e.getMessage should be(
        s"Materialized value mismatch. First element should be a Sink. Found ${classOf[Integer].getName}."
      )
    }
  }

  it should "throw a ClassCastException when the referenced perpetualstream's " +
    "mat value is anything else" in {
    import PerpetualStreamMatValueSpecHelper._
    useSystem(classOf[PerpStreamActors.IntProducingStream]) { case Left(e) =>
      e shouldBe a[ClassCastException]
      e.getMessage should be(
        s"Materialized value mismatch. Should be a Sink. Found ${classOf[Integer].getName}."
      )
    }
  }
}

object PerpetualStreamMatValueSpecHelper {

  import scala.concurrent.duration._
  val payload = 1L
  case object Queue
  implicit val timeout = Timeout(1.second)

  def useSystem[PC <: PerpStream[_]](perpStream: Class[PC])(fn: Either[Throwable, ActorRef] => Unit): Unit = {
    implicit val actorSystem = ActorSystem()
    implicit val materializer = ActorMaterializer()

    // TODO Is it ok to violate actor creation guidelines here?
    val perpRef = actorSystem.actorOf(Props(perpStream), "act1")
    val someRef = actorSystem.actorOf(Props(new SomeActor(perpRef)), "act2")

    Await.result(someRef ? payload, 1.second) match {
      case Success(_) => fn(Right(perpRef))
      case Failure(e) => fn(Left(e))
    }
    actorSystem.terminate()
  }

  trait PerpStream[T] extends PerpetualStream[T] {
    val batchQueue = new LinkedBlockingQueue[Long]()
    // https://stackoverflow.com/a/18469420
    override def receive= ({
      case Queue => sender() ! batchQueue
    }: Receive) orElse super.receive

    def addToBatch: Flow[Long, Long, NotUsed] = Flow[Long].map { l => batchQueue.add(l); l }
  }

  object PerpStreamActors {
    // TODO Can I not put these within the test:(:(
    // Seems like I can't due to some Akka actor construction rule.

    class SinkMaterializingStream extends PerpStream[Sink[Long, NotUsed]] {
      self ! Active
      override def streamGraph =
        MergeHub.source[Long].via(addToBatch).to(Sink.ignore)
    }

    /**
      * This has Sink[T, NotUsed] as the materialized value's first element.
      */
    class GoodProductSinkMaterializingStream extends PerpStream[(Sink[Long, NotUsed], Future[akka.Done])] {
      self ! Active
      override def streamGraph = {
        RunnableGraph.fromGraph(GraphDSL.create(MergeHub.source[Long], Sink.ignore)((_, _)) { implicit builder =>
          (mergeHubSource, sink) =>
            import GraphDSL.Implicits._
            mergeHubSource ~> addToBatch ~> sink
            ClosedShape
        })
      }
    }

    /**
      * This materializes a product that does NOT have Sink[T, NotUsed] as its first element.
      */
    class BadProductSinkMaterializingStream extends PerpStream[(Int, Any)] {
      self ! Active
      override def streamGraph = Source.single(1).toMat(Sink.ignore)((_, _) => (1, 2))
    }

    /**
      * This materializes an Int (neither a Sink[T, NotUsed] or a product).
      */
    class IntProducingStream extends PerpStream[Int] {
      self ! Active
      override def streamGraph = Source.single(1).toMat(Sink.ignore)((_, _) => 6)
    }
  }

  class SomeActor(perpStream: ActorRef) extends Actor with PerpetualStreamMatValue[Long] {
    implicit val mat = ActorMaterializer()

    override def actorLookup(name: String)(implicit refFactory: ActorRefFactory, timeout: Timeout) =
      perpStream

    override def receive: Receive = {
      case l: Long => sender() ! Try { Source.single(l).to(matValue("any-name-works-see-impl")).run() }
    }
  }
}