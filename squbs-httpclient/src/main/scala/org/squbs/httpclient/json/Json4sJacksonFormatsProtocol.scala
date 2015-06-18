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

package org.squbs.httpclient.json

import spray.httpx.Json4sJacksonSupport
import org.json4s._

object Json4sJacksonNoTypeHintsProtocol extends Json4sJacksonSupport {
  override implicit def json4sJacksonFormats: Formats = DefaultFormats.withHints(NoTypeHints)
}

trait Json4sJacksonShortTypeHintsProtocol extends Json4sJacksonSupport {
  override implicit def json4sJacksonFormats: Formats = DefaultFormats.withHints(ShortTypeHints(hints))
  def hints: List[Class[_]]
}

trait Json4sJacksonFullTypeHintsProtocol extends Json4sJacksonSupport {
  override implicit def json4sJacksonFormats: Formats = DefaultFormats.withHints(FullTypeHints(hints))
  def hints: List[Class[_]]
}

trait Json4sJacksonCustomProtocol extends Json4sJacksonSupport
