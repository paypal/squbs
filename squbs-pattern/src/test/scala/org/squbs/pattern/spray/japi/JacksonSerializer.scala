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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import spray.http.ContentTypes
import spray.httpx.marshalling.Marshaller

object JacksonSerializer {

  //default mapper relies on getters
  val defaultMapper = new ObjectMapper()
    //.setVisibility(PropertyAccessor.FIELD, Visibility.ANY)
    .registerModule(DefaultScalaModule)


  def marshaller[T <: AnyRef](clazz: Class[T]): Marshaller[T] =
    Marshaller.delegate[T, String](ContentTypes.`application/json`)(defaultMapper.writeValueAsString(_))

}
