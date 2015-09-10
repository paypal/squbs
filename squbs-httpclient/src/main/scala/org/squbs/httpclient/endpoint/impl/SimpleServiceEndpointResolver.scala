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

package org.squbs.httpclient.endpoint.impl

import org.squbs.httpclient.env.Environment
import org.squbs.httpclient.Configuration
import org.squbs.httpclient.endpoint.{Endpoint, EndpointResolver}

case class SimpleServiceEndpointResolver(resolverName: String, serviceMap: Map[String, Option[Configuration]])
    extends EndpointResolver {

  serviceMap.foreach(service => Endpoint.check(service._1))

  override def resolve(svcName: String, env: Environment): Option[Endpoint] = {
    serviceMap.get(svcName) map {
      case Some(config) => Endpoint(svcName, config)
      case None => Endpoint(svcName, Configuration())
    }
  }

  override def name: String = resolverName
}
