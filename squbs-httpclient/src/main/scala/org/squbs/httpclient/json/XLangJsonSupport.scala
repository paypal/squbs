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

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import com.fasterxml.jackson.databind.ObjectMapper
import de.heikoseeberger.akkahttpjackson.JacksonSupport
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.{DefaultFormats, Formats, Serialization, jackson}

import scala.language.implicitConversions
import scala.reflect._
import scala.reflect.runtime.universe._

object XLangJsonSupport {

  import org.squbs.httpclient.json.ReflectHelper._

  object JacksonMapperSupport {

    private[this] var mappers = Map.empty[String, ObjectMapper]

    /**
      * Java API for registering a class-specific mapper.
      * @param clazz The class requiring a mapper
      * @param mapper The mapper
      */
    def registerMapper(clazz: Class[_], mapper: ObjectMapper): Unit = {
      synchronized {
        mappers += clazz.getName -> mapper
      }
    }

    /**
      * Scala API for registering a class-specific mapper.
      * @param mapper The mapper
      * @tparam T The type to apply this mapper.
      */
    def registerMapper[T: ClassTag](mapper: ObjectMapper): Unit = {
      synchronized {
        mappers += classTag[T].runtimeClass.getName -> mapper
      }
    }

    //default mapper relies on getters
    val defaultMapper = new ObjectMapper()
    //.setVisibility(PropertyAccessor.FIELD, Visibility.ANY)
    //.registerModule(DefaultScalaModule)
    // Don't register the additional modules. Customer code needing specific behavior should do so themselves.

    private def mapper[T](implicit ct: ClassTag[T]): ObjectMapper =
      mappers.getOrElse(ct.runtimeClass.getName, defaultMapper)

    implicit def jacksonMarshaller[T](implicit ct: ClassTag[T]): ToEntityMarshaller[T] =
      JacksonSupport.jacksonToEntityMarshaller[T](mapper[T])

    implicit def jacksonUnmarshaller[T](implicit ct: ClassTag[T]): FromEntityUnmarshaller[T] =
      JacksonSupport.jacksonUnmarshaller[T](ct, mapper[T])
  }

  object TypeTagSupport {

    implicit def typeTagToMarshaller[T <: AnyRef: TypeTag](implicit
                                                            serialization: Serialization = jackson.Serialization,
                                                            formats: Formats = DefaultFormats): ToEntityMarshaller[T] = {
      if (typeOf[T].typeSymbol.isJava) {
        implicit val classTag = ReflectHelper.toClassTag[T]
        JacksonMapperSupport.jacksonMarshaller[T]
      } else {
        Json4sSupport.json4sMarshaller[T]
      }
    }

    implicit def typeTagToUnmarshaller[R: TypeTag](implicit
                                                    serialization: Serialization = jackson.Serialization,
                                                    formats: Formats = DefaultFormats): FromEntityUnmarshaller[R] = {
      if (typeOf[R].typeSymbol.isJava) {
        implicit val classTag = toClassTag[R]
        JacksonMapperSupport.jacksonUnmarshaller[R]
      } else {
        implicit val manifest = toManifest[R]
        Json4sSupport.json4sUnmarshaller[R]
      }
    }
  }

  object ManifestSupport {

    implicit def manifestToMarshaller[T <: AnyRef : Manifest](implicit
                                                              serialization: Serialization = jackson.Serialization,
                                                              formats: Formats = DefaultFormats): ToEntityMarshaller[T] = {
      val clazz = implicitly[Manifest[T]].runtimeClass.asInstanceOf[Class[T]]
      if (isJavaClass(clazz)) {
        implicit val classTag = ClassTag[T](clazz)
        JacksonMapperSupport.jacksonMarshaller[T]
      } else {
        Json4sSupport.json4sMarshaller[T]
      }
    }

    implicit def manifestToUnmarshaller[R: Manifest](implicit serialization: Serialization = jackson.Serialization,
                                                     formats: Formats = DefaultFormats): FromEntityUnmarshaller[R] = {
      val clazz = implicitly[Manifest[R]].runtimeClass.asInstanceOf[Class[R]]
      if (isJavaClass(clazz)) {
        implicit val classTag = ClassTag[R](clazz)
        JacksonMapperSupport.jacksonUnmarshaller[R]
      } else {
        Json4sSupport.json4sUnmarshaller[R]
      }
    }
  }


  object ClassSupport {

    implicit def classToMarshaller[T <: AnyRef](clazz: Class[T])
                                               (implicit serialization: Serialization = jackson.Serialization,
                                                formats: Formats = DefaultFormats): ToEntityMarshaller[T] =
      if (isJavaClass(clazz)) {
        implicit val classTag = ClassTag[T](clazz)
        JacksonMapperSupport.jacksonMarshaller[T]
      } else {
        implicit val manifest = ManifestFactory.classType[T](clazz)
        Json4sSupport.json4sMarshaller[T]
      }

    implicit def classToFromResponseUnmarshaller[R](clazz: Class[R])
                                                   (implicit serialization: Serialization = jackson.Serialization,
                                                    formats: Formats = DefaultFormats): FromEntityUnmarshaller[R] =
      if (isJavaClass(clazz)) {
        implicit val classTag = ClassTag[R](clazz)
        JacksonMapperSupport.jacksonUnmarshaller[R]
      } else {
        implicit val manifest = ManifestFactory.classType[R](clazz)
        Json4sSupport.json4sUnmarshaller[R]
      }

//    private implicit def unmarshallerToFromResponseUnmarshaller[T](ummarshaller: Unmarshaller[T]): FromResponseUnmarshaller[T] = {
//      fromResponseUnmarshaller(fromMessageUnmarshaller(ummarshaller))
//    }

  }


  implicit def optionToMarshaller[T <: AnyRef](data: Option[T])(implicit
                                                                serialization: Serialization = jackson.Serialization,
                                                                formats: Formats = DefaultFormats): ToEntityMarshaller[T] = {
    data match {
      case None => Json4sSupport.json4sMarshaller[T] //no big deal on choosing
      case Some(v) => objectToMarshaller(v)
    }
  }

  implicit def objectToMarshaller[T <: AnyRef](obj: T)(implicit
                                                       serialization: Serialization = jackson.Serialization,
                                                       formats: Formats = DefaultFormats): ToEntityMarshaller[T] = {
    val clazz = obj.getClass.asInstanceOf[Class[T]]
    if (isJavaClass(clazz)) {
      implicit val classTag = ClassTag[T](clazz)
      JacksonMapperSupport.jacksonMarshaller[T]
    } else {
      Json4sSupport.json4sMarshaller[T]
    }
  }

//  implicit def toResponseMarshallable[T <: AnyRef](obj: T): ToResponseMarshallable = {
//    ToResponseMarshallable.isMarshallable(obj)(objectToMarshaller(obj))
//  }


}
