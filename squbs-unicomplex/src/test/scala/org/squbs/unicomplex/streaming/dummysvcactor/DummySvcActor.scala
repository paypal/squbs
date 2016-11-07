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

package org.squbs.unicomplex.streaming.dummysvcactor

import akka.actor.{ActorRef, ActorLogging, Actor}
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import org.squbs.unicomplex.WebContext


case object RegisterTimeoutHandler

case object GetWebContext

class DummySvcActor extends Actor with WebContext with ActorLogging {

  var timeoutListeners = Seq.empty[ActorRef]

  implicit val am = ActorMaterializer()
  import context.dispatcher

  def receive: Receive = {
    case req@HttpRequest(_, Uri(_, _, Path("/dummysvcactor/ping"), _, _), _, _, _) =>
      log.debug("Received request " + req.uri)
      sender() ! HttpResponse(StatusCodes.OK, entity = "pong")

    case req @ HttpRequest(_, Uri(_, _, Path("/dummysvcactor/chunks"), _, _), _, _, _) =>

      var chunkCount = 0L
      var byteCount = 0L

      val future = req.entity.dataBytes.filter(_.length > 0).runForeach{ b =>
          chunkCount += 1
          byteCount += b.length
      }

      val origSender = sender()

      future onSuccess {
        case byteCount => origSender ! HttpResponse(StatusCodes.OK, entity = s"Received $chunkCount chunks and $byteCount bytes.")
      }

    case req @ HttpRequest(_, Uri(_, _, Path("/dummysvcactor/timeout"), _, _), _, _, _) =>

    case req @ HttpRequest(_, Uri(_, _, Path("/dummysvcactor/chunktimeout"), _, _), _, _, _) =>
      req.entity.dataBytes.runWith(Sink.ignore)

    // TODO Missing feature?  How does this actor get notified if an akka-http request-timeout happens?
//    case t: Timedout =>
//      timeoutListeners foreach { _ ! t }
//
//    case RegisterTimeoutHandler =>
//      timeoutListeners = timeoutListeners :+ sender()

    case GetWebContext => sender() ! webContext
  }
}
