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
package org.squbs.actorregistry.testcube

import org.apache.pekko.actor.Actor

case class TestRequest(msg: String)
case class TestRequest1(msg: String)
case class TestResponse(msg: String)


class TestActor extends Actor {
  def receive = {
    case TestRequest(msg)  =>
      sender() ! TestResponse(msg)
    case TestRequest =>
      sender() ! TestResponse
  }
}

class TestActor1 extends Actor {
  def receive = {
    case TestRequest1(msg)  =>
      sender() ! TestResponse(msg)
    case TestRequest1 =>
      sender() ! TestResponse
  }
}

