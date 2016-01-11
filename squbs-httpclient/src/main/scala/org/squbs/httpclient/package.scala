/*
 *  Copyright 2015 PayPal
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
package org.squbs

import java.util.concurrent.TimeUnit

import akka.actor.{ActorContext, ActorRefFactory, ActorSystem}
import akka.util.Timeout

import scala.concurrent.duration._
import scala.language.{implicitConversions, postfixOps}

package object httpclient {

  val defaultRequestTimeout: Option[Timeout] = None

  implicit def refFactoryToSystem(refFactory: ActorRefFactory): ActorSystem = refFactory match {
    case sys: ActorSystem => sys
    case ctx: ActorContext => ctx.system
    case other =>
      throw new IllegalArgumentException(s"Cannot create HttpClient with ActorRefFactory Impl ${other.getClass}")
  }

  implicit def toTimeout(d: Duration): Timeout = Timeout(d match {
    case f: FiniteDuration => f
    case Duration.Inf => Duration.fromNanos(Long.MaxValue)
    case _ => Duration.Zero
  })

  implicit class RequestToAskTimeout(val reqTimeout: Option[Timeout]) extends AnyVal {

    def askTimeout(implicit refFactory: ActorRefFactory): Timeout = {
      import Configuration._
      val Timeout(d) = reqTimeout getOrElse Timeout(requestTimeout(defaultHostSettings), TimeUnit.MILLISECONDS)
      if (d < 1.second) d + 100.millis
      else if (d > 10.second) d + 1.second
      else {
        (d.toMicros * 1.1).toLong micros
      }
    }
  }
}
