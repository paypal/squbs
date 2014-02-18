package org.squbs.lifecycle

import akka.pattern.GracefulStopSupport
import scala.concurrent.duration.FiniteDuration
import akka.actor.{PoisonPill, ActorLogging, Actor}
import org.squbs.unicomplex.{StopRegistry, Unicomplex}
import org.squbs.util.threadless.Future
import scala.util.Success
import akka.actor.FSM.Failure
import java.util.concurrent.TimeUnit


/**
 * Created by zhuwang on 2/13/14.
 */

case object GracefulStop

/**
 * If actor want to be gracefully stopped during system shutdown,
 * it should mixin this trait.
 *
 * The trait will register the actor to a reaper actor once the actor is started.
 * Once the system is about to shutdown, the reaper will send a `GracefulStop`
 * message to the registered actor. The actor should implement the behavior in
 * its `receive` method.
 *
 * If the actor failed to stop after the `stopTimeout`, the reaper will send a
 * `PoisonPill`
 */
trait GracefulStopHelper extends GracefulStopSupport with ActorLogging{this: Actor =>

  // register to the reaper
  private val reaperActor = Unicomplex.reaperActor
  reaperActor ! StopRegistry(stopTimeout * 2)

  /**
   * Duration that the actor needs to finish the graceful stop.
   * Override it for customized timeout and it will be registered to the reaper
   * @return Duration
   */
  def stopTimeout: FiniteDuration =
    FiniteDuration(Unicomplex.config.getMilliseconds("stop-timeout"), TimeUnit.MICROSECONDS)

  /**
   * Default gracefully stop strategy
   * Simply propagate the `GracefulStop` message to all its children
   * After all its children get terminated it stops itself
   */
  def defaultStopStrategy: Unit = {

    def stopChildren(msg: Any) = {
      Future.sequence(context.children.map(
        gracefulStop(_, stopTimeout, msg).asInstanceOf[Future[Boolean]]
      ))
    }

    stopChildren(GracefulStop).onComplete({
      // all children has been terminated successfully
      // stop self
      case Success(result) => context.stop(self)

      // some children are not terminated in the timeout
      // send them PoisonPill again
      case Failure(e) => log.warning(s"[squbs] graceful stop failed on $self with $e")
        stopChildren(PoisonPill).onComplete(_ => {
          // don't care at this time
          context.stop(self)
        })
    })
  }

}