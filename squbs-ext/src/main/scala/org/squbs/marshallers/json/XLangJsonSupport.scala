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
import com.github.pjfanning.pekkohttpjson4s.Json4sSupport
import org.json4s.{DefaultFormats, Formats, Serialization, Serializer, jackson, native}

import scala.annotation.varargs
import scala.language.implicitConversions
import scala.reflect._
import scala.reflect.runtime.universe._

object XLangJsonSupport {

  import ReflectHelper._

  private[json] var defaultSerialization: Serialization = jackson.Serialization
  private[json] var serializations = Map.empty[Class[_], Serialization]

  private[json] var globalDefaultFormats: Formats = DefaultFormats
  private[json] var classFormats = Map.empty[Class[_], Formats]

  /**
    * Sets given mapper as the new default object mapper. Delegates to JacksonMapperSupport.
    * @param objectMapper The new default object mapper
    */
  def setDefaultMapper(objectMapper: ObjectMapper): Unit = JacksonMapperSupport.setDefaultMapper(objectMapper)


  /**
    * Sets given serialization as new default.
    * @param serialization The new default serialization
    */
  def setDefaultSerialization(serialization: Serialization): Unit = defaultSerialization = serialization

  /**
    * Sets given formats as new default.
    * @param formats The new default formats
    */
  def setDefaultFormats(formats: Formats): Unit = globalDefaultFormats = formats

  /**
    * Java API for adding default Json4s serializers. Note this API is additive.
    * Additional serializers will be added for each call.
    * @param serializers The serializers to add
    */
  @varargs
  def addDefaultSerializers(serializers: Serializer[_]*): Unit =
    globalDefaultFormats ++= serializers.toSeq

  /**
    * Java API for registering class-specific serialization (Json4s).
    * @param clazz The class
    * @param serialization The serialization
    */
  def register(clazz: Class[_], serialization: Serialization): Unit =
    synchronized {
      serializations += clazz -> serialization
    }

  /**
    * Scala API for registering type-specific serialization (Json4s).
    * @param serialization The serialization
    * @tparam T The type
    */
  def register[T: ClassTag](serialization: Serialization): Unit = register(classTag[T].runtimeClass, serialization)

  /**
    * Java API for registering class-specific formats (Json4s).
    * @param clazz The class
    * @param formats The formats
    */
  def register(clazz: Class[_], formats: Formats): Unit =
    synchronized {
      classFormats += clazz -> formats
    }

  /**
    * Java API for adding serializers for a particular entry-point class. Note this API is additive.
    * Additional serializers will be added for each call.
    * @param clazz The entry-point class to register/add serializers
    * @param serializers The serializers to add
    */
  @varargs
  def addSerializers(clazz: Class[_], serializers: Serializer[_]*): Unit = {
    if (serializers.nonEmpty) synchronized {
      val previousFormats = classFormats.getOrElse(clazz, DefaultFormats)
      classFormats += clazz -> (previousFormats ++ serializers)
    }
  }

  /**
    * Java API for accessing the Json4s DefaultFormats.
    * @return The DefaultFormats singleton instance
    */
  def defaultFormats: Formats = DefaultFormats

  /**
    * Java API for accessing the Jackson serialization.
    * @return The Jackson serialization
    */
  def jacksonSerialization: Serialization = jackson.Serialization

  /**
    * Java API for accessing the native serialization.
    * @return The native serialization
    */
  def nativeSerialization: Serialization = native.Serialization

  /**
    * Scala API for registering type-specific formats (Json4s).
    * @param formats The formats
    * @tparam T The type
    */
  def register[T: ClassTag](formats: Formats): Unit = register(classTag[T].runtimeClass, formats)

  /**
    * Java API for registering a class-specific mapper. Delegates to JacksonMapperSupport.
    * @param clazz The class requiring a mapper
    * @param mapper The mapper
    */
  def register(clazz: Class[_], mapper: ObjectMapper): Unit = JacksonMapperSupport.register(clazz, mapper)

  /**
    * Scala API for registering a class-specific mapper. Delegates to JacksonMapperSupport
    * @param mapper The mapper
    * @tparam T The type to apply this mapper.
    */
  def register[T: ClassTag](mapper: ObjectMapper): Unit = JacksonMapperSupport.register[T](mapper)

  private def serialization[T: TypeTag]: Serialization = serializations.getOrElse(toClass[T], defaultSerialization)

  private def formats[T: TypeTag]: Formats = classFormats.getOrElse(toClass[T], globalDefaultFormats)

  /**
    * Scala API. Just import XLangJsonSupport._ and life is good.
    * @tparam T The type used to obtain the marshaller
    * @return The appropriate marshaller, appropriately configured.
    */
  implicit def typeToMarshaller[T <: AnyRef: TypeTag]: ToEntityMarshaller[T] = {
    if (typeOf[T].typeSymbol.isJava) {
      implicit val classTag = ReflectHelper.toClassTag[T]
      JacksonMapperSupport.jacksonMarshaller[T]
    } else {
      implicit val mySerialization = serialization[T]
      implicit val myFormats = formats[T]
      Json4sSupport.marshaller[T]
    }
  }

  /**
    * Scala API. Just import XLangJsonSupport._ and life is good.
    * @tparam R The type used to obtain the unmarshaller
    * @return The appropriate unmarshaller, appropriately configured.
    */
  implicit def typeToUnmarshaller[R: TypeTag]: FromEntityUnmarshaller[R] = {
    if (typeOf[R].typeSymbol.isJava) {
      implicit val classTag = toClassTag[R]
      JacksonMapperSupport.jacksonUnmarshaller[R]
    } else {
      implicit val manifest = toManifest[R]
      implicit val mySerialization = serialization[R]
      implicit val myFormats = formats[R]
      Json4sSupport.unmarshaller[R]
    }
  }

  /** Java API to get the appropriate marshaller.
    * @param clazz The class for marshalling
    * @tparam T The type to marshal, auto-inferred
    * @return The appropriate marshaller, appropriately configured
    */
  def marshaller[T <: AnyRef](clazz: Class[T]): Marshaller[T, RequestEntity] = {
    val scalaMarshaller =
      if (isJavaClass(clazz)) {
        implicit val classTag = ClassTag[T](clazz)
        JacksonMapperSupport.jacksonMarshaller[T]
      } else {
        implicit val manifest = ManifestFactory.classType[T](clazz)
        implicit val serialization = serializations.getOrElse(clazz, defaultSerialization)
        implicit val formats = classFormats.getOrElse(clazz, globalDefaultFormats)
        Json4sSupport.marshaller[T]
      }
    Marshaller.fromScala(scalaMarshaller)
  }

  /** Java API to get the appropriate unmarshaller.
    * @param clazz The class for unmarshalling
    * @tparam R The type to unmarshal, auto-inferred
    * @return The appropriate unmarshaller, appropriately configured
    */
  def unmarshaller[R](clazz: Class[R]): Unmarshaller[HttpEntity, R] = {
    val scalaUnmarshaller =
      if (isJavaClass(clazz)) {
        implicit val tt = ReflectHelper.toTypeTag[R](clazz)
        JacksonMapperSupport.jacksonUnmarshaller[R]
      } else {
        implicit val manifest = ManifestFactory.classType[R](clazz)
        implicit val serialization = serializations.getOrElse(clazz, defaultSerialization)
        implicit val formats = classFormats.getOrElse(clazz, globalDefaultFormats)
        Json4sSupport.unmarshaller[R]
      }
    Unmarshaller.fromScala(scalaUnmarshaller).asInstanceOf[Unmarshaller[HttpEntity, R]]
  }
}
