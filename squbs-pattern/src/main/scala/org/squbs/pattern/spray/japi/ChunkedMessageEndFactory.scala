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

import spray.http.{ChunkedMessageEnd, HttpHeader}

import scala.collection.JavaConversions._


/*
 * Java API to support ChunkedMessageEnd
 */
object ChunkedMessageEndFactory {

  def create(): ChunkedMessageEnd = ChunkedMessageEnd

  def create(extension: String) = ChunkedMessageEnd(extension)

  def create(trailer: java.util.List[HttpHeader]) = ChunkedMessageEnd(trailer = trailer.toList)

  def create(extension: String, trailer: java.util.List[HttpHeader]) = ChunkedMessageEnd(extension, trailer.toList)


}
