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

package org.squbs.httpclient.demo

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.CircuitBreakerOpenException
import org.squbs.httpclient.{CircuitBreakerSettings, _}
import org.squbs.httpclient.endpoint.{Endpoint, EndpointRegistry, EndpointResolver}
import org.squbs.httpclient.env.Environment
import spray.http.HttpResponse

import scala.concurrent.duration._

object CircuitBreakerMain2 extends App{

  implicit val system = ActorSystem("CircuitBreakerMain2")

  EndpointRegistry(system).register(new EndpointResolver{

    override def resolve(svcName: String, env: Environment): Option[Endpoint] = {
      val config = Configuration().copy(settings = Settings(hostSettings = Configuration.defaultHostSettings.copy(maxRetries = 0),
        circuitBreakerConfig = CircuitBreakerSettings().copy(callTimeout = 1 second)))
      if (svcName == name) Some(Endpoint("http://localhost:8888", config)) else None
    }

    override def name: String = "DummyService"
  })

  while(true){
    Thread.sleep(2000)
    system.actorOf(Props(new CircuitBreakerActor(system))) ! CircuitBreakerMessage
  }
}

case class CircuitBreakerActor(actorSystem: ActorSystem) extends Actor {

  override def receive: Receive = {
    case CircuitBreakerMessage =>
      val httpClientManager = HttpClientManager(actorSystem).httpClientManager
      httpClientManager ! HttpClientManagerMessage.Get("DummyService")
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