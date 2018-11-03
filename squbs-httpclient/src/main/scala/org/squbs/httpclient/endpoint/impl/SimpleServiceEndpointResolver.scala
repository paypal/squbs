/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the AUTHORS file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.squbs.httpclient.endpoint.impl

import org.squbs.httpclient.env.Environment
import org.squbs.httpclient.Configuration
import org.squbs.httpclient.endpoint.{Endpoint, EndpointResolver}

case class SimpleServiceEndpointResolver(resolverName: String, serviceMap: Map[String, Configuration]) extends EndpointResolver{

  serviceMap.foreach(service => Endpoint.check(service._1))

  override def resolve(svcName: String, env: Environment): Option[Endpoint] = {
    serviceMap.contains(svcName) match {
      case true =>
        val config = if (serviceMap.get(svcName) == Some(null)) Configuration()
                     else serviceMap.get(svcName).getOrElse(Configuration())
        Some(Endpoint(svcName, config))
      case false =>
        None
    }
  }

  override def name: String = resolverName
}
