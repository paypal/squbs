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

package org.squbs.unicomplex

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.pattern.ask
import org.apache.pekko.util.Timeout
import org.squbs.lifecycle.GracefulStop
import org.squbs.util.ConfigUtil._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scala.language.postfixOps

object Bootstrap extends App {

  println("Booting unicomplex")

  // Note, the config directories may change during extension init. It is important to re-read the full config
  // for the actor system start.
  UnicomplexBoot { (name, config) => ActorSystem(name, config) }
    .scanResources()
    .initExtensions
    .stopJVMOnExit
    .start()

  sys.addShutdownHook { Shutdown.shutdown() }
}


object Shutdown extends App {
  shutdown(actorSystemName = args.headOption)

  def shutdown(delayParameter: Option[FiniteDuration] = None, actorSystemName: Option[String] = None): Unit = {
    val name = actorSystemName getOrElse {
      val preConfig = UnicomplexBoot.getFullConfig(None)
      preConfig.getString("squbs.actorsystem-name")
    }
    UnicomplexBoot.actorSystems.get(name) map { actorSystem =>
      val delay = delayParameter orElse
        actorSystem.settings.config.getOption[FiniteDuration]("squbs.shutdown-delay") getOrElse Duration.Zero
      implicit val squbsStopTimeout = Timeout(actorSystem.settings.config.get[FiniteDuration]("squbs.default-stop-timeout", 3.seconds))
      val systemState = (Unicomplex(actorSystem).uniActor ? SystemState).mapTo[LifecycleState]

      import actorSystem.dispatcher

      systemState.onComplete {
        case Success(Stopping | Stopped) | Failure(_) => // Termination already started/happened.  Do nothing!
        case _ => actorSystem.scheduler.scheduleOnce(delay, Unicomplex(name), GracefulStop)
      }

      Try {
        Await.ready(actorSystem.whenTerminated, delay + squbsStopTimeout.duration + (1 second))
      }
    }
  }
}
