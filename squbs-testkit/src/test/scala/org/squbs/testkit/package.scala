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

package org.squbs

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import org.apache.pekko.util.ByteString

import scala.concurrent.Future

package object testkit {
  case object TestPing
  case object TestPong

  def entityAsString(uri: String)(implicit system: ActorSystem): Future[String] = {
    import system.dispatcher
    get(uri) flatMap extractEntityAsString
  }

  def get(uri: String)(implicit system: ActorSystem): Future[HttpResponse] =
    Http().singleRequest(HttpRequest(uri = Uri(uri)))

  def extractEntityAsString(response: HttpResponse)(implicit system: ActorSystem): Future[String] = {
    import system.dispatcher
    response.entity.dataBytes.runFold(ByteString(""))(_ ++ _) map(_.utf8String)
  }
}
