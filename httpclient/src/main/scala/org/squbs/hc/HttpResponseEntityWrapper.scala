package org.squbs.hc

import spray.httpx.unmarshalling._
import spray.http.{StatusCodes, HttpResponse, StatusCode}
import spray.http.StatusCodes.CustomStatusCode

/**
 * Created by hakuang on 6/3/2014.
 */
case class HttpResponseEntityWrapper[T: FromResponseUnmarshaller](status: StatusCode, content: Either[Throwable, T], rawHttpResponse: Option[HttpResponse])

case class HttpResponseWrapper(status: StatusCode, content: Either[Throwable, HttpResponse])
