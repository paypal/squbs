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

import org.json4s.JsonAST.{JString, JObject}
import org.json4s.jackson.JsonMethods._
import org.squbs.unicomplex.{ConfigUtil, RouteDefinition, WebContext}
import spray.http.{StatusCodes, HttpResponse}
import spray.http.MediaTypes._
import spray.http.Uri.Path
import spray.routing.Directives._
import ConfigUtil._

class AdminSvc extends RouteDefinition with WebContext {

  val prefix = if (webContext == "") "/bean" else s"/$webContext/bean"

  val exclusions = context.system.settings.config.getOptionalStringList("squbs.admin.exclusions")
  val (exBeans, exFieldSet) = exclusions map { list =>
    val (beans, fields) = list partition { x => x.indexOf("::") < 0 }
    (beans.toSet, fields.toSet)
  } getOrElse (Set.empty[String], Set.empty[String])

  val exFields = exFieldSet map { fieldSpec =>
    val fields = fieldSpec split "::"
    fields(0) -> fields(1)
  } groupBy (_._1) mapValues { _.map(_._2) }


  val route =
    get {
      pathEndOrSingleSlash {
        respondWithMediaType(`application/json`) {
          requestUri { uri =>
            complete {
              val kv = MBeanUtil.allObjectNames collect {
                case name if !(exBeans contains name) =>
                  val resource = Path(s"$prefix/${name.replace('=', '~')}")
                  name -> JString(uri.withPath(resource).toString())
              }
              pretty(render(JObject(kv)))
            }
          }
        }
      } ~
      path("bean" / Segment) { encName =>
        respondWithMediaType(`application/json`) {
          complete {
            val name = encName.replace('~', '=')
            val response: HttpResponse =
              if (exBeans contains name) HttpResponse(StatusCodes.NotFound, StatusCodes.NotFound.defaultMessage)
              else MBeanUtil.asJSON(name, exFields getOrElse (name, Set.empty))
                .map { json => HttpResponse(entity = json) }
                .getOrElse (HttpResponse(StatusCodes.NotFound, StatusCodes.NotFound.defaultMessage))
            response
          }
        }
      }
    }
}