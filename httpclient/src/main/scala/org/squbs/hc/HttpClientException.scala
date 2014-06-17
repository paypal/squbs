package org.squbs.hc

import spray.http.StatusCodes

/**
 * Created by hakuang on 6/9/14.
 */
case class HttpClientException(code: Int, message: String) extends RuntimeException(message) with Serializable

class ServiceMarkDownException(val svcName: String) extends HttpClientException(900, s"Service:$svcName has been Markdown!")

object ServiceMarkDownException {
  def apply(svcName: String) = {
    new ServiceMarkDownException(svcName)
  }

  def unapply(e: ServiceMarkDownException): Option[(Int, String, String)] = {
    if (e == null) None
    else Some(e.code, e.message, e.svcName)
  }
}

object HttpClientException {
  val serviceMarkDownError = StatusCodes.registerCustom(900, "Service has been Markdown!", "Service has been Markdown!", false, false)
}
