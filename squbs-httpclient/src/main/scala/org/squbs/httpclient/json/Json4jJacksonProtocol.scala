package org.squbs.httpclient.json

import spray.httpx.marshalling.{Marshaller, ToResponseMarshallable}
import spray.httpx.unmarshalling.UnmarshallerLifting._
import spray.httpx.unmarshalling._

import scala.reflect._

/**
 * Created by lma on 6/17/2015.
 */
object Json4jJacksonProtocol {

  import org.squbs.httpclient.json.ReflectHelper._
  import spray.httpx.marshalling.ToResponseMarshallable.isMarshallable

  implicit def json4jMarshaller[T <: AnyRef](obj: T): Marshaller[T] =
    if (isJavaClass(obj))
      JacksonProtocol.jacksonMarshaller
    else
      Json4sJacksonNoTypeHintsProtocol.json4sMarshaller

  //todo determine whether T is a java class
  implicit def optionToMarshaller[T <: AnyRef](data: Option[T]): Marshaller[T] = {
    import scala.reflect.runtime.{universe => ru}
    //classOf[T]
    //scala.reflect.classTag[T].runtimeClass
    Json4sJacksonNoTypeHintsProtocol.json4sMarshaller[T]
  }

  implicit def toResponseMarshallable[T <: AnyRef](obj: T): ToResponseMarshallable = {
    isMarshallable(obj)(json4jMarshaller(obj))
  }

  private implicit def unmarshallerToFromResponseUnmarshaller[T](ummarshaller: Unmarshaller[T]): FromResponseUnmarshaller[T] = {
    fromResponseUnmarshaller(fromMessageUnmarshaller(ummarshaller))
  }

  implicit def classToFromResponseUnmarshaller[R](clazz: Class[R]): FromResponseUnmarshaller[R] = {
    if (isJavaClass(clazz)) JacksonProtocol.jacksonUnmarshaller(clazz)
    else Json4sJacksonNoTypeHintsProtocol.json4sUnmarshaller[R](ManifestFactory.classType(clazz))
  }


}
