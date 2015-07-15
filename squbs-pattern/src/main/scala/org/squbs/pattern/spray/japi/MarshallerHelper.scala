package org.squbs.pattern.spray.japi

import spray.httpx.marshalling.{ToResponseMarshaller, Marshaller}

/**
 * Created by lma on 7/15/2015.
 */
object MarshallerHelper {

  def toResponseMarshaller[T](mashaller: Marshaller[T]): ToResponseMarshaller[T] = {
    ToResponseMarshaller.liftMarshaller(mashaller)
  }

}
