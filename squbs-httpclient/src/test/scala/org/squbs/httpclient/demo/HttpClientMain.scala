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
package org.squbs.httpclient.demo

import akka.actor.{ActorRef, Actor, Props, ActorSystem}
import org.squbs.httpclient._
import org.squbs.httpclient.endpoint.EndpointRegistry
import spray.http.{HttpResponse, StatusCodes}
import scala.util.{Failure, Success}
import org.squbs.httpclient.dummy.GoogleAPI.{Elevation, GoogleApiResult, GoogleMapAPIEndpointResolver}
import org.squbs.httpclient.pipeline.HttpClientUnmarshal

/**
 * Traditional API using get
 */
object HttpClientDemo1 extends App with HttpClientTestKit {

  private implicit val system = ActorSystem("HttpClientDemo1")
  import system.dispatcher
  EndpointRegistry.register(GoogleMapAPIEndpointResolver)

  val response = HttpClientFactory.get("googlemap").get("/api/elevation/json?locations=27.988056,86.925278&sensor=false")
  response onComplete {
    case Success(res@HttpResponse(StatusCodes.OK, _, _, _)) =>
      println("Success, response entity is: " + res.entity.asString)
      shutdownActorSystem
    case Success(res@HttpResponse(code, _, _, _)) =>
      println("Success, the status code is: " + code)
      shutdownActorSystem
    case Failure(e) =>
      println("Failure, the reason is: " + e.getMessage)
      shutdownActorSystem
  }
}

/**
 * Traditional API using get and unmarshall value
 */
object HttpClientDemo2 extends App with HttpClientTestKit{

  private implicit val system = ActorSystem("HttpClientDemo2")
  import system.dispatcher
  EndpointRegistry.register(GoogleMapAPIEndpointResolver)
  import org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol._
  import HttpClientUnmarshal._

  val response = HttpClientFactory.get("googlemap").get("/api/elevation/json?locations=27.988056,86.925278&sensor=false")
  response onComplete {
    case Success(res@HttpResponse(StatusCodes.OK, _, _, _)) =>
      val obj = res.unmarshalTo[GoogleApiResult[Elevation]]
      obj match {
        case Success(data) =>
          println("Success, elevation is: " + data.results.head.elevation)
        case Failure(e) =>
          println("Failure, the reason is: " + e.getMessage)
      }
      shutdownActorSystem
    case Success(res@HttpResponse(code, _, _, _)) =>
      println("Success, the status code is: " + code)
      shutdownActorSystem
    case Failure(e) =>
      println("Failure, the reason is: " + e.getMessage)
      shutdownActorSystem
  }
}

/**
 * Message Based API
 */
object HttpClientDemo3 extends App with HttpClientTestKit {

  private implicit val system = ActorSystem("HttpClientDemo3")
  EndpointRegistry.register(GoogleMapAPIEndpointResolver)

  system.actorOf(Props(new HttpClientDemoActor)) ! GoogleApiCall
}

case class HttpClientDemoActor(implicit system: ActorSystem) extends Actor with HttpClientTestKit {
  override def receive: Receive = {
    case GoogleApiCall =>
      val httpClientManager = HttpClientManager(system).httpClientManager
      httpClientManager ! HttpClientManagerMessage.Get("googlemap")
    case httpClientActorRef: ActorRef =>
      httpClientActorRef ! HttpClientActorMessage.Get("/api/elevation/json?locations=27.988056,86.925278&sensor=false")
    case res@ HttpResponse(StatusCodes.OK, _, _, _) =>
      println("Success, response entity is: " + res.entity.asString)

      import HttpClientUnmarshal._
      import org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol._
      val unmarshalData = res.unmarshalTo[GoogleApiResult[Elevation]]
      unmarshalData match {
        case Success(data) =>
          println("elevation is: " + data.results.head.elevation + ", location.lat is: " + data.results.head.location.lat)
        case Failure(e)     =>
          println("unmarshal error is:" + e.getMessage)
      }

      shutdownActorSystem
    case HttpResponse(code, _, _, _) =>
      println("Success, the status code is: " + code)
      shutdownActorSystem
    case akka.actor.Status.Failure(e) =>
      println("Failure, the reason is: " + e.getMessage)
      shutdownActorSystem
  }
}

case object GoogleApiCall
