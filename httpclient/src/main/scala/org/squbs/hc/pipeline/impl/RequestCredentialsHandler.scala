package org.squbs.hc.pipeline.impl

import spray.http.{HttpCredentials, HttpHeaders}

/**
 * Created by hakuang on 6/5/2014.
 */
class RequestCredentialsHandler(credentials: HttpCredentials) extends RequestAddHeaderHandler(HttpHeaders.Authorization(credentials))

object RequestCredentialsHandler {
  def apply(credentials: HttpCredentials) = new RequestCredentialsHandler(credentials)
}
