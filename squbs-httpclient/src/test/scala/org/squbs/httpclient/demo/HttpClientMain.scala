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
package org.squbs.httpclient.demo

import akka.actor.ActorSystem
import org.squbs.httpclient._
import org.squbs.httpclient.endpoint.{Endpoint, EndpointRegistry, EndpointResolver}
import org.squbs.httpclient.env.{Default, Environment}
import spray.http.StatusCodes
import scala.util.{Failure, Success}

/**
 * Traditional API using get
 */
object HttpClientMain1 extends App with HttpClientTestKit {

  private implicit val system = ActorSystem("HttpClientMain1")
  import system.dispatcher
  EndpointRegistry.register(new GoogleMapAPIEndpointResolver)

  val response = HttpClientFactory.get("googlemap").get("/api/elevation/json?locations=27.988056,86.925278&sensor=false")
  response onComplete {
    case Success(HttpResponseWrapper(StatusCodes.OK, Right(res))) =>
      println("Success, response entity is: " + res.entity.asString)
      shutdownActorSystem
    case Success(HttpResponseWrapper(code, _)) =>
      println("Success, the status code is: " + code)
      shutdownActorSystem
    case Failure(e) =>
      println("Failure, the reason is: " + e.getMessage)
      shutdownActorSystem
  }
}

/**
 * Traditional API using getEntity
 */
object HttpClientMain2 extends App with HttpClientTestKit{

  private implicit val system = ActorSystem("HttpClientMain2")
  import system.dispatcher
  EndpointRegistry.register(new GoogleMapAPIEndpointResolver)
  import org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol._

  val response = HttpClientFactory.get("googlemap").getEntity[GoogleApiResult[Elevation]]("/api/elevation/json?locations=27.988056,86.925278&sensor=false")
  response onComplete {
    case Success(HttpResponseEntityWrapper(StatusCodes.OK, Right(res), rawHttpResponse)) =>
      println("Success, response status is: " + res.status + ", elevation is: " + res.results.head.elevation + ", location.lat is: " + res.results.head.location.lat)
      shutdownActorSystem
    case Success(HttpResponseEntityWrapper(code, _, _)) =>
      println("Success, the status code is: " + code)
      shutdownActorSystem
    case Failure(e) =>
      println("Failure, the reason is: " + e.getMessage)
      shutdownActorSystem
  }
}

class GoogleMapAPIEndpointResolver extends EndpointResolver {
  override def resolve(svcName: String, env: Environment = Default): Option[Endpoint] = {
    if (svcName == name)
      Some(Endpoint("http://maps.googleapis.com/maps"))
    else
      None
  }

  override def name: String = "googlemap"
}

case class Elevation(location: Location, elevation: Double)
case class Location(lat: Double, lng: Double)
case class GoogleApiResult[T](status: String, results: List[T])
