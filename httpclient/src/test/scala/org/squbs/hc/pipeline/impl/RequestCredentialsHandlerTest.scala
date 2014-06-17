package org.squbs.hc.pipeline.impl

import org.scalatest.{Matchers, FlatSpec}
import spray.http.{BasicHttpCredentials, HttpRequest, OAuth2BearerToken}

/**
 * Created by hakuang on 6/5/2014.
 */
class RequestCredentialsHandlerTest extends FlatSpec with Matchers {

  "RequestCredentialsHandler" should "support OAuth2BearerToken" in {
    val httpRequest = HttpRequest()
    val handler = RequestCredentialsHandler(OAuth2BearerToken("test123"))
    val updateHttpRequest = handler.processRequest(httpRequest)
    updateHttpRequest.headers.length should be (1)
    updateHttpRequest.headers.head.name should be ("Authorization")
    updateHttpRequest.headers.head.value should be ("Bearer test123")
  }

  "RequestCredentialsHandler" should "support BasicHttpCredentials" in {
    val httpRequest = HttpRequest()
    val handler = RequestCredentialsHandler(BasicHttpCredentials("username", "password"))
    val updateHttpRequest = handler.processRequest(httpRequest)
    updateHttpRequest.headers.length should be (1)
    updateHttpRequest.headers.head.name should be ("Authorization")
    updateHttpRequest.headers.head.value should be ("Basic dXNlcm5hbWU6cGFzc3dvcmQ=")
  }
}
