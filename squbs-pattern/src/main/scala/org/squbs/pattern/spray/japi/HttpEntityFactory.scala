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

import spray.http.{ContentType, HttpEntity}
import spray.httpx.marshalling._

/**
 * Java API to support HttpEntity
 */
object HttpEntityFactory {

  def create(string : String): HttpEntity = HttpEntity(string)

  def create(bytes: Array[Byte]): HttpEntity = HttpEntity(bytes)

  def create(contentType: ContentType, string: String): HttpEntity = HttpEntity(contentType, string)

  def create(contentType: ContentType, bytes: Array[Byte]): HttpEntity = HttpEntity(contentType, bytes)

  def create[T <: AnyRef](responseObject: T, marshaller: Marshaller[T]): HttpEntity = {
    implicit val iMarshaller = marshaller
    marshal(responseObject) match {
      case Left(e) => throw e
      case Right(entity) => entity
    }
  }
}
