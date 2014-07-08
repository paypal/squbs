package org.squbs.hc

import spray.http.StatusCodes

/**
 * Created by hakuang on 6/9/14.
 */
class HttpClientException(code: Int, message: String) extends RuntimeException(message) with Serializable

case class HttpClientMarkDownException(svcName: String, env: Option[String] = None) extends HttpClientException(900, s"HttpClient:($svcName,$env) has been markdown!")

case class HttpClientExistException(svcName: String, env: Option[String] = None) extends HttpClientException(901, s"HttpClient:($svcName,$env) has been registry!")

case class HttpClientNotExistException(svcName: String, env: Option[String] = None) extends HttpClientException(902, s"HttpClient:($svcName,$env) hasn't been registry!")

case class HttpClientNotSupportMethodException(httpMethod: String) extends HttpClientException(903, s"$httpMethod hasn't been support by HttpClient, please check the Msg type you are calling!")

object HttpClientException {
  val httpClientMarkDownError = StatusCodes.registerCustom(900, "HttpClient has been markdown!", "HttpClient has been markdown!", false, false)
  val httpClientExistingError = StatusCodes.registerCustom(901, "HttpClient has been registry!", "HttpClient has been registry!", false, false)
  val httpClientNotExistingError = StatusCodes.registerCustom(902, "HttpClient hasn't been registry!", "HttpClient hasn't been registry!", false, false)
  val httpClientNotSupportMethodError = StatusCodes.registerCustom(903, "HttpMethod hasn't been support by HttpClient!", "HttpMethod hasn't been support by HttpClient!", false, false)
}
