/*
 *  Copyright 2017 PayPal
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
package org.squbs.marshallers.json

import org.apache.pekko.http.javadsl.marshalling.Marshaller
import org.apache.pekko.http.javadsl.model.{HttpEntity, RequestEntity}
import org.apache.pekko.http.javadsl.unmarshalling.Unmarshaller
import org.apache.pekko.http.scaladsl.marshalling.ToEntityMarshaller
import org.apache.pekko.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import com.fasterxml.jackson.databind.ObjectMapper
import de.heikoseeberger.akkahttpjackson.JacksonSupport

import scala.reflect.runtime.universe._
import scala.reflect.{ClassTag, classTag}

/**
  * Supports Jackson marshalling and unmarshalling allowing per-type configuration.
  * This allows configuring separate mappers for each type, falling back to a default.
  */
object JacksonMapperSupport {

  import ReflectHelper._
  private[json] var defaultMapper: ObjectMapper = new ObjectMapper()
  private[json] var mappers = Map.empty[Class[_], ObjectMapper]

  /**
    * Sets given mapper as the new default object mapper.
    * @param objectMapper The new default object mapper
    */
  def setDefaultMapper(objectMapper: ObjectMapper): Unit = defaultMapper = objectMapper

  /**
    * Java API for registering a class-specific mapper.
    * @param clazz The class requiring a mapper
    * @param mapper The mapper
    */
  def register(clazz: Class[_], mapper: ObjectMapper): Unit = {
    synchronized {
      mappers += clazz -> mapper
    }
  }

  /**
    * Scala API for registering a class-specific mapper.
    * @param mapper The mapper
    * @tparam T The type to apply this mapper.
    */
  def register[T: ClassTag](mapper: ObjectMapper): Unit = register(classTag[T].runtimeClass, mapper)

  private def mapper[T](implicit ct: ClassTag[T]): ObjectMapper =
    mappers.getOrElse(ct.runtimeClass, defaultMapper)

  /**
    * Scala API. Just import JacksonMapperSupport._ and life is good.
    * @param ct The class tag auto-generated by the compiler
    * @tparam T The type for the marshaller
    * @return A Jackson marshaller configured for the specific type
    */
  implicit def jacksonMarshaller[T](implicit ct: ClassTag[T]): ToEntityMarshaller[T] =
    JacksonSupport.marshaller[T](mapper[T])

  /**
    * Scala API. Just import JacksonMapperSupport._ and life is good.
    * @param tt The type tag auto-generated by the compiler
    * @tparam T The type for the unmarshaller
    * @return A Jackson unmarshaller configured for the specific type
    */
  implicit def jacksonUnmarshaller[T](implicit tt: TypeTag[T]): FromEntityUnmarshaller[T] = {
    implicit val classTag = toClassTag(tt)
    JacksonSupport.unmarshaller[T](tt, mapper[T])
  }

  /**
    * Java API to generate a Jackson marshaller.
    * @param clazz The class for marshalling
    * @tparam T The type to unmarshal, auto-inferred
    * @return The Jackson marshaller configured for the specific type
    */
  def marshaller[T <: AnyRef](clazz: Class[T]): Marshaller[T, RequestEntity] = {
    implicit val classTag = ClassTag[T](clazz)
    Marshaller.fromScala(jacksonMarshaller[T])
  }

  /**
    * Java API to generate a Jackson unmarshaller.
    * @param clazz The class for unmarshalling
    * @tparam R The type for the unmarshaller
    * @return A Jackson unmarshaller configured for the specific type
    */
  def unmarshaller[R](clazz: Class[R]): Unmarshaller[HttpEntity, R] = {
    implicit val tt = toTypeTag[R](clazz)
    Unmarshaller.fromScala(jacksonUnmarshaller[R]).asInstanceOf[Unmarshaller[HttpEntity, R]]
  }
}
