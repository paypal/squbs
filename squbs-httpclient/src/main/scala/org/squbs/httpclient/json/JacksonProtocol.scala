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

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import spray.http.{ContentTypes, HttpCharsets, HttpEntity, MediaTypes}
import spray.httpx.marshalling.Marshaller
import spray.httpx.unmarshalling.Unmarshaller

/**
 * Created by lma on 6/23/2015.
 */
object JacksonProtocol {

  //key = class name
  var mappers = Map[String, ObjectMapper]()

  //register a specific mapper
  def registerMapper(clazz: Class[_], mapper: ObjectMapper) = {
    synchronized {
      mappers = mappers + ((clazz.getName, mapper))
    }
  }

  //default mapper relies on getters
  val defaultMapper = new ObjectMapper()
    //.setVisibility(PropertyAccessor.FIELD, Visibility.ANY)
    .registerModule(DefaultScalaModule)

  private def mapper(clazz: Class[_]): ObjectMapper = {
    mappers.get(clazz.getName).getOrElse(defaultMapper)
  }

  implicit def jacksonMarshaller[T <: AnyRef](clazz: Class[T]) =
    Marshaller.delegate[T, String](ContentTypes.`application/json`)(mapper(clazz).writeValueAsString(_))

  implicit def jacksonUnmarshaller[T](clazz: Class[T]): Unmarshaller[T] = {
    Unmarshaller[T](MediaTypes.`application/json`) {
      case x: HttpEntity.NonEmpty ⇒
        try {
          val input = x.asString(defaultCharset = HttpCharsets.`UTF-8`)
          mapper(clazz).readValue(input, clazz)
        } catch {
          case t: Throwable =>
            //t.printStackTrace()
            throw t
        }
    }
  }

}
