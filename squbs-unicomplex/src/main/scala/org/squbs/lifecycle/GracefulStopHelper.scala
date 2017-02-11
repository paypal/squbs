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

import akka.actor._
import akka.pattern.GracefulStopSupport
import org.squbs.unicomplex.{StopTimeout, Unicomplex}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

case object GracefulStop

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
      Future.sequence(dependencies.map(gracefulStop(_, timeout, msg)))
    }

    stopDependencies(GracefulStop).onComplete({
      // all dependencies has been terminated successfully
      // stop self
      case Success(result) => log.debug(s"All dependencies was stopped. Stopping self")
        if (context != null) context stop self

      // some dependencies are not terminated in the timeout
      // send them PoisonPill again
      case Failure(e) => log.warning(s"Graceful stop failed with $e in $timeout")
        stopDependencies(PoisonPill).onComplete(_ => {
          // don't care at this time
          if (context != null) context stop self
        })
    })
  }
}
