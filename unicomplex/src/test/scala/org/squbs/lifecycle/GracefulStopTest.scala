package org.squbs.unicomplex

import akka.actor._
import akka.pattern.pipe
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{WordSpec, BeforeAndAfterAll}
import org.scalatest.matchers.ClassicMatchers
import org.squbs.lifecycle.{TaskDispatcherGracefulStop, GracefulStop, GracefulStopHelper}
import scala.concurrent.duration._
import scala.concurrent.Future
import org.squbs.pattern.Aggregator
import scala.collection.mutable

/**
 * Created by zhuwang on 2/18/14.
 */

object LifecycleSpec {

  class CubeAggregateActor extends Actor with Aggregator with TaskDispatcherGracefulStop {

    import Unicomplex._

    implicit val executionContext = actorSystem.dispatcher

    val handler = expect {
      case GracefulStop => new StopAggregator(GracefulStop)

      case x: String if x.equals("hello") => new CubeAggregator(sender, x)
    }

    class CubeAggregator(originalSender: ActorRef, x: Any) {

      private val results = mutable.ArrayBuffer.empty[String]

      getResultFromCubeA
      getResultFromCubeB

      def getResultFromCubeA = {
        context.actorSelection("/user/cubeASupervisor/cubeA") ! x
        expectOnce {
          case "AA" => results += "AA"
            collectResult()
        }
      }

      def getResultFromCubeB = {
        context.actorSelection("/user/cubeBSupervisor/cubeB") ! x
        expectOnce {
          case "BB" => results += "BB"
            collectResult()
        }
      }

      def collectResult(force: Boolean = false): Unit = {
        if (results.size == 2 || force) {
          originalSender ! results.mkString("")
        }
      }
    }

    class StopAggregator(stopMsg: Any) {

      def stop = stopMsg match {
        case GracefulStop =>
          unexpect(handler)

          Future.sequence(Iterable(
            context.actorSelection("/user/cubeASupervisor/cubeA").resolveOne(1 seconds),
            context.actorSelection("/user/cubeBSupervisor/cubeB").resolveOne(1 seconds)
          )).pipeTo(self)

          expectOnce {
            case dependencies: Iterable[ActorRef] => defaultMidActorStop(dependencies)

            case Status.Failure(e) => log.error(e, "failed to stop dependencies")
          }
      }
    }
  }

  class CubeTaskActorA extends Actor with GracefulStopHelper {

    override def stopTimeout = 8 seconds

    def receive = {
      case GracefulStop => defaultMidActorStop(context.children)

      case "A" => context.actorSelection("/user/cubeAggregatorSupervisor/cubeAggregator") ! "AA"

      case x => context.actorOf(Props[DummyExecuteActor]) ! ("A", 300)
    }
  }

  class CubeTaskActorB extends Actor with GracefulStopHelper {

    override def stopTimeout = 8 seconds

    def receive = {
      case GracefulStop => defaultMidActorStop(context.children)

      case "B" => context.actorSelection("/user/cubeAggregatorSupervisor/cubeAggregator") ! "BB"

      case x => context.actorOf(Props[DummyExecuteActor]) ! ("B", 500)
    }
  }

  class DummyExecuteActor extends Actor with GracefulStopHelper{
    def receive = {
      case GracefulStop => defaultLeafActorStop

      case (msg: String, duration: Int) =>
        Thread.sleep(duration)
        sender ! msg
        context.stop(self)

      case other => println(s"Unknown msg: $other")
    }
  }
}

class LifecycleSpec(_system: ActorSystem) extends  TestKit(_system) with ImplicitSender with WordSpec
  with ClassicMatchers with BeforeAndAfterAll {

  def this() = {
    this(Unicomplex.actorSystem)
  }

  import LifecycleSpec._

  override def beforeAll {
    Bootstrap.main(Array.empty[String])
    system.actorOf(Props[CubeSupervisor], "cubeASupervisor") ! StartCubeActor(Props[CubeTaskActorA], "cubeA")
    system.actorOf(Props[CubeSupervisor], "cubeBSupervisor") ! StartCubeActor(Props[CubeTaskActorB], "cubeB")
    system.actorOf(Props[CubeSupervisor], "cubeAggregatorSupervisor") ! StartCubeActor(Props[CubeAggregateActor],
      "cubeAggregator")
  }

  "The CubeAggregator" must {

    "send back the aggregated result" in {
      val cubeAggregator = system.actorSelection("/user/cubeAggregatorSupervisor/cubeAggregator")
      cubeAggregator ! "hello"
      expectMsg("AABB")
    }

    "get correct result for multiple requests" in {
      val cubeAggregator = system.actorSelection("/user/cubeAggregatorSupervisor/cubeAggregator")
      (1 to 10).foreach(_ => {
        cubeAggregator ! "hello"
        expectMsg("AABB")
      })
    }
  }

  "The Reaper" must {

    "shutdown the system gracefully" in {
      val cubeAggregator = system.actorSelection("/user/cubeAggregatorSupervisor/cubeAggregator")
      (1 to 10).foreach(_ => {
        cubeAggregator ! "hello"
      })
      system.actorSelection("/user/reaper") ! GracefulStop

      Thread.sleep(500)
      cubeAggregator ! "hello"
      receiveN(10, 10 seconds)
      awaitCond({system.isTerminated}, 20 seconds, 2 seconds)
    }
  }
}