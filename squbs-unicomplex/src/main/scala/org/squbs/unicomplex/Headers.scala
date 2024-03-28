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

package org.squbs.unicomplex

import org.apache.pekko.http.scaladsl.model.headers.{ModeledCustomHeader, ModeledCustomHeaderCompanion}

import scala.util.Try

object LocalPortHeader extends ModeledCustomHeaderCompanion[LocalPortHeader] {

  override def name: String = classOf[LocalPortHeader].getName

  override def parse(value: String): Try[LocalPortHeader] = Try(new LocalPortHeader(value.toInt))
}

final case class LocalPortHeader(port: Int) extends ModeledCustomHeader[LocalPortHeader] {

  override def companion: ModeledCustomHeaderCompanion[LocalPortHeader] = LocalPortHeader

  override def value(): String = port.toString

  override def renderInResponses(): Boolean = false

  override def renderInRequests(): Boolean = false
}

object WebContextHeader extends ModeledCustomHeaderCompanion[WebContextHeader] {

  override def name: String = classOf[WebContextHeader].getName

  override def parse(value: String): Try[WebContextHeader] = Try(new WebContextHeader(value))
}

final case class WebContextHeader(webCtx: String) extends ModeledCustomHeader[WebContextHeader] {

  override def companion: ModeledCustomHeaderCompanion[WebContextHeader] = WebContextHeader

  override def value(): String = webCtx

  override def renderInResponses(): Boolean = false

  override def renderInRequests(): Boolean = false
}
