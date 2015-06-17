package org.squbs.httpclient.json

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.ObjectMapper
import spray.http.{HttpCharsets, ContentTypes, HttpEntity, MediaTypes}
import spray.httpx.marshalling.{Marshaller, ToResponseMarshallable}
import spray.httpx.unmarshalling._
import spray.httpx.unmarshalling.UnmarshallerLifting._

/**
 * Created by lma on 6/17/2015.
 */
object Json4jJacksonProtocol {

  import spray.httpx.marshalling.ToResponseMarshallable.isMarshallable

  val mapper = new ObjectMapper().setVisibility(PropertyAccessor.FIELD, Visibility.ANY)

  private def marshall[T](obj: T): String = {
    mapper.writeValueAsString(obj)
  }

  private implicit def json4jMarshaller[T <: AnyRef](obj: T): Marshaller[T] =
    Marshaller.delegate[T, String](ContentTypes.`application/json`)(marshall(_))


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

  implicit def toFromResponseUnmarshaller[R](clazz: Class[R]): FromResponseUnmarshaller[R] = {
    fromResponseUnmarshaller(fromMessageUnmarshaller(json4jUnmarshaller(clazz)))
  }

}
