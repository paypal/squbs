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

import akka.actor.{ActorContext, ActorSystem, ActorRefFactory}
import akka.util.Timeout

import scala.concurrent.duration._
import scala.language.{implicitConversions, postfixOps}

package object httpclient {

  implicit def refFactoryToSystem(refFactory: ActorRefFactory): ActorSystem = refFactory match {
    case sys: ActorSystem => sys
    case ctx: ActorContext => ctx.system
    case other =>
      throw new IllegalArgumentException(s"Cannot create HttpClient with ActorRefFactory Impl ${other.getClass}")
  }

  implicit class RequestToAskTimeout(val requestTimeout: Timeout) extends AnyVal {

    def askTimeout: Timeout = {
      val Timeout(d) = requestTimeout
      if (d < 1.second) d + 100.millis
      else if (d > 10.second) d + 1.second
      else {
        (d.toMicros * 1.1).toLong micros
      }
    }
  }
}
