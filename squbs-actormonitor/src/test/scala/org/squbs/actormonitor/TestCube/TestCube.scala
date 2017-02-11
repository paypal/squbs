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

package org.squbs.actormonitor.testcube

import akka.actor.Actor


class TestActor extends Actor {
  def receive = {
    case x => sender ! x
  }
}

class TestActorWithRoute extends Actor {
  def receive = {
    case x => sender ! x
  }
}

class TestActor1 extends Actor {
  def receive = {
    case x =>context.stop(self)
  }
}
