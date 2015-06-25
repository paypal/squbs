package org.squbs.httpclient.json

import spray.httpx.marshalling.Marshaller
import spray.httpx.unmarshalling.UnmarshallerLifting._
import spray.httpx.unmarshalling._

import scala.reflect._

/**
 * Created by lma on 6/17/2015.
 */
object JsonProtocol {

  import org.squbs.httpclient.json.ReflectHelper._

import scala.reflect.runtime.universe._

  implicit def optionToMarshaller[T <: AnyRef](data: Option[T]): Marshaller[T] = {
    data match {
      case None => Json4sJacksonNoTypeHintsProtocol.json4sMarshaller[T] //no big deal on choosing
      case Some(v) => objectToMarshaller(v)
    }
  }


  implicit def typeTagToMarshaller[T <: AnyRef : TypeTag]: Marshaller[T] =
    if (typeOf[T].typeSymbol.isJava)
      JacksonProtocol.jacksonMarshaller
    else
      Json4sJacksonNoTypeHintsProtocol.json4sMarshaller

  implicit def manifestToMarshaller[T <: AnyRef : Manifest]: Marshaller[T] =
    if (typeOf[T].typeSymbol.isJava)
      JacksonProtocol.jacksonMarshaller
    else
      Json4sJacksonNoTypeHintsProtocol.json4sMarshaller

  implicit def classToMarshaller[T <: AnyRef](clazz : Class[T]): Marshaller[T] =
    if (isJavaClass(clazz))
      JacksonProtocol.jacksonMarshaller
    else
      Json4sJacksonNoTypeHintsProtocol.json4sMarshaller

  implicit def objectToMarshaller[T <: AnyRef](obj: T): Marshaller[T] =
    if (isJavaClass(obj))
      JacksonProtocol.jacksonMarshaller
    else
      Json4sJacksonNoTypeHintsProtocol.json4sMarshaller


  //  implicit def toResponseMarshallable[T <: AnyRef](obj: T): ToResponseMarshallable = {
  //    isMarshallable(obj)(objectToMarshaller(obj))
  //  }
  //


  private implicit def unmarshallerToFromResponseUnmarshaller[T](ummarshaller: Unmarshaller[T]): FromResponseUnmarshaller[T] = {
    fromResponseUnmarshaller(fromMessageUnmarshaller(ummarshaller))
  }

  implicit def classToFromResponseUnmarshaller[R](clazz: Class[R]): FromResponseUnmarshaller[R] = {
    if (isJavaClass(clazz)) JacksonProtocol.jacksonUnmarshaller(clazz)
    else Json4sJacksonNoTypeHintsProtocol.json4sUnmarshaller[R](ManifestFactory.classType(clazz))
  }

  implicit def manifestToUnmarshaller[R: Manifest]: Unmarshaller[R] = {
    val clazz = implicitly[Manifest[R]].runtimeClass.asInstanceOf[Class[R]]
    if (isJavaClass(clazz)) JacksonProtocol.jacksonUnmarshaller(clazz)
    else Json4sJacksonNoTypeHintsProtocol.json4sUnmarshaller[R]
  }

  implicit def typeTagToUnmarshaller[R](implicit evidence: TypeTag[R]): Unmarshaller[R] = {
    if (typeOf[R].typeSymbol.isJava) {
      val clazz = evidence.mirror.runtimeClass(evidence.tpe).asInstanceOf[Class[R]]
      JacksonProtocol.jacksonUnmarshaller(clazz)
    }
    else Json4sJacksonNoTypeHintsProtocol.json4sUnmarshaller(toManifest)
  }

}
