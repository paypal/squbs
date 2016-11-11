/*
 * Copyright 2015 PayPal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.squbs

import java.net.InetSocketAddress
import java.nio.channels.ServerSocketChannel

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.util.ByteString

import scala.concurrent.Future

package object unicomplex {

  // Remove this once Akka-Http exposes this test utility.
  def temporaryServerAddress(interface: String = "127.0.0.1"): InetSocketAddress = {
    val serverSocket = ServerSocketChannel.open()
    try {
      serverSocket.socket.bind(new InetSocketAddress(interface, 0))
      val port = serverSocket.socket.getLocalPort
      new InetSocketAddress(interface, port)
    } finally serverSocket.close()
  }

  def temporaryServerHostnameAndPort(interface: String = "127.0.0.1"): (InetSocketAddress, String, Int) = {
    val socketAddress = temporaryServerAddress(interface)
    (socketAddress, socketAddress.getHostName, socketAddress.getPort)
  }

  def extractEntityAsString(response: HttpResponse)
                           (implicit am: ActorMaterializer, system: ActorSystem): Future[String] = {
    import system.dispatcher
    response.entity.dataBytes.runFold(ByteString(""))(_ ++ _) map(_.utf8String)
  }

  def entityAsString(uri: String)(implicit am: ActorMaterializer, system: ActorSystem): Future[String] = {
    import system.dispatcher
    get(uri) flatMap extractEntityAsString
  }

  def entityAsStringWithHeaders(uri: String)(implicit am: ActorMaterializer, system: ActorSystem): Future[(String, Seq[HttpHeader])] = {
    import system.dispatcher
    get(uri) flatMap( response => extractEntityAsString(response) map((_, response.headers)))
  }

  def entityAsInt(uri: String)(implicit am: ActorMaterializer, system: ActorSystem): Future[Int] = {
    import system.dispatcher
    entityAsString(uri) map (s => s.toInt)
  }

  def get(uri: String)(implicit am: ActorMaterializer, system: ActorSystem): Future[HttpResponse] = {
    Http().singleRequest(HttpRequest(uri = Uri(uri)))
  }

  def post(uri: String, e: RequestEntity)(implicit am: ActorMaterializer, system: ActorSystem): Future[HttpResponse] = {
    Http().singleRequest(HttpRequest(method = HttpMethods.POST, uri = Uri(uri), entity = e))
  }

  def put(uri: String)(implicit am: ActorMaterializer, system: ActorSystem): Future[HttpResponse] = {
    Http().singleRequest(HttpRequest(method = HttpMethods.PUT, uri = Uri(uri)))
  }
}
