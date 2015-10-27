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
package org.squbs.admin

import org.squbs.unicomplex.{RouteDefinition, WebContext}
import spray.http.MediaTypes._
import spray.http.Uri.Path
import spray.routing.Directives._

class AdminSvc extends RouteDefinition with WebContext {

  val prefix = if (webContext == "") "/bean" else s"/$webContext/bean"

  def route =
    get {
      pathEndOrSingleSlash {
        respondWithMediaType(`application/json`) {
          requestUri { uri =>
            complete {
              MBeanUtil.allObjectNames.map { name =>
                val resource = Path(s"$prefix/${name.replace('=', '~')}")
                s""""$name" : "${uri.withPath(resource)}""""
              } .mkString("{\n  ", ",\n  ", "\n}")
            }
          }
        }
      } ~
      path("bean" / Segment) { name =>
        respondWithMediaType(`application/json`) {
          complete {
            MBeanUtil.asJSON(name.replace('~', '='))
          }
        }
      }
    }
}