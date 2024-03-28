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

package org.squbs.lifecycle

import java.util.concurrent.TimeUnit

import org.apache.pekko.actor._
import org.apache.pekko.pattern.GracefulStopSupport
import org.squbs.unicomplex.{StopTimeout, Unicomplex}
import org.squbs.util.DurationConverters

import scala.compat.java8.FutureConverters._
import scala.compat.java8.FunctionConverters._
import scala.jdk.CollectionConverters._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

case object GracefulStop {
  def getInstance: GracefulStop.type = this
}

/**
 * The trait provides some helper methods to support graceful stop of an actor
 * in Squbs framework
 *
 * Once you mix this trait in your actor, you can override stopTimeout to indicate
 * how much time this actor may need to stop it self.
 *
 * When the actor gets created, it will send the `stopTimeout` to its parent.
 * You can have the logic in the parent actor to decide to spend how much time to
 * stop its children
 *
 * If you want your actor to stop gracefully, you should put your stop logic in the
 * `receive` method responding to the `GracefulStop` message
 */
trait GracefulStopHelper extends GracefulStopSupport with ActorLogging{this: Actor =>

  import Unicomplex._

  if (self.path.elements.size > 2) {
    // send StopTimeout to parent
    context.parent ! StopTimeout(stopTimeout)
  }

  import context.dispatcher

  /**
   * Duration that the actor needs to finish the graceful stop.
   * Override it for customized timeout and it will be registered to the reaper
   * Default to 5 seconds
   * @return Duration
   */
  def stopTimeout: FiniteDuration =
    FiniteDuration(config.getDuration("default-stop-timeout", TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)

  /**
   * Default gracefully stop behavior for leaf level actors
   * (Actors only receive the msg as input and send out a result)
   * towards the `GracefulStop` message
   *
   * Simply stop itself
   */
  protected final def defaultLeafActorStop: Unit = {
    log.debug(s"Stopping self")
    context stop self
  }

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
                                          timeout: FiniteDuration = stopTimeout / 2): Unit = {

    def stopDependencies(msg: Any) = {
      // Note: We need to call the Java API as that will again call the Scala API.
      // Any overrides will now come into picture.
      Future.sequence(dependencies.map(gracefulStop(_, DurationConverters.toJava(timeout), msg).toScala))
    }

    stopDependencies(GracefulStop).onComplete({
      // all dependencies has been terminated successfully
      // stop self
      case Success(result) => log.debug(s"All dependencies was stopped. Stopping self")
        if (context != null) context stop self

      // some dependencies are not terminated in the timeout
      // send them PoisonPill again
      case Failure(e) => log.warning(s"Graceful stop failed with $e in $timeout")
        stopDependencies(PoisonPill).onComplete { _ =>
          // don't care at this time
          if (context != null) context stop self
        }
    })
  }

  /**
   * Java API stopping non-leaf actors.
   * @param dependencies All non-leaf actors to be stopped.
   * @param timeout The timeout.
   */
  protected final def defaultMidActorStop(dependencies: java.util.List[ActorRef], timeout: java.time.Duration): Unit =
    defaultMidActorStop(dependencies.asScala, DurationConverters.toScala(timeout))

  /**
   * Java API stopping non-leaf actors with default timeout.
   * @param dependencies All non-leaf actors to be stopped.
   */
  protected final def defaultMidActorStop(dependencies: java.util.List[ActorRef]): Unit =
    defaultMidActorStop(dependencies.asScala)

  /**
   * Java API for gracefulStop.
   * @param target The target actor to stop.
   * @param timeout The timeout.
   * @param stopMessage The message to send to the actor for stopping.
   * @return A CompletionStage carrying `true` for success stopping the target.
   */
  protected def gracefulStop(target: ActorRef, timeout: java.time.Duration, stopMessage: Any):
      java.util.concurrent.CompletionStage[java.lang.Boolean] =
    gracefulStop(target, DurationConverters.toScala(timeout), stopMessage).toJava
      .thenApply(asJavaFunction((t: Boolean) => t:java.lang.Boolean))

  /**
   * Java API for gracefulStop using the default message - PoisonPill.
   * @param target The target actor to stop.
   * @param timeout The timeout.
   * @return A CompletionStage carrying `true` for success stopping the target.
   */
  protected final def gracefulStop(target: ActorRef, timeout: java.time.Duration):
      java.util.concurrent.CompletionStage[java.lang.Boolean] =
    gracefulStop(target, timeout, PoisonPill)
}

/**
 * Java API for creating actors with GracefulStopHelper.
 */
abstract class ActorWithGracefulStopHelper extends AbstractActor with GracefulStopHelper {

  /**
   * Override getStopTimeout to set a custom stop timeout.
   * @return The timeout, in milliseconds to allow for stopping the server.
   */
  def getStopTimeout: Long = super.stopTimeout.toMillis

  override final def stopTimeout: FiniteDuration = getStopTimeout.millis

}
