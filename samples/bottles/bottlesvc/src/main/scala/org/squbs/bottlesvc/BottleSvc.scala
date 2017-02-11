/*
 *  Copyright 2017 PayPal
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

package org.squbs.bottlesvc

import akka.actor.{ActorLogging, Actor, Props}
import spray.can.Http
import spray.routing._
import Directives._
import spray.http._
import MediaTypes._
import spray.httpx.encoding.Gzip

import org.squbs.unicomplex.RouteDefinition
import org.squbs.unicomplex.MediaTypeExt._

import org.squbs.bottlemsgs._


// this class defines our service behavior independently from the service actor
class BottleSvc extends RouteDefinition {
  
  def route =
    path("hello") {
      get {
        respondWithMediaType(`text/html`) { // XML is marshalled to `text/xml` by default, so we simply override here
          complete {
            <html>
              <body>
                <h1>Say hello to <i>spray-routing</i> on <i>spray-can</i>!</h1>
              </body>
            </html>
          }
        }
      }
    } ~
    path(""".*\.html""".r) { name =>
      encodeResponse(Gzip) {
        getFromResource("html/" + name)
      }
    } ~  
    path("events") {
      get { ctx =>
        context.actorOf(Props(classOf[Mediator] ,ctx))
      }
    }
  }

class Mediator(ctx: RequestContext) extends Actor with ActorLogging {
  
  context.actorSelection("/user/bottlecube/lyrics") ! StartEvents
  val responseStart = HttpResponse(entity = HttpEntity(`text/event-stream`, toSSE("Starting")))
  ctx.responder ! ChunkedResponseStart(responseStart)
      
  def toSSE(msg: String) = "event: lyric\ndata: " + msg.replace("\n", "\ndata: ") + "\n\n"
  val streamEnd = "event: streamEnd\ndata: End of stream\n\n"
      
  def receive = {
    case Event(msg) => 
      val eventMessage = toSSE(msg)
      log.info('\n' + eventMessage)
      ctx.responder ! MessageChunk(eventMessage)
            
    case EndEvents  =>
      log.info('\n' + streamEnd)
      ctx.responder ! MessageChunk(streamEnd)
      ctx.responder ! ChunkedMessageEnd()
      context.stop(self)
        
    // Connection closed sent from ctx.responder
    case ev: Http.ConnectionClosed =>
      log.warning("Connection closed, {}", ev)
      context.stop(self)        
  }
}
