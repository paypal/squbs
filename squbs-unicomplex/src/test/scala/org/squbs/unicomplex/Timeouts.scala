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

import org.apache.pekko.util.Timeout

import scala.concurrent.duration._
import scala.language.postfixOps

object Timeouts {

  implicit val askTimeout = Timeout(30.seconds)

  val awaitMax = 60.seconds

  // This is used for tests that would keep the startup waiting, and expect a startup timeout anyway. Set it low.
  val startupTimeout = Timeout(5.seconds)
}
