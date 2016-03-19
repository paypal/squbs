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
package org.squbs.pattern.spray.japi

import spray.http.MediaType
import spray.http.MediaTypes._

object MediaTypes {

  final val APPLICATION_ATOM_XML = `application/atom+xml`
  final val APPLICATION_FORM_URLENCODED = `application/x-www-form-urlencoded`
  final val APPLICATION_JSON = `application/json`
  final val APPLICATION_OCTET_STREAM = `application/octet-stream`
  final val APPLICATION_XHTML_XML = `application/xhtml+xml`
  final val APPLICATION_XML = `application/xml`
  final val APPLICATION_JAVASCRIPT = `application/javascript`

  def byName(value: String): MediaType = {
    val parts = value.split('/')
    if (parts.length != 2) throw new IllegalArgumentException(value + " is not a valid media-type")
    getForKey((parts(0), parts(1))) getOrElse register(MediaType.custom(parts(0), parts(1)))
  }
}