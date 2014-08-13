/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the CONTRIBUTING file distributed with this work for
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
package org.squbs.httpclient.dummy

import org.squbs.httpclient.endpoint.{Endpoint, EndpointResolver}
import org.squbs.httpclient.env.{DEV, Default, Environment}
import org.squbs.httpclient.HttpClientException
import DummyService._

object DummyServiceEndpointResolver extends EndpointResolver{

  override def resolve(svcName: String, env: Environment): Option[Endpoint] = {
    svcName match {
      case name => Some(Endpoint(dummyServiceEndpoint))
      case _    => None
    }
  }

  override def name: String = "DummyService"
}

object NotExistingEndpointResolver extends EndpointResolver {

  override def resolve(svcName: String, env: Environment): Option[Endpoint] = {
    svcName match {
      case name => Some(Endpoint("http://www.notexistingservice.com"))
      case _    => None
    }
  }

  override def name: String = "NotExistingService"
}

object DummyLocalhostResolver extends EndpointResolver {
  override def resolve(svcName: String, env: Environment = Default): Option[Endpoint] = {
    if (svcName == null && svcName.length <= 0) throw new HttpClientException(700, "Service name cannot be null")
    env match {
      case Default | DEV => Some(Endpoint("http://localhost:8080/" + svcName))
      case _   => throw new HttpClientException(701, "DummyLocalhostResolver cannot support " + env + " environment")
    }
  }

  override def name: String = "DummyLocalhostResolver"
}
