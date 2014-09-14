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

import org.squbs.httpclient._
import akka.pattern.CircuitBreakerOpenException
import scala.concurrent.duration._
import akka.actor.{Props, ActorRef, Actor, ActorSystem}
import org.squbs.httpclient.endpoint.{Endpoint, EndpointResolver, EndpointRegistry}
import org.squbs.httpclient.env.Environment
import org.squbs.httpclient.CircuitBreakerConfiguration
import scala.Some
import spray.http.HttpResponse

object CircuitBreakerMain2 extends App{

  implicit val actorSystem = ActorSystem("CircuitBreakerMain2")
  implicit val ec = actorSystem.dispatcher

  EndpointRegistry.register(new EndpointResolver{

    override def resolve(svcName: String, env: Environment): Option[Endpoint] = {
      svcName match {
        case name =>
          val config = Configuration().copy(hostSettings = Configuration.defaultHostSettings.copy(maxRetries = 0), circuitBreakerConfig = CircuitBreakerConfiguration().copy(callTimeout = 1 second))
          Some(Endpoint("http://localhost:8888", config))
        case _    => None
      }
    }

    override def name: String = "DummyService"
  })

  while(true){
    Thread.sleep(2000)
    actorSystem.actorOf(Props(new CircuitBreakerActor(actorSystem))) ! CircuitBreakerMessage
  }
}

case class CircuitBreakerActor(actorSystem: ActorSystem) extends Actor {

  override def receive: Receive = {
    case CircuitBreakerMessage =>
      val httpClientManager = HttpClientManager(actorSystem).httpClientManager
      httpClientManager ! HttpClientManagerMessage.Get("DummyService")(actorSystem)
    case ref: ActorRef =>
      ref ! HttpClientActorMessage.Get("/view")
    case httpResponse: HttpResponse =>
      println("call success, body is:" + httpResponse.entity.data.asString)
    case akka.actor.Status.Failure(e: CircuitBreakerOpenException) =>
      println("circuitBreaker open! remaining time is:" + e.remainingDuration.toSeconds)
    case akka.actor.Status.Failure(e: Throwable) =>
      println("exception is:" + e.getMessage)
    case other =>
      println("test other exception is:" + other + ",name is:" + other.getClass.getCanonicalName)
  }
}

case object CircuitBreakerMessage
