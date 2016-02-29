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

import spray.httpx.marshalling.Marshaller
import spray.httpx.unmarshalling.UnmarshallerLifting._
import spray.httpx.unmarshalling._

import scala.reflect.ManifestFactory

object JsonProtocol {

  import org.squbs.httpclient.json.ReflectHelper._

  import scala.reflect.runtime.universe._

  object TypeTagSupport {

    implicit def typeTagToMarshaller[T <: AnyRef : TypeTag]: Marshaller[T] = {
      if (typeOf[T].typeSymbol.isJava) {
        val evidence = implicitly[TypeTag[T]]
        val clazz = evidence.mirror.runtimeClass(evidence.tpe).asInstanceOf[Class[T]]
        JacksonProtocol.jacksonMarshaller(clazz)
      }
      else
        Json4sJacksonNoTypeHintsProtocol.json4sMarshaller
    }

    implicit def typeTagToUnmarshaller[R](implicit evidence: TypeTag[R]): Unmarshaller[R] = {
      if (typeOf[R].typeSymbol.isJava) {
        val clazz = evidence.mirror.runtimeClass(evidence.tpe).asInstanceOf[Class[R]]
        JacksonProtocol.jacksonUnmarshaller(clazz)
      }
      else Json4sJacksonNoTypeHintsProtocol.json4sUnmarshaller(toManifest)
    }
  }

  object ManifestSupport {

    implicit def manifestToMarshaller[T <: AnyRef : Manifest]: Marshaller[T] = {
      val clazz = implicitly[Manifest[T]].runtimeClass.asInstanceOf[Class[T]]
      if (isJavaClass(clazz))
        JacksonProtocol.jacksonMarshaller(clazz)
      else
        Json4sJacksonNoTypeHintsProtocol.json4sMarshaller
    }

    implicit def manifestToUnmarshaller[R: Manifest]: Unmarshaller[R] = {
      val clazz = implicitly[Manifest[R]].runtimeClass.asInstanceOf[Class[R]]
      if (isJavaClass(clazz))
        JacksonProtocol.jacksonUnmarshaller(clazz)
      else
        Json4sJacksonNoTypeHintsProtocol.json4sUnmarshaller[R]
    }

  }


  object ClassSupport {

    implicit def classToMarshaller[T <: AnyRef](clazz: Class[T]): Marshaller[T] =
      if (isJavaClass(clazz))
        JacksonProtocol.jacksonMarshaller(clazz)
      else
        Json4sJacksonNoTypeHintsProtocol.json4sMarshaller

    implicit def classToFromResponseUnmarshaller[R](clazz: Class[R]): FromResponseUnmarshaller[R] = {
      if (isJavaClass(clazz)) JacksonProtocol.jacksonUnmarshaller(clazz)
      else Json4sJacksonNoTypeHintsProtocol.json4sUnmarshaller[R](ManifestFactory.classType(clazz))
    }

    private implicit def unmarshallerToFromResponseUnmarshaller[T](ummarshaller: Unmarshaller[T]): FromResponseUnmarshaller[T] = {
      fromResponseUnmarshaller(fromMessageUnmarshaller(ummarshaller))
    }

  }


  implicit def optionToMarshaller[T <: AnyRef](data: Option[T]): Marshaller[T] = {
    data match {
      case None => Json4sJacksonNoTypeHintsProtocol.json4sMarshaller[T] //no big deal on choosing
      case Some(v) => objectToMarshaller(v)
    }
  }

  implicit def objectToMarshaller[T <: AnyRef](obj: T): Marshaller[T] = {
    val clazz = obj.getClass.asInstanceOf[Class[T]]
    if (isJavaClass(clazz))
      JacksonProtocol.jacksonMarshaller(clazz)
    else
      Json4sJacksonNoTypeHintsProtocol.json4sMarshaller
  }

//  implicit def toResponseMarshallable[T <: AnyRef](obj: T): ToResponseMarshallable = {
//    ToResponseMarshallable.isMarshallable(obj)(objectToMarshaller(obj))
//  }


}
