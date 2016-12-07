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
package org.squbs.httpclient.dummy

import akka.actor.ActorSystem
import org.squbs.endpoint.{Endpoint, EndpointResolver}
import org.squbs.env.Environment

class DummyServiceEndpointResolver(dummyServiceEndpoint: String)
                                  (implicit system: ActorSystem) extends EndpointResolver {

  override def resolve(svcName: String, env: Environment): Option[Endpoint] = {
    if (svcName == name) {
      Some(Endpoint(dummyServiceEndpoint))
    } else {
      None
    }
  }

  override def name: String = "DummyService"
}

class NotExistingEndpointResolver(implicit system: ActorSystem) extends EndpointResolver {

  override def resolve(svcName: String, env: Environment): Option[Endpoint] = {
    if (svcName == name) {
      Some(Endpoint("http://www.notexistingservice.com"))
    } else {
      None
    }
  }

  override def name: String = "NotExistingService"
}
