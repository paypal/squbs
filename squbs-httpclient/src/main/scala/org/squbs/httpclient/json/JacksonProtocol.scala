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
