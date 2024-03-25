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
package org.squbs.admin

import java.net.URLEncoder

import org.apache.pekko.http.scaladsl.model.Uri.Path
import org.apache.pekko.http.scaladsl.model._
import org.json4s.JsonAST.{JObject, JString}
import org.json4s.jackson.JsonMethods._
import org.squbs.unicomplex.{RouteDefinition, WebContext}
import org.squbs.util.ConfigUtil._

class AdminSvc extends RouteDefinition with WebContext {

  val prefix = if (webContext == "") "/bean" else s"/$webContext/bean"

  val exclusions = context.system.settings.config.get[Seq[String]]("squbs.admin.exclusions", Seq.empty[String]).toSet
  val (exBeans, exFieldSet) = exclusions partition { !_.contains("::") }

  val exFields = exFieldSet
    .map { fieldSpec =>
      val fields = fieldSpec split "::"
      fields(0) -> fields(1)
    }
    .groupBy(_._1)
    .map { case (k, v) => k -> v.map(_._2) }

  val route =
    get {
      pathEndOrSingleSlash {
        extractUri { uri =>
          complete {
            val kv = MBeanUtil.allObjectNames collect {
              case name if !(exBeans contains name) =>
                val resource = Path(s"$prefix/${URLEncoder.encode(name.replace('=', '~'), "UTF-8")}")
                name -> JString(uri.withPath(resource).toString())
            }
            HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, pretty(render(JObject(kv)))))
          }
        }
      } ~
      path("bean" / Segment) { encName =>
        complete {
          val name = encName.replace('~', '=').replace('%', '/')
          val response: HttpResponse =
            if (exBeans contains name) HttpResponse(StatusCodes.NotFound, entity = StatusCodes.NotFound.defaultMessage)
            else MBeanUtil.asJSON(name, exFields getOrElse (name, Set.empty))
              .map { json => HttpResponse(entity = json) }
              .getOrElse (HttpResponse(StatusCodes.NotFound, entity = StatusCodes.NotFound.defaultMessage))
          response
        }
      }
    }
}
