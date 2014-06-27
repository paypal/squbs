package org.squbs.hc.pipeline.impl

import org.scalatest.{Matchers, FlatSpec}
import spray.http.{HttpHeader, HttpRequest}
import spray.http.HttpHeaders.RawHeader

/**
 * Created by hakuang on 6/5/2014.
 */
class RequestHeaderHandlerSpec extends FlatSpec with Matchers{

  "RequestAddHeaderHandler" should "support to add HttpHeader in HttpRequest" in {
    val httpRequest = HttpRequest()
    val httpHeader = RawHeader("test-name", "test-value")
    val handler = RequestAddHeaderHandler(httpHeader)
    val updateHttpRequest = handler.processRequest(httpRequest)
    updateHttpRequest.headers.length should be (1)
    updateHttpRequest.headers.head should be (httpHeader)
  }

  "RequestRemoveHeaderHandler" should "support to remove HttpHeader in HttpRequest" in {
    val httpHeader1 = RawHeader("name1", "value1")
    val httpHeader2 = RawHeader("name2", "value2")
    val httpRequest = HttpRequest(headers = List[HttpHeader](httpHeader1, httpHeader2))
    val handler = RequestRemoveHeaderHandler(httpHeader1)
    val updateHttpRequest = handler.processRequest(httpRequest)
    updateHttpRequest.headers.length should be (1)
    updateHttpRequest.headers.head should be (httpHeader2)
  }

  "RequestUpdateHeaderHandler" should "support to update HttpHeader in HttpRequest" in {
    val httpHeader1 = RawHeader("name1", "value1")
    val httpHeader2 = RawHeader("name1", "value2")
    val httpRequest = HttpRequest(headers = List[HttpHeader](httpHeader1))
    val handler = RequestUpdateHeaderHandler(httpHeader2)
    val updateHttpRequest = handler.processRequest(httpRequest)
    updateHttpRequest.headers.length should be (1)
    updateHttpRequest.headers.head should be (httpHeader2)
  }
}
