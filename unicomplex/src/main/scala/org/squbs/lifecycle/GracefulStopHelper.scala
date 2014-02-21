package org.squbs.lifecycle

import akka.pattern.GracefulStopSupport
import scala.concurrent.duration.FiniteDuration
import akka.actor._
import org.squbs.unicomplex.{Supervisor, Unicomplex, StopRegistry}
import java.util.concurrent.TimeUnit
import scala.concurrent.Future
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

  import Unicomplex._

  /**
   * Tell the CubeSupervisor a reasonable timeout
   */
  Supervisor(self.path).foreach(_ ! StopRegistry(stopTimeout))

  /**
   * Duration that the actor needs to finish the graceful stop.
   * Override it for customized timeout and it will be registered to the reaper
   * Default to 5 seconds
   * @return Duration
   */
  def stopTimeout: FiniteDuration =
    FiniteDuration(config.getMilliseconds("stop-timeout"), TimeUnit.MILLISECONDS)

  /**
   * Default gracefully stop behavior for leaf level actors
   * (Actors only receive the msg as input and send out a result)
   * towards the `GracefulStop` message
   *
   * Simply stop itself
   */
  protected final def defaultLeafActorStop: Unit = context.stop(self)

  /**
   * Default gracefully stop behavior for middle level actors
   * (Actors rely on the results of other actors to finish their tasks)
   * towards the `GracefulStop` message
   *
   * Simply propagate the `GracefulStop` message to all actors
   * that should be stop ahead of this actor
   *
   * If some actors failed to respond to the `GracefulStop` message,
   * It will send `PoisonPill` again
   *
   * After all the actors get terminated it stops itself
   */
  protected final def defaultMidActorStop(dependencies: Iterable[ActorRef],
                                          timeout: FiniteDuration = stopTimeout): Unit = {
    implicit val executionContext = actorSystem.dispatcher

    def stopDependencies(msg: Any) = {
      Future.sequence(dependencies.map(gracefulStop(_, timeout, msg)))
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
