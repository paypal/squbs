package org.squbs.lifecycle

import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import akka.actor.{PoisonPill, ActorRef, Actor, ActorSystem}
import org.scalatest.{BeforeAndAfterAll, Matchers, FlatSpecLike}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Future

/**
 * Created by lma on 15-1-5.
 */
class GracefulStopHelperTest extends TestKit(ActorSystem("testSystem")) with FlatSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll {


  "GracefulStopHelper" should "work when stop failed" in {

    val actorRef = TestActorRef(new Actor with GracefulStopHelper {
      def receive: Actor.Receive = {
        case "Stop" =>
          defaultMidActorStop(Seq(self))
          sender() ! "Done"
      }

      override def gracefulStop(target: ActorRef, timeout: FiniteDuration, stopMessage: Any = PoisonPill): Future[Boolean] = {

        Future {
          throw new RuntimeException("BadMan")
        }

      }
    })

    val actor = actorRef.underlyingActor

    actorRef ! "Stop"
    expectMsg("Done")

  }

  "GracefulStopHelper" should "work" in {

    val actorRef = TestActorRef(new Actor with GracefulStopHelper {
      def receive: Actor.Receive = {
        case "Stop" =>
          defaultMidActorStop(Seq(self))
          sender() ! "Done"
      }

      override def gracefulStop(target: ActorRef, timeout: FiniteDuration, stopMessage: Any = PoisonPill): Future[Boolean] = {

        Future {
          true
        }

      }
    })

    val actor = actorRef.underlyingActor

    actorRef ! "Stop"
    expectMsg("Done")

  }

}
