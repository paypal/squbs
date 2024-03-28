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

package org.squbs.resolver

import java.net.URI

import org.apache.pekko.actor.ActorSystem
import org.squbs.env.{DEV, Default, Environment}

class DummyServiceResolver(implicit system: ActorSystem) extends Resolver[URI] {

  override def resolve(svcName: String, env: Environment): Option[URI] = {
    if (svcName == name) Some(URI.create("http://www.google.com"))
    else None
  }

  override def name: String = "DummyService"
}

class DummyLocalhostResolver(implicit system: ActorSystem) extends Resolver[URI] {

  override def resolve(svcName: String, env: Environment = Default): Option[URI] = {
    require(svcName != null, "Service name cannot be null")
    require(svcName.length > 0, "Service name must not be blank")

    env match {
      case Default | DEV => Some(URI.create("http://localhost:8080"))
      case _ => throw new RuntimeException("DummyLocalhostResolver cannot support " + env + " environment")
    }
  }

  override def name: String = "DummyLocalhostResolver"
}
