package org.squbs.httpclient

import spray.http.StatusCodes

/**
 * Created by hakuang on 6/9/14.
 */
class HttpClientException(code: Int, message: String) extends RuntimeException(message) with Serializable

case class HttpClientMarkDownException(svcName: String, env: Option[String] = HttpClientFactory.defaultEnv)
  extends HttpClientException(900, s"HttpClient:($svcName,$env) has been markdown!")

case class HttpClientExistException(svcName: String, env: Option[String] = HttpClientFactory.defaultEnv)
  extends HttpClientException(901, s"HttpClient:($svcName,$env) has been registry!")

case class HttpClientNotExistException(svcName: String, env: Option[String] = HttpClientFactory.defaultEnv)
  extends HttpClientException(902, s"HttpClient:($svcName,$env) hasn't been registry!")

case class HttpClientEndpointNotExistException(svcName: String, env: Option[String] = HttpClientFactory.defaultEnv)
  extends HttpClientException(903, s"HttpClient:($svcName,$env) endpoint cannot be resolved!")

case class HttpClientConfigurationTypeException(svcName: String, env: Option[String] = HttpClientFactory.defaultEnv)
  extends HttpClientException(904, s"HttpClient:($svcName,$env) configuration type error, configuration type should be org.squbs.httpclient.config.Configuration")

object HttpClientException {
  val httpClientMarkDownError = StatusCodes.registerCustom(900, "HttpClient has been markdown!", "HttpClient has been markdown!", false, false)
  val httpClientExistingError = StatusCodes.registerCustom(901, "HttpClient has been registry!", "HttpClient has been registry!", false, false)
  val httpClientNotExistingError = StatusCodes.registerCustom(902, "HttpClient hasn't been registry!", "HttpClient hasn't been registry!", false, false)
}
