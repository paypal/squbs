/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the AUTHORS file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.squbs.httpclient.json

import spray.httpx.Json4sSupport
import org.json4s._
import org.json4s.ShortTypeHints

object Json4sNativeNoTypeHintsProtocol extends Json4sSupport {
  override implicit def json4sFormats: Formats = DefaultFormats.withHints(NoTypeHints)
}

trait Json4sNativeShortTypeHintsProtocol extends Json4sSupport {
  override implicit def json4sFormats: Formats = DefaultFormats.withHints(ShortTypeHints(hints))
  def hints: List[Class[_]]
}

trait Json4sNativeFullTypeHintsProtocol extends Json4sSupport {
  override implicit def json4sFormats: Formats = DefaultFormats.withHints(FullTypeHints(hints))
  def hints: List[Class[_]]
}

trait Json4sNativeCustomProtocol extends Json4sSupport
