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

package org.squbs.pipeline

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.io.IO
import akka.io.Tcp.Unbound
import spray.can.Http

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}
import scala.util.Success

trait MockHttpServerSupport {

  private var mockServer: ActorRef = ActorRef.noSender
  val onStart: Promise[Http.Bound] = Promise()
  val onStop: Promise[Unbound] = Promise()
  import Timeouts._

  def startMockServer(host: String = "127.0.0.1", port: Int = 9090, handler: Option[ActorRef] = None)(implicit system: ActorSystem): Unit = {

    mockServer = system.actorOf(Props(new MockHTTPServer(handler.getOrElse(system.actorOf(Props(new Handler))))))
    IO(Http) tell(Http.Bind(mockServer, interface = host, port = port), mockServer)
    Await.result(onStart.future, awaitMax)
    println("MockHTTPServer started..")

  }

  def stopMockServer(implicit system: ActorSystem): Unit = {
    mockServer ! Http.Unbind
    Await.result(onStop.future, awaitMax)
    println("MockHTTPServer stopped..")
  }

  def handleRequest(implicit ctx: akka.actor.ActorContext): Actor.Receive = {
    case _ => throw new UnsupportedOperationException("Must implement handleRequest!")
  }

  private class Handler extends Actor {

    def receive: Actor.Receive = handleRequest(context)

  }

  private class MockHTTPServer(handler: ActorRef) extends Actor {

    def receive: Actor.Receive = {

      case evt: Http.Bound =>
        println("MockHTTPServer: Http.Bound")
        context.become(boundReceive(sender()))
        onStart.complete(Success(evt))

      case other => println("Unknown Message: " + other)

    }

    def boundReceive(listener: ActorRef): Actor.Receive = {
      case _: Http.Connected =>
        println("MockHTTPServer: Http.Connected")
        sender ! Http.Register(handler)

      case _: Http.ConnectionClosed =>
        println("MockHTTPServer: Http.ConnectionClosed")

      case evt: Unbound =>
        println("MockHTTPServer: Unbound")
        context.stop(handler)
        context.stop(self)
        onStop.complete(Success(Unbound))

      case Http.Unbind => listener ! Http.Unbind
    }
  }

}
