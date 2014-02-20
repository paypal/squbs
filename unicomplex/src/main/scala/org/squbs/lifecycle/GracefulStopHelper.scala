package org.squbs.lifecycle

import akka.pattern.GracefulStopSupport
import scala.concurrent.duration.FiniteDuration
import akka.actor._
import org.squbs.unicomplex.Unicomplex
import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import org.squbs.unicomplex.StopRegistry
import scala.util.Success
import scala.util.Failure


/**
 * Created by zhuwang on 2/13/14.
 */

case object GracefulStop

/**
 * The trait provides some helper methods to support graceful stop of an actor
 * in Squbs framework
 */
trait GracefulStopHelper extends GracefulStopSupport with ActorLogging{this: Actor =>

  /**
   * Register the actor to the global reaper
   */
  private[lifecycle] def registerToReaper: Unit = {
    val reaperActor = Unicomplex.reaperActor
    reaperActor ! StopRegistry(stopTimeout * 2)
  }

  /**
   * Duration that the actor needs to finish the graceful stop.
   * Override it for customized timeout and it will be registered to the reaper
   * @return Duration
   */
  def stopTimeout: FiniteDuration =
    FiniteDuration(Unicomplex.config.getMilliseconds("stop-timeout"), TimeUnit.MILLISECONDS)

  /**
   * Default gracefully stop behavior for leaf level actors
   * (Actors only receive the msg as input and send out a result)
   * towards the `GracefulStop` message
   *
   * Simply stop itself
   */
  protected def defaultLeafActorStop: Unit = context.stop(self)

  /**
   * Default gracefully stop behavior for middle level actors
   * (Actors rely on the results of other actors to finish their tasks)
   * towards the `GracefulStop` message
   *
   * Simply propagate the `GracefulStop` message to all actors
   * that should be stop ahead of this actor
   * After all the actors get terminated it stops itself
   */
  protected def defaultMidActorStop(dependencies: Iterable[ActorRef]): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global

    def stopDependencies(msg: Any) = {
      Future.sequence(dependencies.map(gracefulStop(_, stopTimeout, msg)))
    }

    stopDependencies(GracefulStop).onComplete({
      // all children has been terminated successfully
      // stop self
      case Success(result) => context.stop(self)

      // some children are not terminated in the timeout
      // send them PoisonPill again
      case Failure(e) => log.warning(s"Graceful stop failed with $e")
        stopDependencies(PoisonPill).onComplete(_ => {
          // don't care at this time
          context.stop(self)
        })
    })
  }
}

/**
 * This trait is for the top level actors
 * who dispatch the tasks to other actors
 *
 * The trait will register the actor to the reaper automatically.
 * Once the system is about to shutdown, the reaper will send a `GracefulStop`
 * message to all registered actors. The actors should implement the behavior in
 * its `receive` method.
 *
 * If the actor failed to stop after the `stopTimeout`, the reaper will send a
 * `PoisonPill`
 */
trait TaskDispatcherGracefulStop extends GracefulStopHelper {this: Actor =>

  registerToReaper

}