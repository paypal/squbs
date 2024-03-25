/*
 * Copyright 2017 PayPal
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

package org.squbs.unicomplex.dummysvcactor

import org.apache.pekko.actor.{Actor, ActorLogging, ActorRef}
import org.apache.pekko.http.scaladsl.model.Uri.Path
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.stream.scaladsl.Sink
import org.squbs.unicomplex.WebContext

import scala.util.{Failure, Success}


case object RegisterTimeoutHandler

case object GetWebContext

class DummySvcActor extends Actor with WebContext with ActorLogging {

  var timeoutListeners = Seq.empty[ActorRef]

  import context.{dispatcher, system}

  def receive: Receive = {
    case req @ HttpRequest(_, Uri(_, _, Path("/dummysvcactor/ping"), _, _), _, _, _) =>
      log.debug("Received request " + req.uri)
      sender() ! HttpResponse(StatusCodes.OK, entity = "pong")

    case req @ HttpRequest(_, Uri(_, _, Path("/dummysvcactor/chunks"), _, _), _, _, _) =>

      var chunkCount = 0L
      var byteCount = 0L

      val future = req.entity.dataBytes.filter(_.length > 0).runForeach { b =>
        chunkCount += 1
        byteCount += b.length
      }

      val origSender = sender()

      future onComplete  {
        case Success(byteCount) => origSender ! HttpResponse(StatusCodes.OK, entity = s"Received $chunkCount chunks and $byteCount bytes.")
        case Failure(e) => HttpResponse(StatusCodes.InternalServerError, entity = s"Fail to process chunks: $e")
      }

    case req @ HttpRequest(_, Uri(_, _, Path("/dummysvcactor/timeout"), _, _), _, _, _) =>

    case req @ HttpRequest(_, Uri(_, _, Path("/dummysvcactor/chunktimeout"), _, _), _, _, _) =>
      req.entity.dataBytes.runWith(Sink.ignore)

    // TODO Missing feature?  How does this actor get notified if an pekko-http request-timeout happens?
//    case t: Timedout =>
//      timeoutListeners foreach { _ ! t }
//
//    case RegisterTimeoutHandler =>
//      timeoutListeners = timeoutListeners :+ sender()

    case GetWebContext => sender() ! webContext
  }
}
