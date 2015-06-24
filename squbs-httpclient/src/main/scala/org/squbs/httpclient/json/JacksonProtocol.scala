package org.squbs.httpclient.json

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.ObjectMapper
import spray.http.{ContentTypes, HttpCharsets, HttpEntity, MediaTypes}
import spray.httpx.marshalling.Marshaller
import spray.httpx.unmarshalling.Unmarshaller

/**
 * Created by lma on 6/23/2015.
 */
object JacksonProtocol {

  val defaultMapper = new ObjectMapper().setVisibility(PropertyAccessor.FIELD, Visibility.ANY)

  private def marshall(obj: AnyRef): String = {
    defaultMapper.writeValueAsString(obj)
  }
  
  implicit def jacksonMarshaller[T <: AnyRef] =
    Marshaller.delegate[T, String](ContentTypes.`application/json`)(marshall(_))


  implicit def jacksonUnmarshaller[T](clazz: Class[T]) : Unmarshaller[T] = {
    Unmarshaller[T](MediaTypes.`application/json`) {
      case x: HttpEntity.NonEmpty ⇒
        try {
          val input = x.asString(defaultCharset = HttpCharsets.`UTF-8`)
          defaultMapper.readValue(input, clazz)
        } catch {
          case t: Throwable =>
            //t.printStackTrace()
            throw t
        }
    }
  }

}
