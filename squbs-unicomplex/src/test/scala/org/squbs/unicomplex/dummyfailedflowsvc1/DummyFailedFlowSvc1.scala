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
package org.squbs.unicomplex.dummyfailedflowsvc1

import org.apache.pekko.http.scaladsl.model.Uri.Path
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.stream.scaladsl.Flow
import org.squbs.unicomplex.{FlowDefinition, WebContext}

/**
  * A FlowDefinition must be a no-arg constructor. This is intended to test a failure path.
  * @param name Some bogus name.
  */
class DummyFailedFlowSvc1(name: String) extends FlowDefinition with WebContext {

  val pingPath = s"/$webContext/ping"

  def flow = Flow[HttpRequest].map {
    case HttpRequest(_, Uri(_, _, Path(`pingPath`), _, _), _, _, _) =>
      HttpResponse(StatusCodes.OK, entity = "pong")

    case _ => HttpResponse(StatusCodes.NotFound, entity = "Path not found!")
  }
}
