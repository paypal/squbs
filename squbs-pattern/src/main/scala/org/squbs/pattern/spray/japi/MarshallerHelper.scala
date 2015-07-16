package org.squbs.pattern.spray.japi

import spray.httpx.marshalling.{Marshaller, ToResponseMarshaller}
import spray.httpx.unmarshalling.UnmarshallerLifting._
import spray.httpx.unmarshalling._

/**
 * Created by lma on 7/15/2015.
 */
object MarshallerHelper {

  def toResponseMarshaller[T](mashaller: Marshaller[T]): ToResponseMarshaller[T] = {
    ToResponseMarshaller.liftMarshaller(mashaller)
  }

  implicit def toFromResponseUnmarshaller[R](unmarshaller: Unmarshaller[R]): FromResponseUnmarshaller[R] = {
    fromResponseUnmarshaller(fromMessageUnmarshaller(unmarshaller))
  }

}
