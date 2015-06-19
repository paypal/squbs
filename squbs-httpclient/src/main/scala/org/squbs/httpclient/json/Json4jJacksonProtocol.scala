package org.squbs.httpclient.json

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.ObjectMapper
import spray.http.{HttpCharsets, ContentTypes, HttpEntity, MediaTypes}
import spray.httpx.marshalling.{Marshaller, ToResponseMarshallable}
import spray.httpx.unmarshalling._
import spray.httpx.unmarshalling.UnmarshallerLifting._

import scala.reflect._
import scala.reflect.api.JavaUniverse

/**
 * Created by lma on 6/17/2015.
 */
object Json4jJacksonProtocol {

  import spray.httpx.marshalling.ToResponseMarshallable.isMarshallable

  val mapper = new ObjectMapper().setVisibility(PropertyAccessor.FIELD, Visibility.ANY)

  private def marshall[T](obj: T): String = {
    mapper.writeValueAsString(obj)
  }

  //TODO cache?
  private def isJavaClass(v: Any): Boolean = {
    import reflect.runtime.universe._
    val typeMirror = runtimeMirror(v.getClass.getClassLoader)
    val instanceMirror = typeMirror.reflect(v)
    val symbol = instanceMirror.symbol
    symbol.isJava
  }

  private def isJavaClass(clazz: Class[_]): Boolean = {
    import reflect.runtime.universe._
    val typeMirror = runtimeMirror(clazz.getClassLoader)
    val classMirror = typeMirror.classSymbol(clazz)
    classMirror.isJava
  }

  private implicit def json4jMarshaller[T <: AnyRef](obj: T): Marshaller[T] =
    if (isJavaClass(obj))
      Marshaller.delegate[T, String](ContentTypes.`application/json`)(marshall(_))
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

  private implicit def json4jUnmarshaller[T](clazz: Class[T]) =
    Unmarshaller[T](MediaTypes.`application/json`) {
      case x: HttpEntity.NonEmpty ⇒
        try {
          val input = x.asString(defaultCharset = HttpCharsets.`UTF-8`)
          mapper.readValue(input, clazz)
        } catch {
          case t: Throwable =>
            //t.printStackTrace()
            throw t
        }
    }

  private implicit def unmarshallerToFromResponseUnmarshaller[T](ummarshaller: Unmarshaller[T]): FromResponseUnmarshaller[T] = {
    fromResponseUnmarshaller(fromMessageUnmarshaller(ummarshaller))
  }

  implicit def classToFromResponseUnmarshaller[R](clazz: Class[R]): FromResponseUnmarshaller[R] = {
    if (isJavaClass(clazz)) json4jUnmarshaller(clazz)
    else Json4sJacksonNoTypeHintsProtocol.json4sUnmarshaller[R](ManifestFactory.classType(clazz))
  }


}
